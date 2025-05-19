package com.sil.buildmode

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sil.others.Helpers
import com.sil.services.ScreenshotService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_FIRST_RUN = "isFirstRun"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPref: SharedPreferences

    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private lateinit var screenshotToggleButton: ToggleButton
    private lateinit var searchEditText: EditText
    private lateinit var settingsButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initSharedPreferences()
        initViews()
        setupListeners()
        updateToggleStates()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcut = ShortcutInfoCompat.Builder(this, "share_image")
                .setShortLabel("Save to BUILDMODE")
                .setLongLabel("Save to BUILDMODE")
                .setIcon(IconCompat.createWithResource(this, R.drawable.mia_stat_name))
                .setIntent(
                    Intent(Intent.ACTION_SEND)
                        .setClass(this, Share::class.java)
                        .setType("image/*")
                )
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        }
    }

    private fun initSharedPreferences() {
        generalSharedPref = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
        if (generalSharedPref.getBoolean(KEY_FIRST_RUN, true)) {
            generalSharedPref.edit { putBoolean(KEY_FIRST_RUN, false) }
        }
    }
    private fun initViews() {
        settingsButton = findViewById(R.id.settingsButton)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)
        searchEditText = findViewById(R.id.searchEditText)
    }
    private fun setupListeners() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: isChecked=$isChecked")
            screenshotToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            updateServiceState(
                ScreenshotService::class.java,
                isChecked,
                KEY_SCREENSHOT_ENABLED
            )
        }
        searchEditText.doAfterTextChanged { text ->
            searchRunnable?.let { searchHandler.removeCallbacks(it) }

            searchRunnable = Runnable {
                val query = text.toString().trim()
                if (query.isNotEmpty()) {
                    Log.i(TAG, "Delayed search triggered for: $query")
                    Helpers.searchToServer(this, query)
                }
            }

            searchHandler.postDelayed(searchRunnable!!, 500) // 1000 ms = 1 second
        }
    }
    private fun updateToggleStates() {
        val isScreenshotRunning = isServiceRunning(this, ScreenshotService::class.java)
        screenshotToggleButton.isChecked = isScreenshotRunning
        screenshotToggleButton.text = getString(R.string.screenshotToggleOnText)
    }
    // endregion

    // region State Related
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        Log.i(TAG, "isServiceRunning | Checking if ${serviceClass.simpleName} is running...")

        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateServiceState(serviceClass: Class<*>, isEnabled: Boolean, preferenceKey: String) {
        val serviceIntent = Intent(this@Main, serviceClass)
        if (isEnabled) {
            Log.i(TAG, "${serviceClass.simpleName} created")
            startForegroundService(serviceIntent)
        } else {
            Log.i(TAG, "${serviceClass.simpleName} stopped")
            stopService(serviceIntent)
        }
        CoroutineScope(Dispatchers.IO).launch {
            generalSharedPref.edit { putBoolean(preferenceKey, isEnabled) }
        }
    }
    // endregion
}