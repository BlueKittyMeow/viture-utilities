package com.viture.hud

import android.content.Context
import android.content.SharedPreferences

/**
 * Application preferences manager using SharedPreferences.
 * Handles user settings like photo save location.
 */
object AppPreferences {
    private const val PREFS_NAME = "viture_hud_prefs"
    private const val KEY_SAVE_LOCATION = "save_location"

    /**
     * Where to save captured photos.
     */
    enum class SaveLocation {
        CAMERA_ROLL,  // Save to Pictures/VitureHUD (visible in gallery)
        APP_STORAGE   // Save to app-specific storage (private)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get the current save location preference.
     * Default is CAMERA_ROLL so photos appear in gallery.
     */
    fun getSaveLocation(context: Context): SaveLocation {
        val value = getPrefs(context).getString(KEY_SAVE_LOCATION, SaveLocation.CAMERA_ROLL.name)
        return try {
            SaveLocation.valueOf(value ?: SaveLocation.CAMERA_ROLL.name)
        } catch (e: IllegalArgumentException) {
            SaveLocation.CAMERA_ROLL
        }
    }

    /**
     * Set the save location preference.
     */
    fun setSaveLocation(context: Context, location: SaveLocation) {
        getPrefs(context).edit()
            .putString(KEY_SAVE_LOCATION, location.name)
            .apply()
    }
}
