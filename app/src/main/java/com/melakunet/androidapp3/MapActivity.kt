package com.melakunet.androidapp3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

// Map screen: follows the phone on an OpenStreetMap tile map
class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapCoordinatesText: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // The one and only marker: it is moved, never recreated
    private var locationMarker: Marker? = null
    private var firstLocation = true
    private var isReceivingUpdates = false

    // Saved home pin and its safe zone circle, both follow whatever HomeStore holds
    private var homeMarker: Marker? = null
    private var safeZoneCircle: Polygon? = null
    private var noHomeToastShown = false

    // Request permissions for location
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, getString(R.string.error_location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    // Set up the map, the coordinates card and the location updates
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid needs its cache settings and a user agent before any map view is inflated,
        // the tile policy of OpenStreetMap requires identifying the app
        val osmPrefs = getSharedPreferences(OSMDROID_PREF_NAME, Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, osmPrefs)
        Configuration.getInstance().userAgentValue = packageName

        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize UI
        mapView = findViewById(R.id.mapView)
        mapCoordinatesText = findViewById(R.id.mapCoordinatesText)

        setupMap()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateMap(location)
                }
            }
        }

        checkLocationPermission()

        // Handle edge-to-edge window padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Toolbar back arrow returns to the previous screen
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // Wake the map up, redraw the safe zone and follow the phone again
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Home may have been changed on another screen while this one was away
        refreshSafeZone()
        startLocationUpdates()
    }

    // Let the map idle and stop the updates to save battery
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopLocationUpdates()
    }

    // Standard OpenStreetMap tiles, pinch to zoom, fading zoom buttons
    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)

        // Marker stays hidden until the first fix gives it a real position
        locationMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.map_marker_current_location)
            isEnabled = false
        }
        mapView.overlays.add(locationMarker)
    }

    // Draw the saved home and its safe zone, or clear them when no home is stored
    private fun refreshSafeZone() {
        val home = HomeStore.loadHome(this)
        if (home == null) {
            clearSafeZone()
            // Say it once per visit, a Toast on every resume would be noise
            if (!noHomeToastShown) {
                Toast.makeText(this, getString(R.string.toast_no_home_for_map), Toast.LENGTH_SHORT).show()
                noHomeToastShown = true
            }
            return
        }

        val homePoint = GeoPoint(home.latitude, home.longitude)
        showSafeZoneCircle(homePoint, HomeStore.loadRadius(this).toDouble())
        showHomeMarker(homePoint)
        mapView.invalidate()
    }

    // Circle around home, kept at the bottom of the overlay list so both pins stay tappable
    private fun showSafeZoneCircle(center: GeoPoint, radiusMeters: Double) {
        var circle = safeZoneCircle
        if (circle == null) {
            circle = Polygon(mapView)
            circle.title = getString(R.string.title_safe_zone)
            circle.fillPaint.color = ColorUtils.setAlphaComponent(getColor(R.color.teal_primary), SAFE_ZONE_FILL_ALPHA)
            circle.outlinePaint.color = getColor(R.color.teal_primary)
            circle.outlinePaint.strokeWidth = SAFE_ZONE_STROKE_WIDTH
            // Index 0 is drawn first, which puts the fill under every marker
            mapView.overlays.add(0, circle)
            safeZoneCircle = circle
        }
        circle.setPoints(Polygon.pointsAsCircle(center, radiusMeters))
    }

    // Pin for the saved home, tinted so it never reads as the live position
    private fun showHomeMarker(point: GeoPoint) {
        var marker = homeMarker
        if (marker == null) {
            marker = Marker(mapView)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = getString(R.string.map_marker_home)
            marker.icon = homeMarkerIcon()
            mapView.overlays.add(marker)
            homeMarker = marker
        }
        marker.position = point
    }

    // Own copy of the default pin painted violet, the shared one stays untouched
    private fun homeMarkerIcon(): Drawable? {
        val icon = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default)?.mutate()
        icon?.setTint(getColor(R.color.violet_secondary))
        return icon
    }

    // Take the home pin and the circle off the map
    private fun clearSafeZone() {
        homeMarker?.let { mapView.overlays.remove(it) }
        safeZoneCircle?.let { mapView.overlays.remove(it) }
        homeMarker = null
        safeZoneCircle = null
        mapView.invalidate()
    }

    // Ask for location permission, onResume starts the updates once it is granted
    private fun checkLocationPermission() {
        if (!hasLocationPermission()) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // True when the phone may be located
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Follow the phone with continuous updates
    private fun startLocationUpdates() {
        if (!hasLocationPermission() || isReceivingUpdates) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isReceivingUpdates = true
    }

    // Stop following the phone
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isReceivingUpdates = false
    }

    // Move the marker and the camera to the new fix
    private fun updateMap(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)

        locationMarker?.apply {
            position = point
            isEnabled = true
        }

        if (firstLocation) {
            // Jump straight to the phone the first time
            mapView.controller.setZoom(DEFAULT_ZOOM)
            mapView.controller.setCenter(point)
            firstLocation = false
        } else {
            // Glide afterwards so panning by hand is not fought over
            mapView.controller.animateTo(point)
        }

        mapView.invalidate()
        updateCoordinatesText(location)
    }

    // Show the current coordinates in the bottom card
    private fun updateCoordinatesText(location: Location) {
        val latitude = String.format(Locale.US, "%.6f", location.latitude)
        val longitude = String.format(Locale.US, "%.6f", location.longitude)
        mapCoordinatesText.text = getString(R.string.map_coordinates_format, latitude, longitude)
    }

    // Shared constants for map setup and safe-zone styling.
    companion object {
        private const val OSMDROID_PREF_NAME = "osmdroid_prefs"
        private const val DEFAULT_ZOOM = 16.0
        private const val SAFE_ZONE_FILL_ALPHA = 48
        private const val SAFE_ZONE_STROKE_WIDTH = 4f
    }
}
