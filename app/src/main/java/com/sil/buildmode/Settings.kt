package com.sil.buildmode

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ThemedSpinnerAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
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

    private lateinit var usernameText: EditText
    private lateinit var savesLeftText: TextView
    private lateinit var editUsernameButton: Button
    private lateinit var userLogoutButton: Button
    private lateinit var screenshotToggleButton: ToggleButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }
    private fun initRelated() {
        usernameText = findViewById(R.id.usernameEditText)
        editUsernameButton = findViewById(R.id.editUsername)
        userLogoutButton = findViewById(R.id.userLogout)
        savesLeftText = findViewById(R.id.savesLeftText)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)

        val username = generalSharedPreferences.getString("username", "")
        usernameText.text = Editable.Factory.getInstance().newEditable(username)

        Helpers.getSavesLeft(this) { savesLeft ->
            Log.i(TAG, "You have $savesLeft uploads left today!")
            savesLeftText.text = getString(R.string.savesLeftText, savesLeft)
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
        editUsernameButton.setOnClickListener {
            editUsernameRelated()
        }
        userLogoutButton.setOnClickListener {
            userLogoutRelated()
        }
    }
    // endregion

    // region Service Related
    private fun startScreenshotService(serviceIntent: Intent) {
        startForegroundService(serviceIntent)
        generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, true) }
        updateToggle(screenshotToggleButton, true)
        // showToast(this, "Screenshot monitoring started")
    }
    // endregion

    // region Auth Related
    private fun editUsernameRelated() {
        Log.i(TAG, "editUsernameRelated")

        val newUsername = usernameText.text.toString()
        Helpers.authEditUsernameToServer(this, newUsername) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Edit username success")
                    showToast(this, "Edit username successful!")
                    generalSharedPreferences.edit {
                        putString("username", newUsername)
                    }
                } else {
                    Log.i(TAG, "Edit username failed!")
                    showToast(this, "Edit username failed!")
                }
            }
        }
    }
    private fun userLogoutRelated() {
        Log.i(TAG, "userLogoutRelated")

        generalSharedPreferences.edit(commit = true) {
            putString("username", "")
            .putString("access_token", "")
            .putBoolean(KEY_SCREENSHOT_ENABLED, false)
        }  // ← block until written

        val serviceIntent = Intent(this@Settings, ScreenshotService::class.java)
        stopService(serviceIntent)

        val intent = Intent(this, Welcome::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
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