package com.melakunet.androidapp3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Main screen: shows location and a mini compass.
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var headingSensor: HeadingSensor
    private lateinit var locationCallback: LocationCallback
    
    private lateinit var miniCompass: CompassView
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var addressText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var homeStatusText: TextView
    private lateinit var locationButton: Button
    private lateinit var setHomeButton: Button
    private lateinit var openSafeZoneButton: Button
    private lateinit var openMapButton: Button

    private var lastLocation: Location? = null
    private var lastGeocodedLocation: Location? = null
    private var hasFetchedAddress = false
    private var currentAzimuth = 0f
    private var isLiveUpdating = false
    private var shouldResumeLiveUpdates = false

    // Request permissions for location
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLiveUpdates()
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
        lastUpdateText = findViewById(R.id.lastUpdateText)
        homeStatusText = findViewById(R.id.homeStatusText)
        locationButton = findViewById(R.id.locationButton)
        setHomeButton = findViewById(R.id.setHomeButton)
        openSafeZoneButton = findViewById(R.id.openSafeZoneButton)
        openMapButton = findViewById(R.id.openMapButton)

        headingSensor = HeadingSensor(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    handleLocationUpdate(location)
                }
            }
        }

        locationButton.setOnClickListener {
            if (isLiveUpdating) stopLiveUpdates() else checkPermissionsAndStartLiveUpdates()
        }

        setHomeButton.setOnClickListener {
            lastLocation?.let { loc ->
                HomeStore.saveHome(this, loc.latitude, loc.longitude, addressText.text.toString())
                Toast.makeText(this, getString(R.string.toast_home_saved), Toast.LENGTH_SHORT).show()
                refreshHomeStatus()
                updateMiniCompassHome()
            }
        }

        openSafeZoneButton.setOnClickListener {
            startActivity(Intent(this, SafeZoneActivity::class.java))
        }

        openMapButton.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        // Tap on compass card opens full compass screen
        findViewById<android.view.View>(R.id.compassCard).setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }

        refreshHomeStatus()

        // Handle edge-to-edge window padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // Start compass updates
        headingSensor.start { azimuth ->
            currentAzimuth = azimuth
            miniCompass.setAzimuth(azimuth)
            updateMiniCompassHome()
        }
        if (shouldResumeLiveUpdates) {
            shouldResumeLiveUpdates = false
            checkPermissionsAndStartLiveUpdates()
        }
        refreshHomeStatus()
        updateMiniCompassHome()
    }

    override fun onPause() {
        super.onPause()
        // Stop compass to save battery
        headingSensor.stop()
        shouldResumeLiveUpdates = isLiveUpdating
        stopLiveUpdates()
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
            R.id.action_safe_zone -> {
                startActivity(Intent(this, SafeZoneActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Start live updates when permission is available.
    private fun checkPermissionsAndStartLiveUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLiveUpdates()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Start continuous location updates.
    private fun startLiveUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isLiveUpdating = true
        updateLocationButton()
    }

    // Stop continuous location updates.
    private fun stopLiveUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLiveUpdating = false
        updateLocationButton()
    }

    // Refresh UI from a new location fix.
    private fun handleLocationUpdate(location: Location) {
        lastLocation = location
        setHomeButton.isEnabled = true
        updateLocationUI(location)
        updateLastUpdatedText()
        refreshHomeStatus()
        updateMiniCompassHome()

        if (shouldReverseGeocode(location)) {
            reverseGeocode(location)
        }
    }

    // Update the location button label for the current tracking state.
    private fun updateLocationButton() {
        locationButton.text = getString(
            if (isLiveUpdating) R.string.btn_stop_live_updates else R.string.btn_start_live_updates
        )
    }

    // Update the visible timestamp for the latest fix.
    private fun updateLastUpdatedText() {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lastUpdateText.text = getString(R.string.status_updated_format, timestamp)
    }

    // Decide whether the next fix should trigger reverse geocoding.
    private fun shouldReverseGeocode(location: Location): Boolean {
        val previous = lastGeocodedLocation
        return !hasFetchedAddress || previous == null || previous.distanceTo(location) > 30f
    }

    // Update the core location fields.
    private fun updateLocationUI(location: Location) {
        latitudeText.text = String.format(Locale.US, "%.6f", location.latitude)
        longitudeText.text = String.format(Locale.US, "%.6f", location.longitude)
        accuracyText.text = getString(R.string.accuracy_format, location.accuracy.toInt().toString())
    }

    // Update distance to home text
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

    // Update the green arrow on mini compass
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

    // Find address from coordinates
    private fun reverseGeocode(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (!Geocoder.isPresent()) {
            hasFetchedAddress = false
            addressText.text = getString(R.string.error_geocoder_not_present)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    runOnUiThread { displayAddress(location, addresses) }
                }
                override fun onError(errorMessage: String?) {
                    runOnUiThread {
                        hasFetchedAddress = false
                        addressText.text = getString(R.string.error_geocoding_failed)
                    }
                }
            })
        } else {
            Thread {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    runOnUiThread {
                        if (addresses != null) displayAddress(location, addresses)
                        else {
                            hasFetchedAddress = false
                            addressText.text = getString(R.string.error_geocoding_failed)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        hasFetchedAddress = false
                        addressText.text = getString(R.string.error_geocoding_failed)
                    }
                }
            }.start()
        }
    }

    // Show the resolved address text.
    private fun displayAddress(location: Location, addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val addressLines = (0..address.maxAddressLineIndex).map { address.getAddressLine(it) }
            addressText.text = addressLines.joinToString("\n")
            lastGeocodedLocation = Location(location)
            hasFetchedAddress = true
            refreshHomeStatus()
            updateMiniCompassHome()
        } else {
            hasFetchedAddress = false
            addressText.text = getString(R.string.error_geocoding_failed)
        }
    }
}
