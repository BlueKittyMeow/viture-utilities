package com.viture.hud

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings activity for configuring app preferences.
 * Currently supports:
 * - Photo save location (Camera Roll or App Storage)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var backButton: Button
    private lateinit var saveLocationGroup: RadioGroup
    private lateinit var radioCameraRoll: RadioButton
    private lateinit var radioAppStorage: RadioButton
    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize views
        backButton = findViewById(R.id.backButton)
        saveLocationGroup = findViewById(R.id.saveLocationGroup)
        radioCameraRoll = findViewById(R.id.radioCameraRoll)
        radioAppStorage = findViewById(R.id.radioAppStorage)
        versionText = findViewById(R.id.versionText)

        // Set version text
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = "Viture HUD v$versionName"
        } catch (e: Exception) {
            versionText.text = "Viture HUD"
        }

        // Load current preference
        loadCurrentSettings()

        // Set up listeners
        backButton.setOnClickListener {
            finish()
        }

        saveLocationGroup.setOnCheckedChangeListener { _, checkedId ->
            val location = when (checkedId) {
                R.id.radioCameraRoll -> AppPreferences.SaveLocation.CAMERA_ROLL
                R.id.radioAppStorage -> AppPreferences.SaveLocation.APP_STORAGE
                else -> AppPreferences.SaveLocation.CAMERA_ROLL
            }
            AppPreferences.setSaveLocation(this, location)
        }
    }

    private fun loadCurrentSettings() {
        val currentLocation = AppPreferences.getSaveLocation(this)
        when (currentLocation) {
            AppPreferences.SaveLocation.CAMERA_ROLL -> radioCameraRoll.isChecked = true
            AppPreferences.SaveLocation.APP_STORAGE -> radioAppStorage.isChecked = true
        }
    }
}
