package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.openExternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class FullContent : AppCompatActivity() {
    // region Vars
    private val TAG = "FullContent"

    private val SERVER_URL = BuildConfig.SERVER_URL

    private lateinit var imageView: PhotoView
    private lateinit var similarPostButton: ImageButton
    private lateinit var sharePostButton: ImageButton
    private lateinit var deletePostButton: ImageButton
    private lateinit var linksLinearLayout: LinearLayout
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        imageView = findViewById(R.id.fullImageView)
        similarPostButton = findViewById(R.id.similarPostButton)
        sharePostButton = findViewById(R.id.sharePostButton)
        linksLinearLayout = findViewById(R.id.linksLinearLayout)
        deletePostButton = findViewById(R.id.deletePostButton)

        val fileName = intent.getStringExtra("fileName") ?: ""
        val tags = intent.getStringExtra("tags") ?: ""

        WindowCompat.setDecorFitsSystemWindows(window, false)

        initButtons(fileName)
        fillChips(tags)
    }

    private fun initButtons(fileName: String) {
        val fileUrl = "$SERVER_URL/api/get_file/$fileName"

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
            AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete this file?")
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

    private fun fillChips(tags: String) {
        val data = JSONObject(tags)               // expects the shape you showed as “data”
        val appName = data.optString("app_name")
        val links = data.optJSONArray("links")
        val handles = data.optJSONArray("account_identifiers")
        Log.i(TAG, "appName: $appName, links: $links, handles: $handles")

        linksLinearLayout.removeAllViews()

        var added = false
        if (links != null && links.length() > 0) {
            for (i in 0 until links.length()) {
                val link = links.optString(i)?.trim().orEmpty()
                if (link.isNotEmpty()) {
                    addChipButton(
                        text = Helpers.sanitizeLinkLabel(link),
                        onClick = { this.openExternal(link) }
                    )
                    added = true
                }
            }
        }
        if (handles != null && handles.length() > 0) {
            for (i in 0 until handles.length()) {
                val handle = handles.optString(i)?.trim().orEmpty()
                if (handle.isNotEmpty()) {
                    val resolved = Helpers.resolveHandleToUrl(appName, handle)
                    addChipButton(
                        text = handle,
                        onClick = { this.openExternal(resolved) }
                    )
                    added = true
                }
            }
        }

        linksLinearLayout.visibility = if (added) View.VISIBLE else View.GONE
    }

    private fun addChipButton(text: String, onClick: () -> Unit) {
        val chip = layoutInflater.inflate(R.layout.item_link_chip, linksLinearLayout, false)
        val tv = chip.findViewById<TextView>(R.id.chipTextView)
        tv.text = text
        chip.setOnClickListener { onClick() }
        linksLinearLayout.addView(chip)
    }
}
