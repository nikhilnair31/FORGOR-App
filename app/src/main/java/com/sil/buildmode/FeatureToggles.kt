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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sil.utils.ScreenshotServiceUtils
import kotlin.math.max

class FeatureToggles : AppCompatActivity() {
    // region Vars
    private val TAG = "FeatureToggles"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private lateinit var batteryOptLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePermLauncher: ActivityResultLauncher<String>
    private lateinit var notifPermLauncher: ActivityResultLauncher<String>

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

        // Launchers
        batteryOptLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // User returns from settings. Just proceed; user was instructed to disable manually.
            onScreenshotPermissionsGranted()
        }
        imagePermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.i(TAG, "READ_MEDIA_IMAGES result=$granted")
            if (granted && hasAllImageAccess()) requestNotificationPermission()
            else { showToast("Grant ‘Photos: Allow all’ to proceed."); resetScreenshotToggle() }
        }
        notifPermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS result=$granted")
            if (granted) promptDisableBatteryOptimization()
            else { showToast("Grant notification permission to proceed."); resetScreenshotToggle() }
        }

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
        val isRunning = ScreenshotServiceUtils.isServiceRunning()
        updateToggle(screenshotToggleButton, isRunning)

        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: $isChecked")
            screenshotToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val serviceIntent = Intent(this, ScreenshotService::class.java)
            if (isChecked) {
                pendingToggle = { startScreenshotService(serviceIntent) }  // set first
                if (hasAllImageAccess() && hasNotificationAccess()) {
                    promptDisableBatteryOptimization()
                } else {
                    requestImagePermission()
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
                    this.showToast("Failed to load frequency options")
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
                    this.showToast("Failed to update summary frequency")
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
                    this.showToast("Failed to update digest frequency")
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
    private fun requestImagePermission() {
        if (hasAllImageAccess()) {
            requestNotificationPermission()
        } else {
            imagePermLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun requestNotificationPermission() {
        if (hasNotificationAccess()) {
            promptDisableBatteryOptimization()
        } else {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun promptDisableBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isUnrestricted = pm.isIgnoringBatteryOptimizations(packageName)

        if (isUnrestricted) {
            Log.i(TAG, "Battery optimization already unrestricted.")
            onScreenshotPermissionsGranted()
            return
        }

        showToast("Disable battery optimization for reliable monitoring.")
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Battery opt settings not resolvable: ${t.message}")
        }
        // proceed anyway, user may or may not flip it
        onScreenshotPermissionsGranted()
    }

    private fun hasAllImageAccess(): Boolean {
        // Treat “Allow all photos” as READ_MEDIA_IMAGES == GRANTED.
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationAccess(): Boolean {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onScreenshotPermissionsGranted() {
        Log.i(TAG, "All prerequisites satisfied")
        pendingToggle?.invoke()
        pendingToggle = null
    }

    private fun resetScreenshotToggle() {
        pendingToggle = null
        screenshotToggleButton.isChecked = false
        updateToggle(screenshotToggleButton, false)
        generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, false) }
    }
    // endregion
}