package com.melakunet.androidapp3

import android.content.Context
import android.content.SharedPreferences

/**
 * Data class representing a saved home location.
 */
data class HomeLocation(val latitude: Double, val longitude: Double, val address: String)

/**
 * Persistence helper for storing and retrieving the home location using SharedPreferences.
 * Uses Double bit representation to maintain coordinate precision.
 */
object HomeStore {
    private const val PREF_NAME = "guardian_prefs"
    private const val KEY_LATITUDE = "home_latitude"
    private const val KEY_LONGITUDE = "home_longitude"
    private const val KEY_ADDRESS = "home_address"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the provided home location details to SharedPreferences.
     */
    fun saveHome(context: Context, latitude: Double, longitude: Double, address: String) {
        getPrefs(context).edit().apply {
            putLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(latitude))
            putLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(longitude))
            putString(KEY_ADDRESS, address)
            apply()
        }
    }

    /**
     * Loads the saved home location from SharedPreferences.
     * Returns null if no home location has been saved.
     */
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

    /**
     * Clears any saved home location from SharedPreferences.
     */
    fun clearHome(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
