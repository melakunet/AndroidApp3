package com.melakunet.androidapp3

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Manages accelerometer and magnetometer sensors to provide device azimuth (heading).
 * Applies a low-pass filter for smooth data and normalizes results to 0..359 degrees.
 */
class HeadingSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var listener: ((Float) -> Unit)? = null
    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private val alpha = 0.1f // Filter coefficient

    /**
     * Starts listening for sensor updates.
     * @param listener Callback providing the azimuth in degrees (0-359).
     */
    fun start(listener: (Float) -> Unit) {
        this.listener = listener
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    /**
     * Stops listening for sensor updates and clears references.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            for (i in 0..2) gravity[i] = alpha * event.values[i] + (1 - alpha) * gravity[i]
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            for (i in 0..2) geomagnetic[i] = alpha * event.values[i] + (1 - alpha) * geomagnetic[i]
        }

        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            
            // Azimuth in orientation[0] is in radians [-PI, PI]
            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            // Normalize to [0, 360)
            azimuthDeg = (azimuthDeg + 360) % 360
            
            listener?.invoke(azimuthDeg)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
