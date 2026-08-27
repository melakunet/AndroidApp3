package com.melakunet.androidapp3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
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

/**
 * Main activity for the Guardian app.
 * Handles location fetching, reverse geocoding, and home location persistence.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var addressText: TextView
    private lateinit var homeStatusText: TextView
    private lateinit var locationButton: Button
    private lateinit var setHomeButton: Button

    // Holds the last successfully retrieved location
    private var lastLocation: Location? = null

    // Permission request launcher for fine and coarse location
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

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize UI components
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        addressText = findViewById(R.id.addressText)
        homeStatusText = findViewById(R.id.homeStatusText)
        locationButton = findViewById(R.id.locationButton)
        setHomeButton = findViewById(R.id.setHomeButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationButton.setOnClickListener {
            checkPermissionsAndFetchLocation()
        }

        // Action for setting current location as home
        setHomeButton.setOnClickListener {
            lastLocation?.let { loc ->
                HomeStore.saveHome(this, loc.latitude, loc.longitude, addressText.text.toString())
                Toast.makeText(this, getString(R.string.toast_home_saved), Toast.LENGTH_SHORT).show()
                refreshHomeStatus()
            }
        }

        // Initial refresh of home status UI
        refreshHomeStatus()

        // Apply window insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                lastLocation = location
                setHomeButton.isEnabled = true
                updateLocationUI(location)
                reverseGeocode(location)
                refreshHomeStatus()
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

    /**
     * Refreshes the home status UI, including distance calculation if home and current location exist.
     */
    private fun refreshHomeStatus() {
        val home = HomeStore.loadHome(this)
        if (home == null) {
            homeStatusText.text = getString(R.string.status_no_home)
            return
        }

        val status = StringBuilder()
        status.append(getString(R.string.status_home_format, home.address))

        lastLocation?.let { currentLoc ->
            val homeLoc = Location("").apply {
                latitude = home.latitude
                longitude = home.longitude
            }
            val distance = currentLoc.distanceTo(homeLoc)
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

    private fun reverseGeocode(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (!Geocoder.isPresent()) {
            addressText.text = getString(R.string.error_geocoder_not_present)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread { displayAddress(addresses) }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread { addressText.text = getString(R.string.error_geocoding_failed) }
                    }
                }
            )
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
            // Refresh home status in case address was updated for a newly saved home
            refreshHomeStatus()
        } else {
            addressText.text = getString(R.string.error_geocoding_failed)
        }
    }
}
