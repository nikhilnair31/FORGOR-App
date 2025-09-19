package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.openExternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.view.isNotEmpty
import com.sil.others.Helpers.Companion.EP

class FullContent : AppCompatActivity() {
    // region Vars
    private val TAG = "FullContent"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var imageView: PhotoView
    private lateinit var similarPostButton: ImageButton
    private lateinit var sharePostButton: ImageButton
    private lateinit var deletePostButton: ImageButton
    private lateinit var linksLinearLayout: LinearLayout
    private lateinit var rootConstraintLayout: ConstraintLayout
    private var isUiVisible = true
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        imageView = findViewById(R.id.fullImageView)
        similarPostButton = findViewById(R.id.similarPostButton)
        sharePostButton = findViewById(R.id.sharePostButton)
        linksLinearLayout = findViewById(R.id.linksLinearLayout)
        deletePostButton = findViewById(R.id.deletePostButton)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val fileId = intent.getIntExtra("fileId", 0)
        val fileName = intent.getStringExtra("fileName") ?: ""
        val tags = intent.getStringExtra("tags") ?: ""
        val lastQuery = generalSharedPreferences.getString("last_query", "") ?: ""

        imageView.setOnPhotoTapListener { _, _, _ ->
            toggleUI()
        }

        initInteraction(fileId, lastQuery)
        initButtons(fileName)
        initLinks(tags)
    }
    // endregion

    // region Server Related
    private fun initInteraction(fileId: Int, lastQuery: String) {
        // Insert to interactions
        Helpers.insertPostInteraction(this, fileId, lastQuery) { success ->
            if (success) {
                Log.i(TAG, "Inserted post interaction")
            } else {
                Log.e(TAG, "Failed to insert post interaction")
            }
        }
    }
    // endregion

    // region UI Related
    private fun toggleUI() {
        isUiVisible = !isUiVisible

        // Animate visibility (optional: remove if you want instant toggle)
        listOf(similarPostButton, sharePostButton, deletePostButton).forEach { button ->
            if (isUiVisible) {
                button.animate().alpha(1f).setDuration(200).withStartAction {
                    button.visibility = View.VISIBLE
                }.start()
            } else {
                button.animate().alpha(0f).setDuration(200).withEndAction {
                    button.visibility = View.GONE
                }.start()
            }
        }

        // Handle link layout separately
        if (isUiVisible && linksLinearLayout.isNotEmpty()) {
            linksLinearLayout.animate().alpha(1f).setDuration(200).withStartAction {
                linksLinearLayout.visibility = View.VISIBLE
            }.start()
        } else {
            linksLinearLayout.animate().alpha(0f).setDuration(200).withEndAction {
                linksLinearLayout.visibility = View.GONE
            }.start()
        }

        // Toggle system UI (status/nav bars)
        if (!isUiVisible) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView).show(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
        }
    }
    private fun initButtons(fileName: String) {
        val fileUrl = "${EP.GET_FILE}/$fileName"

        // Image Handling
        if (Helpers.isImageFile(fileName)) {
            val glideUrl = Helpers.getImageURL(this, fileUrl)
            if (glideUrl != null) {
                imageView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(glideUrl)
                    .dontTransform()
                    .into(imageView)
            }
        }

        // Sharing
        sharePostButton.setOnClickListener {
            Helpers.downloadAndShareFile(this, fileName, fileUrl)
        }

        // Similar
        similarPostButton.setOnClickListener {
            Helpers.getSimilarFromServer(this, fileName) { success, response ->
                if (success && response != null) {
                    val json = JSONObject(response)
                    val resultsArray = json.getJSONArray("results")
                    Log.i(TAG, "resultsArray: $resultsArray")

                    val intent = Intent().apply {
                        putExtra("similar_results_json", json.toString())
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    // Handle failure
                    Log.e(TAG, "Failed to fetch similar results")
                }
            }
        }

        // Deletion
        deletePostButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete screenshot?")
                .setMessage("It will be removed from the server and cannot be recovered. The screenshot will NOT be deleted from your device.")
                .setPositiveButton("Delete") { _, _ ->
                    Helpers.deleteFile(this, fileName)
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(300)
                        setResult(RESULT_OK, Intent().putExtra("deletedFileName", fileName))
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun initLinks(tags: String) {
        // Clear previous links
        linksLinearLayout.removeAllViews()
        linksLinearLayout.visibility = View.GONE

        // Get links else hide links
        val data = try {
            JSONObject(tags)
        } catch (_: Exception) {
            linksLinearLayout.visibility = View.GONE
            return
        }

        // Extract data
        val appName = data.optString("app_name", "")
        val links   = data.optJSONArray("links") ?: JSONArray()
        val handles = data.optJSONArray("account_identifiers") ?: JSONArray()

        // Convert
        val handleUrls = Helpers.resolveHandlesToUrls(appName, handles)

        // Add chips for links and account identifiers
        var addedLink = false
        var addedHandle = false

        fun addChipButton(text: String, onClick: () -> Unit) {
            val chip = layoutInflater.inflate(R.layout.item_link_chip, linksLinearLayout, false)
            val tv = chip.findViewById<TextView>(R.id.chipTextView)
            tv.text = text
            chip.setOnClickListener { onClick() }
            linksLinearLayout.addView(chip)
        }
        fun updateLinksLayoutVisibility() {
            linksLinearLayout.visibility = if (addedLink || addedHandle) View.VISIBLE else View.GONE
        }

        // Tracking for raw URLs
        if (links.length() > 0) {
            Helpers.getTrackingLinks(this, links) { trackingLinks ->
                runOnUiThread {
                    if (trackingLinks != null) {
                        for (i in 0 until trackingLinks.length()) {
                            val obj = trackingLinks.optJSONObject(i)
                            val original = obj?.optString("original") ?: continue
                            val tracking = obj.optString("tracking")
                            addChipButton(
                                text = Helpers.sanitizeLinkLabel(original),
                                onClick = { openExternal(tracking) }
                            )
                            addedLink = true
                        }
                    }
                    updateLinksLayoutVisibility()
                }
            }
        }

        // Tracking for handles (converted to URLs)
        if (handleUrls.length() > 0) {
            Helpers.getTrackingLinks(this, handleUrls) { trackingHandles ->
                runOnUiThread {
                    if (trackingHandles != null) {
                        for (i in 0 until trackingHandles.length()) {
                            val obj = trackingHandles.optJSONObject(i)
                            val original = obj?.optString("original") ?: continue
                            val tracking = obj.optString("tracking")
                            addChipButton(
                                text = Helpers.sanitizeLinkLabel(original),
                                onClick = { openExternal(tracking) }
                            )
                            addedHandle = true
                        }
                    }
                    updateLinksLayoutVisibility()
                }
            }
        }
    }
    // endregion
}
