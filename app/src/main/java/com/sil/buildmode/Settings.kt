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
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService

class Settings : AppCompatActivity() {
    // region Vars
    private val TAG = "Settings"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private var pendingToggle: (() -> Unit)? = null

    private lateinit var userButton: Button
    private lateinit var savesLeftText: TextView
    private lateinit var bulkDownloadButton: Button
    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }
    private fun initRelated() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        userButton = findViewById(R.id.userActivityButton)
        bulkDownloadButton = findViewById(R.id.bulkDownloadButton)
        savesLeftText = findViewById(R.id.savesLeftText)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)

        userButton.setOnClickListener {
            startActivity(Intent(this, User::class.java))
        }

        bulkDownloadButton.setOnClickListener {
            // Download
        }

        val cachedSavesLeft = generalSharedPreferences.getInt("cached_saves_left", -1)
        if (cachedSavesLeft != -1) {
            savesLeftText.text = getString(R.string.savesLeftText, cachedSavesLeft)
        }
        Helpers.getSavesLeft(this) { savesLeft ->
            Log.i(TAG, "You have $savesLeft uploads left today!")
            savesLeftText.text = getString(R.string.savesLeftText, savesLeft)
            generalSharedPreferences.edit {
                putInt("cached_saves_left", savesLeft)
            }
        }

        val isScreenshotServiceRunning = Helpers.isServiceRunning(this, ScreenshotService::class.java)
        updateToggle(screenshotToggleButton, isScreenshotServiceRunning)
        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: isChecked=$isChecked")

            screenshotToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val serviceIntent = Intent(this, ScreenshotService::class.java)
            if (isChecked) {
                if (areScreenshotPermissionsGranted()) {
                    startScreenshotService(serviceIntent)
                } else {
                    pendingToggle = { startScreenshotService(serviceIntent) }
                    requestScreenshotPermissions()
                    screenshotToggleButton.isChecked = false
                }
            } else {
                stopService(serviceIntent)
                generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, false) }
                updateToggle(screenshotToggleButton, false)
            }
        }
    }
    // endregion

    // region Service Related
    private fun startScreenshotService(serviceIntent: Intent) {
        startForegroundService(serviceIntent)
        generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, true) }
        updateToggle(screenshotToggleButton, true)
    }
    // endregion

    // region UI Related
    private fun updateToggle(toggle: ToggleButton, isChecked: Boolean) {
        toggle.isChecked = isChecked
        toggle.background = ContextCompat.getDrawable(this, if (isChecked) R.color.accent_0 else R.color.base_0)

        toggle.text = when (toggle) {
            screenshotToggleButton -> getString(if (isChecked) R.string.screenshotToggleOnText else R.string.screenshotToggleOffText)
            else -> ""
        }
    }
    // endregion

    // region Permissions Related
    private fun requestScreenshotPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (!hasRuntimePermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, initRequestCode)
        } else if (!isBatteryOptimized()) {
            requestIgnoreBatteryOptimizations()
        } else {
            onScreenshotPermissionsGranted()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            batteryUnrestrictedRequestCode -> {
                if (isBatteryOptimized()) {
                    onScreenshotPermissionsGranted()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == initRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (!isBatteryOptimized()) {
                    requestIgnoreBatteryOptimizations()
                } else {
                    onScreenshotPermissionsGranted()
                }
            } else {
                showToast(this, "Screenshot permissions denied.")
                screenshotToggleButton.isChecked = false
            }
        }
    }

    private fun areScreenshotPermissionsGranted(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )
        return hasRuntimePermissions(permissions) && isBatteryOptimized()
    }

    private fun onScreenshotPermissionsGranted() {
        Log.i(TAG, "Screenshot permissions granted")
        pendingToggle?.invoke()
        pendingToggle = null
    }
    // endregion
}