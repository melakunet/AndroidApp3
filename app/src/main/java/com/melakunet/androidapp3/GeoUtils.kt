package com.melakunet.androidapp3

import android.location.Location

/**
 * Geographic calculation utilities for the Guardian app.
 */
object GeoUtils {

    /**
     * Data class to return both bearing and distance from current location to a target.
     */
    data class RelativePosition(val bearing: Float, val distance: Float)

    /**
     * Calculates the bearing (0-359 degrees) and distance (meters) to a HomeLocation.
     */
    fun calculateRelativePosition(current: Location, home: HomeLocation): RelativePosition {
        val homeLoc = Location("").apply {
            latitude = home.latitude
            longitude = home.longitude
        }

        var bearing = current.bearingTo(homeLoc)
        bearing = (bearing + 360) % 360

        val distance = current.distanceTo(homeLoc)
        
        return RelativePosition(bearing, distance)
    }
}
