package com.sil.buildmode

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.edit
import com.sil.buildmode.Settings
import com.sil.others.Helpers
import com.sil.services.ScreenshotService

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_FIRST_RUN = "isFirstRun"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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
        checkServiceStatus()
    }

    private fun initSharedPreferences() {
        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
        if (generalSharedPreferences.getBoolean(KEY_FIRST_RUN, true)) {
            generalSharedPreferences.edit { putBoolean(KEY_FIRST_RUN, false) }
        }
    }
    private fun initViews() {
        settingsButton = findViewById(R.id.settingsButton)
        searchEditText = findViewById(R.id.searchEditText)
    }
    private fun setupListeners() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
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
    // endregion

    // region Service Related
    private fun checkServiceStatus() {
        val wasScreenshotServiceRunning = generalSharedPreferences.getBoolean(KEY_SCREENSHOT_ENABLED, false)
        val isScreenshotServiceRunning = Helpers.isServiceRunning(this, ScreenshotService::class.java)
        if (wasScreenshotServiceRunning && !isScreenshotServiceRunning) {
            Log.i(TAG, "ScreenshotService was running but is not running anymore. Starting it again.")
            val serviceIntent = Intent(this@Main, ScreenshotService::class.java)
            startForegroundService(serviceIntent)
        }
        else if (!wasScreenshotServiceRunning && isScreenshotServiceRunning) {
            Log.i(TAG, "ScreenshotService was not running but is running now. Stopping it.")
            val serviceIntent = Intent(this@Main, ScreenshotService::class.java)
            stopService(serviceIntent)
        }
        else {
            Log.i(TAG, "ScreenshotService status is as expected.")
        }
    }
    // endregion
}