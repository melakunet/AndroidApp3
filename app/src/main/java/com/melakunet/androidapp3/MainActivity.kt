package com.melakunet.androidapp3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main activity for the Guardian app.
 * Handles location fetching, reverse geocoding, and displays a mini compass.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var headingSensor: HeadingSensor
    
    private lateinit var miniCompass: CompassView
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var addressText: TextView
    private lateinit var homeStatusText: TextView
    private lateinit var locationButton: Button
    private lateinit var setHomeButton: Button

    private var lastLocation: Location? = null
    private var currentAzimuth = 0f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fetchLocation()
        } else {
            Toast.makeText(this, getString(R.string.error_location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize UI
        miniCompass = findViewById(R.id.miniCompass)
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        addressText = findViewById(R.id.addressText)
        homeStatusText = findViewById(R.id.homeStatusText)
        locationButton = findViewById(R.id.locationButton)
        setHomeButton = findViewById(R.id.setHomeButton)

        headingSensor = HeadingSensor(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationButton.setOnClickListener {
            checkPermissionsAndFetchLocation()
        }

        setHomeButton.setOnClickListener {
            lastLocation?.let { loc ->
                HomeStore.saveHome(this, loc.latitude, loc.longitude, addressText.text.toString())
                Toast.makeText(this, getString(R.string.toast_home_saved), Toast.LENGTH_SHORT).show()
                refreshHomeStatus()
                updateMiniCompassHome()
            }
        }

        // Tap on compass card opens full compass screen
        findViewById<android.view.View>(R.id.compassCard).setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }

        refreshHomeStatus()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        headingSensor.start { azimuth ->
            currentAzimuth = azimuth
            miniCompass.setAzimuth(azimuth)
            updateMiniCompassHome()
        }
        refreshHomeStatus()
        updateMiniCompassHome()
    }

    override fun onPause() {
        super.onPause()
        headingSensor.stop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_compass -> {
                startActivity(Intent(this, CompassActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    lastLocation = location
                    setHomeButton.isEnabled = true
                    updateLocationUI(location)
                    reverseGeocode(location)
                    refreshHomeStatus()
                    updateMiniCompassHome()
                } else {
                    addressText.text = getString(R.string.error_location_null)
                }
            }.addOnFailureListener { e ->
                addressText.text = getString(R.string.error_location_failure, e.localizedMessage)
            }
    }

    private fun updateLocationUI(location: Location) {
        latitudeText.text = String.format(Locale.US, "%.6f", location.latitude)
        longitudeText.text = String.format(Locale.US, "%.6f", location.longitude)
        accuracyText.text = getString(R.string.accuracy_format, location.accuracy.toString())
    }

    private fun refreshHomeStatus() {
        val home = HomeStore.loadHome(this)
        if (home == null) {
            homeStatusText.text = getString(R.string.status_no_home)
            return
        }

        val status = StringBuilder()
        status.append(getString(R.string.status_home_format, home.address))

        lastLocation?.let { currentLoc ->
            val pos = GeoUtils.calculateRelativePosition(currentLoc, home)
            val distance = pos.distance
            val distanceStr = if (distance > 1000) {
                getString(R.string.distance_km, distance / 1000f)
            } else {
                getString(R.string.distance_meters, distance.toInt())
            }
            status.append("\n")
            status.append(getString(R.string.status_distance_format, distanceStr))
        }

        homeStatusText.text = status.toString()
    }

    /**
     * Updates the home indicator on the mini compass.
     */
    private fun updateMiniCompassHome() {
        val home = HomeStore.loadHome(this)
        val current = lastLocation
        if (home != null && current != null) {
            val pos = GeoUtils.calculateRelativePosition(current, home)
            val relativeAngle = (pos.bearing - currentAzimuth + 360) % 360
            miniCompass.setHomeAngle(relativeAngle, pos.distance < 10.0)
        } else {
            miniCompass.setHomeAngle(null)
        }
    }

    private fun reverseGeocode(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (!Geocoder.isPresent()) {
            addressText.text = getString(R.string.error_geocoder_not_present)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    runOnUiThread { displayAddress(addresses) }
                }
                override fun onError(errorMessage: String?) {
                    runOnUiThread { addressText.text = getString(R.string.error_geocoding_failed) }
                }
            })
        } else {
            Thread {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    runOnUiThread {
                        if (addresses != null) displayAddress(addresses)
                        else addressText.text = getString(R.string.error_geocoding_failed)
                    }
                } catch (e: Exception) {
                    runOnUiThread { addressText.text = getString(R.string.error_geocoding_failed) }
                }
            }.start()
        }
    }

    private fun displayAddress(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val addressLines = (0..address.maxAddressLineIndex).map { address.getAddressLine(it) }
            addressText.text = addressLines.joinToString("\n")
            refreshHomeStatus()
            updateMiniCompassHome()
        } else {
            addressText.text = getString(R.string.error_geocoding_failed)
        }
    }
}
