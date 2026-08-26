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
 * Handles location fetching and reverse geocoding to display the current address.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var addressText: TextView
    private lateinit var locationButton: Button

    // Permission request launcher for fine and coarse location
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission granted, fetch location
            fetchLocation()
        } else {
            // Permission denied, show toast as per requirement
            Toast.makeText(this, getString(R.string.error_location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Setup toolbar as support action bar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize UI components
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        addressText = findViewById(R.id.addressText)
        locationButton = findViewById(R.id.locationButton)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set click listener for the location button
        locationButton.setOnClickListener {
            checkPermissionsAndFetchLocation()
        }

        // Keep the existing edge-to-edge insets handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Checks if location permissions are granted. If not, requests them.
     */
    private fun checkPermissionsAndFetchLocation() {
        when {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocation()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    /**
     * Fetches the current location using FusedLocationProviderClient.
     */
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                updateLocationUI(location)
                reverseGeocode(location)
            } else {
                addressText.text = getString(R.string.error_location_null)
            }
        }.addOnFailureListener { e ->
            addressText.text = getString(R.string.error_location_failure, e.localizedMessage)
        }
    }

    /**
     * Updates the UI text views with the fetched location data.
     */
    private fun updateLocationUI(location: Location) {
        latitudeText.text = String.format(Locale.US, "%.6f", location.latitude)
        longitudeText.text = String.format(Locale.US, "%.6f", location.longitude)
        accuracyText.text = getString(R.string.accuracy_format, location.accuracy.toString())
    }

    /**
     * Performs reverse geocoding to find the address from coordinates.
     * Uses the asynchronous API on API 33+ and a background thread on older versions.
     */
    private fun reverseGeocode(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (!Geocoder.isPresent()) {
            addressText.text = getString(R.string.error_geocoder_not_present)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Asynchronous API for API 33+
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread {
                            displayAddress(addresses)
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            addressText.text = getString(R.string.error_geocoding_failed)
                        }
                    }
                }
            )
        } else {
            // Background thread for older versions
            Thread {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    runOnUiThread {
                        if (addresses != null) {
                            displayAddress(addresses)
                        } else {
                            addressText.text = getString(R.string.error_geocoding_failed)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        addressText.text = getString(R.string.error_geocoding_failed)
                    }
                }
            }.start()
        }
    }

    /**
     * Formats and displays the first address from the geocoder results.
     */
    private fun displayAddress(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val addressLines = mutableListOf<String>()
            for (i in 0..address.maxAddressLineIndex) {
                addressLines.add(address.getAddressLine(i))
            }
            addressText.text = addressLines.joinToString("\n")
        } else {
            addressText.text = getString(R.string.error_geocoding_failed)
        }
    }
}
