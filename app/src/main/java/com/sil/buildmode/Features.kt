package com.sil.buildmode

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService

class Features : AppCompatActivity() {
    // region Vars
    private val TAG = "Features"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private val KEY_TEXT_ENABLED = "isTextMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private var pendingToggle: (() -> Unit)? = null

    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var textToggleButton: ToggleButton
    private lateinit var buttonToMain: Button
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_features)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }

    private fun initRelated() {
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        textToggleButton = findViewById(R.id.textToggleButton)
        buttonToMain = findViewById(R.id.buttonToMain)

        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: isChecked=$isChecked")

            screenshotToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val serviceIntent = Intent(this, ScreenshotService::class.java)
            if (isChecked) {
                if (areAllPermissionsGranted()) {
                    startScreenshotService(serviceIntent)
                } else {
                    pendingToggle = { startScreenshotService(serviceIntent) }
                    requestAllPermissions()
                    showToast(this, "Please grant all permissions to enable feature")
                    screenshotToggleButton.isChecked = false
                }
            } else {
                stopService(serviceIntent)
                generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, false) }
                updateToggle(screenshotToggleButton, false)
            }
        }
        textToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Text toggle changed: isChecked=$isChecked")
            textToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            if (isChecked) {
                if (areAllPermissionsGranted()) {
                    generalSharedPreferences.edit { putBoolean(KEY_TEXT_ENABLED, true) }
                    updateToggle(textToggleButton, true)
                } else {
                    pendingToggle = {
                        generalSharedPreferences.edit { putBoolean(KEY_TEXT_ENABLED, true) }
                        updateToggle(textToggleButton, true)
                    }
                    requestAllPermissions()
                    showToast(this, "Please grant all permissions to enable feature")
                    textToggleButton.isChecked = false
                }
            } else {
                generalSharedPreferences.edit { putBoolean(KEY_TEXT_ENABLED, false) }
                updateToggle(textToggleButton, false)
            }
        }
        buttonToMain.setOnClickListener {
            val intent = Intent(this, Main::class.java)
            startActivity(intent)
            finish()
        }
    }
    //

    // region UI Related
    private fun updateToggle(toggle: ToggleButton, isChecked: Boolean) {
        toggle.isChecked = isChecked
        toggle.background = ContextCompat.getDrawable(this, if (isChecked) R.color.accent_0 else R.color.base_0)
        toggle.text = getString(if (isChecked) R.string.screenshotToggleOnText else R.string.screenshotToggleOffText)
    }
    // endregion

    // region Service Related
    private fun startScreenshotService(serviceIntent: Intent) {
        startForegroundService(serviceIntent)
        generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, true) }
        updateToggle(screenshotToggleButton, true)
    }
    // endregion

    // region Permissions Related
    private fun requestAllPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (!hasRuntimePermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, initRequestCode)
        } else if (!isBatteryOptimized()) {
            requestIgnoreBatteryOptimizations()
        }
    }

    private fun hasRuntimePermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startActivityForResult(intent, batteryUnrestrictedRequestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == initRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (!isBatteryOptimized()) {
                    requestIgnoreBatteryOptimizations()
                } else {
                    onAllPermissionsGranted()
                }
            } else {
                showToast(this, "Permissions denied. Cannot proceed.")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == batteryUnrestrictedRequestCode && isBatteryOptimized()) {
            onAllPermissionsGranted()
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        return hasRuntimePermissions(arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )) && isBatteryOptimized()
    }

    private fun onAllPermissionsGranted() {
        Log.i(TAG, "All permissions granted")
        pendingToggle?.invoke()
        pendingToggle = null
    }
    // endregion
}