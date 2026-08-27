package com.melakunet.androidapp3

import android.location.Location

// Map and location calculations
object GeoUtils {

    // Result for bearing and distance
    data class RelativePosition(val bearing: Float, val distance: Float)

    // Find bearing and distance to home
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
