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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
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
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService
import com.sil.workers.TokenRefreshWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

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

    private lateinit var filterChipRow: LinearLayout
    private lateinit var filterChipScroll: View
    private var allResults: List<JSONObject> = emptyList() // base list for filters
    private val filterOptions = arrayOf("Social Media", "Flights", "Music", "Tracking", "TV/Movie", "Food", "Places")
    private var activeFilters: MutableSet<String> = mutableSetOf()

    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchTextWatcherEnabled = true
    private var searchRunnable: Runnable? = null
    private lateinit var resultAdapter: ResultAdapter

    private lateinit var emptyGroupLayout: ConstraintLayout
    private lateinit var emptyPlaceholder: TextView

    private lateinit var searchTextLayout: ConstraintLayout
    private lateinit var searchEditText: EditText
    private lateinit var optionsExpandButton: ImageButton

    private lateinit var optionsButtonsLayout: LinearLayout
    private lateinit var settingsButton: ImageButton
    private lateinit var sizeToggleButton: ImageButton
    private lateinit var fileUploadButton: ImageButton
    private lateinit var filterPostsButton: ImageButton

    private lateinit var rootConstraintLayout: ConstraintLayout
    private lateinit var recyclerView: RecyclerView
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
        emptyGroupLayout = findViewById(R.id.emptyGroupLayout)
        emptyPlaceholder = findViewById(R.id.emptyPlaceholder)
        recyclerView = findViewById(R.id.imageRecyclerView)
        optionsExpandButton = findViewById(R.id.optionsButton)
        settingsButton = findViewById(R.id.settingsButton)
        sizeToggleButton = findViewById(R.id.sizeToggleButton)
        fileUploadButton = findViewById(R.id.fileUploadButton)
        filterPostsButton = findViewById(R.id.filterPostsButton)
        filterChipScroll = findViewById(R.id.filterChipScroll)
        filterChipRow = findViewById(R.id.filterChipRow)
        searchEditText = findViewById(R.id.searchEditText)

        resultAdapter = ResultAdapter(this, mutableListOf()).apply {
            setHasStableIds(true)                                        // ✨ stable IDs
        }

        recyclerView.adapter = resultAdapter
        recyclerView.itemAnimator = DefaultItemAnimator()

        currentSpanIndex = generalSharedPreferences.getInt("grid_span_index", 0)
        layoutManager = StaggeredGridLayoutManager(
            spanOptions[currentSpanIndex],
            StaggeredGridLayoutManager.VERTICAL
        )
        sizeToggleButton.setImageResource(spanIcons[currentSpanIndex])
        layoutManager.spanCount = spanOptions[currentSpanIndex]
        recyclerView.layoutManager = layoutManager
        recyclerView.setItemAnimator(null);

        optionsExpandButton.setOnClickListener {
            if (optionsButtonsLayout.isVisible) {
                optionsButtonsLayout.visibility = View.GONE
                filterChipScroll.visibility = View.GONE
            } else {
                optionsButtonsLayout.visibility = View.VISIBLE
            }
        }
        filterPostsButton.setOnClickListener {
            filterChipScroll.visibility = if (filterChipScroll.isVisible) View.GONE else View.VISIBLE
        }
        fileUploadButton.setOnClickListener {
            pickImagesLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
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

            generalSharedPreferences.edit {
                putInt("grid_span_index", currentSpanIndex)
            }
        }

        searchEditText.doAfterTextChanged { text ->
            searchQueryUpdated(text.toString())
        }

        buildFilterChips()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomForContent = max(ime.bottom + 36, sys.bottom)

            rootConstraintLayout.updatePadding(bottom = bottomForContent - 64)
            insets
        }
    }

    private fun buildFilterChips() {
        filterChipRow.removeAllViews()
        for (opt in filterOptions) {
            val chip = layoutInflater.inflate(R.layout.item_link_chip, filterChipRow, false)
            val tv = chip.findViewById<TextView>(R.id.chipTextView)
            tv.text = opt

            chip.setOnClickListener {
                Log.i(TAG, "activeFilters: $activeFilters | opt: $opt")
                val nowSelected = !activeFilters.contains(opt)
                Log.i(TAG, "nowSelected: $nowSelected")
                if (nowSelected) activeFilters.add(opt) else activeFilters.remove(opt)
                Log.i(TAG, "activeFilters: $activeFilters")
                setChipSelected(chip, nowSelected)
                applyFilters()
            }
            filterChipRow.addView(chip)
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

            if (!Helpers.isConnectedFast(this)) {
                emptyPlaceholder.text = getString(R.string.networkSlow)
                showToast( this, "No or slow internet connection")
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
                        emptyPlaceholder.text = getString(R.string.noResultsFound)
                        emptyGroupLayout.fadeIn()

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
                            emptyGroupLayout.fadeIn()
                        } else {
                            allResults = resultList
                            resultAdapter.updateData(allResults)
                            recyclerView.scrollToPosition(0)
                            recyclerView.fadeIn()
                            emptyGroupLayout.fadeOut()
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

    // region Data Related
    private fun applyFilters() {
        val baseList = allResults
        Log.i(TAG, "baseList: ${baseList.size}")

        if (activeFilters.isEmpty()) {
            // No filters → show all
            resultAdapter.updateData(baseList)
            recyclerView.fadeIn()
            return
        }

        val filteredList = baseList.filter { item ->
            val tags = item.optString("tags", "")
            Log.i(TAG, "tags: $tags")
            activeFilters.any { filter ->
                tags.contains(filter, ignoreCase = true)
            }
        }
        Log.i(TAG, "filteredList: ${filteredList.size}")

        resultAdapter.updateData(filteredList.toMutableList())

        if (filteredList.isEmpty()) {
            recyclerView.fadeOut()
        } else {
            recyclerView.fadeIn()
        }
    }
    private fun setChipSelected(chip: View, selected: Boolean) {
        chip.isSelected = selected
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
    }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNullOrEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        // Persist temporary read permission
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
        }

        // Forward to your existing Share activity
        val sendIntent = Intent(this, Share::class.java).apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(sendIntent)
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