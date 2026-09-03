package com.melakunet.androidapp3

import android.content.Context
import android.content.SharedPreferences

// Data for a saved home
data class HomeLocation(val latitude: Double, val longitude: Double, val address: String)

// Saves and loads home data from SharedPreferences
object HomeStore {
    private const val PREF_NAME = "guardian_prefs"
    private const val KEY_LATITUDE = "home_latitude"
    private const val KEY_LONGITUDE = "home_longitude"
    private const val KEY_ADDRESS = "home_address"
    private const val KEY_RADIUS = "safe_zone_radius"
    private const val DEFAULT_RADIUS = 200f

    // Open the app preferences used for home data.
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Save home coordinates and address
    fun saveHome(context: Context, latitude: Double, longitude: Double, address: String) {
        getPrefs(context).edit().apply {
            putLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(latitude))
            putLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(longitude))
            putString(KEY_ADDRESS, address)
            apply()
        }
    }

    // Save the safe zone distance
    fun saveRadius(context: Context, radius: Float) {
        getPrefs(context).edit().putFloat(KEY_RADIUS, radius).apply()
    }

    // Get the saved home location
    fun loadHome(context: Context): HomeLocation? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_LATITUDE)) return null

        val latBits = prefs.getLong(KEY_LATITUDE, 0L)
        val lngBits = prefs.getLong(KEY_LONGITUDE, 0L)
        val address = prefs.getString(KEY_ADDRESS, "") ?: ""

        return HomeLocation(
            java.lang.Double.longBitsToDouble(latBits),
            java.lang.Double.longBitsToDouble(lngBits),
            address
        )
    }

    // Get the safe zone distance
    fun loadRadius(context: Context): Float {
        return getPrefs(context).getFloat(KEY_RADIUS, DEFAULT_RADIUS)
    }

    // Delete home data
    fun clearHome(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
