package com.sil.buildmode

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
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
import com.sil.models.FrequencyOption
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService
import kotlin.math.max

class FeatureToggles : AppCompatActivity() {
    // region Vars
    private val TAG = "FeatureToggles"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private var pendingToggle: (() -> Unit)? = null

    private lateinit var rootConstraintLayout: ConstraintLayout
    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var summaryCycleButton: Button
    private lateinit var digestCycleButton: Button

    private val frequencyOptions = mutableListOf<FrequencyOption>()
    private var summaryFrequencyIndex = 0
    private var digestFrequencyIndex = 0
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_toggles)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        // init views immediately
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        summaryCycleButton = findViewById(R.id.summaryFreqToggleButton)
        digestCycleButton = findViewById(R.id.digestEnabledToggleButton)

        // safe to set inset listener now
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
        }

        fetchFrequencies {
            initScreenshotToggle()
            initSummaryCycleButton()
            initDigestCycleButton()
        }
    }
    // endregion

    // region UI Related
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
    private fun initSummaryCycleButton() {
        val cachedId = generalSharedPreferences.getInt("summary_frequency_id", frequencyOptions.first().id)

        summaryFrequencyIndex = frequencyOptions.indexOfFirst { it.id == cachedId }.coerceAtLeast(0)
        renderCycleButton(summaryCycleButton, frequencyOptions[summaryFrequencyIndex])

        Helpers.getSummaryFrequency(this) { serverId ->
            runOnUiThread {
                summaryFrequencyIndex = frequencyOptions.indexOfFirst { it.id == serverId }.coerceAtLeast(0)
                generalSharedPreferences.edit { putInt("summary_frequency_id", serverId) }
                renderCycleButton(summaryCycleButton, frequencyOptions[summaryFrequencyIndex])
            }
        }

        summaryCycleButton.setOnClickListener {
            val newIndex = (summaryFrequencyIndex + 1) % frequencyOptions.size
            val selectedFreq = frequencyOptions[newIndex]
            updateSummaryFrequency(selectedFreq)
        }
    }
    private fun initDigestCycleButton() {
        val cachedId = generalSharedPreferences.getInt("digest_frequency_id", frequencyOptions.first().id)

        digestFrequencyIndex = frequencyOptions.indexOfFirst { it.id == cachedId }.coerceAtLeast(0)
        renderCycleButton(digestCycleButton, frequencyOptions[digestFrequencyIndex])

        Helpers.getDigestFrequency(this) { serverId ->
            runOnUiThread {
                digestFrequencyIndex = frequencyOptions.indexOfFirst { it.id == serverId }.coerceAtLeast(0)
                generalSharedPreferences.edit { putInt("digest_frequency_id", serverId) }
                renderCycleButton(digestCycleButton, frequencyOptions[digestFrequencyIndex])
            }
        }

        digestCycleButton.setOnClickListener {
            val newIndex = (digestFrequencyIndex + 1) % frequencyOptions.size
            val selectedFreq = frequencyOptions[newIndex]
            updateDigestFrequency(selectedFreq)
        }
    }

    private fun renderCycleButton(cycleButton: Button, freq: FrequencyOption) {
        val (label, bgCol, txtCol) = when (freq.name) {
            "none" -> Triple(R.string.summaryNoneText, R.color.base_0, R.color.accent_1)
            "daily" -> Triple(R.string.summaryDailyText, R.color.accent_0, R.color.accent_1)
            "weekly" -> Triple(R.string.summaryWeeklyText, R.color.accent_0, R.color.accent_1)
            "monthly" -> Triple(R.string.summaryMonthlyText, R.color.accent_0, R.color.accent_1)
            else -> Triple(R.string.summaryNoneText, R.color.base_0, R.color.accent_1)
        }

        cycleButton.setText(label)
        cycleButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgCol))
        cycleButton.setTextColor(ContextCompat.getColor(this, txtCol))
        cycleButton.contentDescription = "frequency: $label"
    }
    private fun updateToggle(toggle: ToggleButton, isChecked: Boolean) {
        toggle.isChecked = isChecked
        toggle.background = ContextCompat.getDrawable(this, if (isChecked) R.color.accent_0 else R.color.base_0)

        toggle.text = getString(if (isChecked) R.string.toggleOnText else R.string.toggleOffText)
    }
    // endregion

    // region Feature Related
    private fun fetchFrequencies(callback: () -> Unit) {
        Helpers.getAllFrequencies() { freqs: List<FrequencyOption> ->
            runOnUiThread {
                if (freqs.isNotEmpty()) {
                    frequencyOptions.clear()
                    frequencyOptions.addAll(freqs)
                    callback()
                } else {
                    showToast(this, "Failed to load frequency options")
                }
            }
        }
    }

    private fun updateSummaryFrequency(freq: FrequencyOption) {
        Helpers.editSummaryFreqToServer(this, freq.id) { success ->
            runOnUiThread {
                if (success) {
                    generalSharedPreferences.edit { putInt("summary_frequency_id", freq.id) }
                    summaryFrequencyIndex = frequencyOptions.indexOfFirst { it.id == freq.id }
                    renderCycleButton(summaryCycleButton, freq)
                } else {
                    showToast(this, "Failed to update summary frequency")
                }
            }
        }
    }
    private fun updateDigestFrequency(freq: FrequencyOption) {
        Helpers.editDigestFreqToServer(this, freq.id) { success ->
            runOnUiThread {
                if (success) {
                    generalSharedPreferences.edit { putInt("digest_frequency_id", freq.id) }
                    digestFrequencyIndex = frequencyOptions.indexOfFirst { it.id == freq.id }
                    renderCycleButton(digestCycleButton, freq)
                } else {
                    showToast(this, "Failed to update digest frequency")
                }
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
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.POST_NOTIFICATIONS
            )

            else -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        ActivityCompat.requestPermissions(this, permissions, initRequestCode)
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
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
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivityForResult(intent, batteryUnrestrictedRequestCode)
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
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                        isBatteryOptimized()
            }

            else -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED &&
                        isBatteryOptimized()
            }
        }
    }

    private fun onScreenshotPermissionsGranted() {
        Log.i(TAG, "Screenshot permissions granted")
        pendingToggle?.invoke()
        pendingToggle = null
    }
    // endregion
}