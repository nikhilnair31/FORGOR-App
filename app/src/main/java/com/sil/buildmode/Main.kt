package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sil.others.Helpers
import com.sil.services.ScreenshotService
import com.sil.workers.TokenRefreshWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import androidx.core.content.edit

class Main : AppCompatActivity() {
    // region Vars
    private val TAG = "Main"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var layoutManager: StaggeredGridLayoutManager
    private val spanOptions = listOf(2, 3, 4)
    private val spanIcons = listOf(
        R.drawable.outline_view_comfy_alt_24,   // 1 column
        R.drawable.baseline_view_module_24,  // 2 columns
        R.drawable.outline_view_compact_24     // 3 columns
    )
    private var currentSpanIndex = 0

    private lateinit var rootConstraintLayout: ConstraintLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var optionsButtonsLayout: LinearLayout
    private lateinit var searchTextLayout: ConstraintLayout

    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchTextWatcherEnabled = true
    private var searchRunnable: Runnable? = null
    private lateinit var resultAdapter: ResultAdapter

    private lateinit var searchEditText: EditText
    private lateinit var optionsExpandButton: ImageButton
    private lateinit var fileUploadButton: ImageButton
    private lateinit var sizeToggleButton: ImageButton
    private lateinit var settingsButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initUI()
        initData()
        checkScreenshotServiceStatus()
        scheduleTokenRefreshWorker()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val deletedFileName = data?.getStringExtra("deletedFileName")
            val similarResultsJson = data?.getStringExtra("similar_results_json")

            if (!deletedFileName.isNullOrEmpty()) {
                val currentList = resultAdapter.getData()
                val updatedList = currentList.filter { it.optString("file_name") != deletedFileName }
                resultAdapter.updateData(updatedList)

                if (updatedList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    recyclerView.fadeOut()
                }
            }

            if (!similarResultsJson.isNullOrEmpty()) {
                try {
                    val json = JSONObject(similarResultsJson)
                    val results = json.getJSONArray("results")
                    val resultList = List(results.length()) { i -> results.getJSONObject(i) }

                    searchTextWatcherEnabled = false
                    searchEditText.setText("")
                    searchTextWatcherEnabled = true

                    generalSharedPreferences.edit().apply {
                        putString("last_query", "")
                        putString("last_results_json", similarResultsJson)
                        apply()
                    }

                    resultAdapter.updateData(resultList)
                    // resultAdapter.notifyDataSetChanged()
                    recyclerView.fadeIn()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse similar results: ${e.localizedMessage}")
                }
            }
        }
    }
    // endregion

    // region Data Related
    private fun initData() {
        val savedQuery = generalSharedPreferences.getString("last_query", "") ?: ""
        if (savedQuery.isNotEmpty()) {
            searchTextWatcherEnabled = false
            searchEditText.setText(savedQuery)
            searchTextWatcherEnabled = true
        }

        val savedResultsJson = generalSharedPreferences.getString("last_results_json", "") ?: ""
        if (savedResultsJson.isNotEmpty()) {
            try {
                val json = JSONObject(savedResultsJson)
                val results = json.getJSONArray("results")
                val resultList = List(results.length()) { i -> results.getJSONObject(i) }

                if (resultList.isNotEmpty()) {
                    resultAdapter.updateData(resultList)
                    recyclerView.fadeIn()
                } else {
                    recyclerView.fadeOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring saved results: ${e.localizedMessage}")
            }
        }
    }
    // endregion

    // region UI Related
    private fun initUI() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        optionsButtonsLayout = findViewById(R.id.optionsButtonsLayout)
        searchTextLayout = findViewById(R.id.searchTextLayout)
        recyclerView = findViewById(R.id.imageRecyclerView)
        optionsExpandButton = findViewById(R.id.optionsButton)
        fileUploadButton = findViewById(R.id.fileUploadButton)
        sizeToggleButton = findViewById(R.id.sizeToggleButton)
        settingsButton = findViewById(R.id.settingsButton)
        searchEditText = findViewById(R.id.searchEditText)

        resultAdapter = ResultAdapter(this, mutableListOf())

        recyclerView.adapter = resultAdapter
        recyclerView.itemAnimator = DefaultItemAnimator()

        currentSpanIndex = generalSharedPreferences.getInt("grid_span_index", 0)
        layoutManager = StaggeredGridLayoutManager(spanOptions[currentSpanIndex], StaggeredGridLayoutManager.VERTICAL)
        sizeToggleButton.setImageResource(spanIcons[currentSpanIndex])
        layoutManager.spanCount = spanOptions[currentSpanIndex]
        recyclerView.layoutManager = layoutManager

        optionsExpandButton.setOnClickListener {
            if (optionsButtonsLayout.isVisible) {
                optionsButtonsLayout.visibility = View.GONE
            } else {
                optionsButtonsLayout.visibility = View.VISIBLE

            }
        }
        fileUploadButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
        sizeToggleButton.setOnClickListener {
            currentSpanIndex = (currentSpanIndex + 1) % spanOptions.size
            val newSpanCount = spanOptions[currentSpanIndex]
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()

            sizeToggleButton.setImageResource(spanIcons[currentSpanIndex])

            // (optional) persist preference
            generalSharedPreferences.edit {
                putInt("grid_span_index", currentSpanIndex)
            }
        }

        searchEditText.doAfterTextChanged { text ->
            searchQueryUpdated(text.toString())
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomForContent = max(ime.bottom + 36, sys.bottom)

            // content above IME/nav
            rootConstraintLayout.updatePadding(
                bottom = bottomForContent - 64
            )

            // make the neon bar extend under the gesture area
//            searchEditText.updatePadding(
//                left = searchEditText.paddingLeft,
//                top = sys.bottom,
//                right = searchEditText.paddingRight,
//                bottom = sys.bottom// + 24 /* your original bottom padding */
//            )

            insets
        }
    }

    private fun searchQueryUpdated(text: String) {
        if (!searchTextWatcherEnabled) return

        searchRunnable?.let { searchHandler.removeCallbacks(it) }

        searchRunnable = Runnable {
            val query = text.trim()

            if (query.isEmpty()) {
                Log.i(TAG, "Empty search triggered")

                resultAdapter.updateData(emptyList())
                recyclerView.fadeOut()

                return@Runnable
            }

            Log.i(TAG, "Delayed search triggered for: $query")

            Helpers.searchToServer(this, query) { response  ->
                response?.let {
                    generalSharedPreferences.edit().apply {
                        putString("last_query", query)
                        putString("last_results_json", it)
                        apply()
                    }
                }

                runOnUiThread {
                    if (response == null) {
                        Log.e(TAG, "Query returned null")

                        resultAdapter.updateData(emptyList())
                        recyclerView.fadeOut()

                        return@runOnUiThread
                    }

                    try {
                        val json = JSONObject(response)
                        val results = json.getJSONArray("results")
                        val resultList = List(results.length()) { i -> results.getJSONObject(i) }
                        Log.i(TAG, "Query returned ${results.length()} results")

                        if (resultList.isEmpty()) {
                            resultAdapter.updateData(emptyList())
                            recyclerView.fadeOut()
                        } else {
                            resultAdapter.updateData(resultList)
                            recyclerView.fadeIn()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response: ${e.localizedMessage}")
                    }
                }
            }
        }

        searchHandler.postDelayed(searchRunnable!!, 300) // 1000 ms = 1 second
    }

    private fun View.fadeIn(duration: Long = 200, delay: Long = 0) {
        if (!isVisible || alpha < 1f) {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f)
                .setStartDelay(delay)
                .setDuration(duration)
                .start()
        }
    }
    private fun View.fadeOut(duration: Long = 200, delay: Long = 0, endAction: (() -> Unit)? = null) {
        if (isVisible && alpha > 0f) {
            animate().alpha(0f)
                .setStartDelay(delay)
                .setDuration(duration)
                .withEndAction {
                    visibility = View.GONE
                    endAction?.invoke()
                }
                .start()
        }
    }
    // endregion

    // region Worker Related
    private fun scheduleTokenRefreshWorker() {
        val refreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(11, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TokenRefreshWork",
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest
        )
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