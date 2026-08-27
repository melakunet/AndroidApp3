package com.melakunet.androidapp3

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity that monitors if the user is within a defined "Safe Zone" around home.
 * Provides real-time updates and notifications/vibrations when leaving the zone.
 */
class SafeZoneActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false

    private lateinit var statusText: TextView
    private lateinit var distanceText: TextView
    private lateinit var radiusText: TextView
    private lateinit var radiusSlider: Slider
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var speedText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var trackingButton: Button

    private var homeLocation: HomeLocation? = null
    private var currentRadius = 200f
    private var wasInside = true
    private val channelId = "guardian_alerts"

    // Permission launcher for location and notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (locationGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, getString(R.string.error_location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_safe_zone)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize UI
        statusText = findViewById(R.id.statusText)
        distanceText = findViewById(R.id.distanceText)
        radiusText = findViewById(R.id.radiusText)
        radiusSlider = findViewById(R.id.radiusSlider)
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        speedText = findViewById(R.id.speedText)
        lastUpdateText = findViewById(R.id.lastUpdateText)
        trackingButton = findViewById(R.id.trackingButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        homeLocation = HomeStore.loadHome(this)
        currentRadius = HomeStore.loadRadius(this)
        
        radiusSlider.value = currentRadius
        radiusText.text = getString(R.string.radius_format, currentRadius.toInt())

        radiusSlider.addOnChangeListener { _, value, _ ->
            currentRadius = value
            radiusText.text = getString(R.string.radius_format, value.toInt())
            HomeStore.saveRadius(this, value)
        }

        trackingButton.setOnClickListener {
            if (isTracking) stopTracking() else checkPermissionsAndStartTracking()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { updateUI(it) }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        if (isTracking) stopTracking()
    }

    private fun checkPermissionsAndStartTracking() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isTracking = true
        trackingButton.text = getString(R.string.btn_stop_tracking)
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        trackingButton.text = getString(R.string.btn_start_tracking)
    }

    private fun updateUI(location: Location) {
        latitudeText.text = String.format(Locale.US, "%.6f", location.latitude)
        longitudeText.text = String.format(Locale.US, "%.6f", location.longitude)
        accuracyText.text = getString(R.string.accuracy_format, location.accuracy.toInt().toString())
        speedText.text = getString(R.string.speed_format, location.speed * 3.6f)
        lastUpdateText.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val home = homeLocation ?: run {
            statusText.text = getString(R.string.status_no_home)
            return
        }

        val pos = GeoUtils.calculateRelativePosition(location, home)
        distanceText.text = getString(R.string.status_distance_format, 
            if (pos.distance > 1000) getString(R.string.distance_km, pos.distance / 1000f)
            else getString(R.string.distance_meters, pos.distance.toInt())
        )

        val isInside = pos.distance <= currentRadius
        if (isInside) {
            statusText.text = getString(R.string.status_inside_zone)
            statusText.setTextColor(getColor(R.color.teal_primary))
        } else {
            statusText.text = getString(R.string.status_outside_zone)
            statusText.setTextColor(getColor(R.color.sunset_error))
            
            if (wasInside) {
                sendAlert()
            }
        }
        wasInside = isInside
    }

    private fun sendAlert() {
        // Notification
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_left_safe_zone))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            NotificationManagerCompat.from(this).notify(1, builder.build())
        }

        // Vibration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(500)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notif_channel_name)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
