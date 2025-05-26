package com.sil.buildmode

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.view.WindowCompat
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.sil.others.Helpers
import com.sil.services.ScreenshotService
import org.json.JSONObject

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"
    private val KEY_OVERLAY_ENABLED = "isOverlayEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    lateinit var resultAdapter: ResultAdapter
    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private lateinit var searchEditText: EditText
    private lateinit var settingsButton: ImageButton
    private lateinit var placeholder: TextView
    private lateinit var recyclerView: RecyclerView
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initUI()
        checkScreenshotServiceStatus()
    }

    private fun initUI() {
        window.navigationBarColor = ContextCompat.getColor(this, R.color.accent_0)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        placeholder = findViewById(R.id.emptyPlaceholder)
        placeholder.visibility = View.GONE

        resultAdapter = ResultAdapter(this, mutableListOf())
        recyclerView = findViewById(R.id.imageRecyclerView)
        recyclerView.adapter = resultAdapter
        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }

        searchEditText = findViewById(R.id.searchEditText)
        searchEditText.doAfterTextChanged { text ->
            searchRunnable?.let { searchHandler.removeCallbacks(it) }

            searchRunnable = Runnable {
                val query = text.toString().trim()

                if (query.isEmpty()) {
                    Log.i(TAG, "Empty search triggered")

                    resultAdapter.updateData(emptyList())
                    recyclerView.visibility = View.GONE
                    placeholder.visibility = View.GONE

                    return@Runnable
                }

                Log.i(TAG, "Delayed search triggered for: $query")

                Helpers.searchToServer(this, query) { response  ->
                    runOnUiThread {
                        if (response == null) {
                            Log.e(TAG, "Query returned null")

                            resultAdapter.updateData(emptyList())
                            recyclerView.visibility = View.GONE
                            placeholder.visibility = View.VISIBLE

                            return@runOnUiThread
                        }

                        try {
                            val json = JSONObject(response)
                            val results = json.getJSONArray("results")
                            val resultList = List(results.length()) { i -> results.getJSONObject(i) }

                            Log.i(TAG, "Query returned ${results.length()} results")

                            if (resultList.isEmpty()) {
                                resultAdapter.updateData(emptyList())
                                recyclerView.visibility = View.GONE
                                placeholder.visibility = View.VISIBLE
                            } else {
                                resultAdapter.updateData(resultList)
                                recyclerView.visibility = View.VISIBLE
                                placeholder.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing response: ${e.localizedMessage}")
                        }
                    }
                }
            }

            searchHandler.postDelayed(searchRunnable!!, 500) // 1000 ms = 1 second
        }
    }
    // endregion

    // region Service Related
    private fun checkScreenshotServiceStatus() {
        val wasScreenshotServiceRunning = generalSharedPreferences.getBoolean(KEY_SCREENSHOT_ENABLED, false)
        val isScreenshotServiceRunning = Helpers.isServiceRunning(this, ScreenshotService::class.java)

        val screenshotServiceIntent = Intent(this, ScreenshotService::class.java)

        if (wasScreenshotServiceRunning && !isScreenshotServiceRunning) {
            Log.i(TAG, "ScreenshotService was running but is not running anymore. Starting it again.")
            startForegroundService(screenshotServiceIntent)
        }
        else if (!wasScreenshotServiceRunning && isScreenshotServiceRunning) {
            Log.i(TAG, "ScreenshotService was not running but is running now. Stopping it.")
            stopService(screenshotServiceIntent)
        }
        else {
            Log.i(TAG, "ScreenshotService status is as expected.")
        }
    }
    // endregion
}