package com.sil.buildmode

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService
import kotlin.math.max

class FeaturePermissions : AppCompatActivity() {
    // region Vars
    private val TAG = "Features"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private var pendingToggle: (() -> Unit)? = null

    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var digestCycleButton: Button
    private lateinit var buttonToMain: Button
    private lateinit var rootConstraintLayout: ConstraintLayout

    private val digestOptions = listOf(
        Triple(R.string.digestNoneText,   R.color.base_0, R.color.accent_1),
        Triple(R.string.digestWeeklyText, R.color.accent_0,            R.color.accent_1),
        Triple(R.string.digestMonthlyText,R.color.accent_0,            R.color.accent_1)
    )
    private var digestIndex = generalSharedPreferences.getInt("digest_index", 0).coerceIn(0, digestOptions.lastIndex)
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_permissions)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }

    private fun initRelated() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        digestCycleButton = findViewById(R.id.digestFreqToggleButton)
        buttonToMain = findViewById(R.id.buttonToMain)

        initDigestButton()
        initScreenshotToggle()
        initMainButton()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
        }
    }
    // endregion

    // region UI Related
    private fun initDigestButton() {
        digestIndex = generalSharedPreferences.getInt("digest_index", 0).coerceIn(0, digestOptions.lastIndex)
        Log.i(TAG, "digestIndex: $digestIndex")
        renderDigestButton(digestIndex)

        digestCycleButton.setOnClickListener {
            val newIndex = (digestIndex + 1) % digestOptions.size
            renderDigestButton(newIndex)
            updateDigestFrequency(newIndex)
        }
    }
    private fun initScreenshotToggle() {
        val isRunning = Helpers.isServiceRunning(this, ScreenshotService::class.java)
        updateToggle(screenshotToggleButton, isRunning)

        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: $isChecked")
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
    private fun initMainButton() {
        buttonToMain.setOnClickListener {
            val intent = Intent(this, Main::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun renderDigestButton(index: Int) {
        val (label, bgCol, txtCol) = digestOptions[index]
        digestCycleButton.setText(label)
        digestCycleButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgCol))
        digestCycleButton.setTextColor(ContextCompat.getColor(this, txtCol))
        digestCycleButton.contentDescription = "Digest: $label"
    }
    private fun updateToggle(toggle: ToggleButton, isChecked: Boolean) {
        toggle.isChecked = isChecked
        toggle.background = ContextCompat.getDrawable(this, if (isChecked) R.color.accent_0 else R.color.base_0)

        toggle.text = when (toggle) {
            screenshotToggleButton -> getString(if (isChecked) R.string.screenshotToggleOnText else R.string.screenshotToggleOffText)
            else -> ""
        }
    }
    // endregion

    // region Feature Related
    private fun updateDigestFrequency(newIndex: Int) {
        // Call API
        val freqName = when (newIndex) {
            0 -> "none"
            1 -> "weekly"
            2 -> "monthly"
            else -> "none"
        }

        Helpers.authEditDigestToServer(this, freqName) { success ->
            if (success) {
                // Save locally only if backend update succeeded
                generalSharedPreferences.edit { putInt("digest_index", newIndex) }
                digestIndex = newIndex
            } else {
                // Keep old state if backend failed
                showToast(this, "Failed to update digest")
            }
        }
    }
    private fun startScreenshotService(serviceIntent: Intent) {
        startForegroundService(serviceIntent)
        generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, true) }
        updateToggle(screenshotToggleButton, true)
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