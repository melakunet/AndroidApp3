package com.melakunet.androidapp3

import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt

// Compass screen: shows bearing and distance to home
class CompassActivity : AppCompatActivity() {

    private lateinit var headingSensor: HeadingSensor
    private lateinit var compassView: CompassView
    private lateinit var bearingText: TextView
    private lateinit var directionText: TextView
    private lateinit var homeBearingText: TextView
    private lateinit var homeHintText: TextView

    private var currentAzimuth = 0f
    private var targetAzimuth = 0f
    private var azimuthAnimator: ValueAnimator? = null

    private var homeLocation: HomeLocation? = null
    private var lastLocation: Location? = null

    // Set up the full compass screen.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_compass)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        compassView = findViewById(R.id.compassView)
        bearingText = findViewById(R.id.bearingText)
        directionText = findViewById(R.id.directionText)
        homeBearingText = findViewById(R.id.homeBearingText)
        homeHintText = findViewById(R.id.homeHintText)

        headingSensor = HeadingSensor(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Toolbar back arrow returns to the previous screen.
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // Restart heading updates and refresh the current location.
    override fun onResume() {
        super.onResume()
        headingSensor.start { azimuth ->
            animateAzimuth(azimuth)
        }

        homeLocation = HomeStore.loadHome(this)
        requestCurrentLocation()
    }

    // Stop heading updates and any running rotation animation.
    override fun onPause() {
        super.onPause()
        headingSensor.stop()
        azimuthAnimator?.cancel()
    }

    // Smoothly rotate the compass needle
    private fun animateAzimuth(target: Float) {
        if (azimuthAnimator?.isRunning == true) {
            targetAzimuth = target
            return
        }

        targetAzimuth = target
        val start = currentAzimuth
        var end = target

        // Find shortest path to rotate
        val diff = end - start
        if (diff > 180) end -= 360 else if (diff < -180) end += 360

        azimuthAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                currentAzimuth = (value + 360) % 360
                compassView.setAzimuth(currentAzimuth)
                updateCompassUI(currentAzimuth)
                updateHomeBearingUI()
            }
            start()
        }
    }

    // Show the current heading as degrees and a compass word.
    private fun updateCompassUI(azimuth: Float) {
        val rounded = azimuth.roundToInt() % 360
        bearingText.text = getString(R.string.bearing_format, rounded)
        directionText.text = getDirectionName(rounded)
    }

    // Convert a bearing into a compass direction name.
    private fun getDirectionName(degrees: Int): String {
        return when (((degrees + 22.5) % 360 / 45).toInt()) {
            0 -> getString(R.string.dir_n)
            1 -> getString(R.string.dir_ne)
            2 -> getString(R.string.dir_e)
            3 -> getString(R.string.dir_se)
            4 -> getString(R.string.dir_s)
            5 -> getString(R.string.dir_sw)
            6 -> getString(R.string.dir_w)
            7 -> getString(R.string.dir_nw)
            else -> getString(R.string.dir_n)
        }
    }

    // Get one fix so the compass can point toward home.
    private fun requestCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            homeBearingText.text = getString(R.string.error_location_permission_needed)
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                lastLocation = location
                updateHomeBearingUI()
            }
    }

    // Update the home bearing, distance and arrow state.
    private fun updateHomeBearingUI() {
        val home = homeLocation ?: run {
            homeBearingText.text = getString(R.string.status_no_home)
            homeHintText.text = ""
            compassView.setHomeAngle(null)
            return
        }

        val current = lastLocation ?: return

        val pos = GeoUtils.calculateRelativePosition(current, home)
        val bearingToHome = pos.bearing
        val distance = pos.distance

        val distanceStr = if (distance > 1000) {
            getString(R.string.distance_km, distance / 1000f)
        } else {
            getString(R.string.distance_meters, distance.toInt())
        }

        homeBearingText.text = getString(R.string.home_bearing_format, bearingToHome.roundToInt(), distanceStr)
        
        val isArrived = distance < 10.0
        homeHintText.text = if (isArrived) getString(R.string.home_hint_arrived) else getString(R.string.home_hint_turn)

        val relativeAngle = (bearingToHome - currentAzimuth + 360) % 360
        compassView.setHomeAngle(relativeAngle, isArrived)
    }
}
