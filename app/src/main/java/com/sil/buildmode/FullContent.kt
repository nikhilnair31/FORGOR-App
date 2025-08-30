package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.sil.others.Helpers
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.bhuvaneshw.pdf.PdfViewer
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.sil.others.Helpers.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

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

        initRelated(fileName)
        fillChips(tags)
    }

    private fun initRelated(fileName: String) {
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

        linksLinearLayout.removeAllViews()

        var added = false
        if (links != null && links.length() > 0) {
            for (i in 0 until links.length()) {
                val link = links.optString(i)?.trim().orEmpty()
                if (link.isNotEmpty()) {
                    addChipButton(
                        text = sanitizeLinkLabel(link),
                        onClick = { openExternal(link) }
                    )
                    added = true
                }
            }
        }
        if (handles != null && handles.length() > 0) {
            for (i in 0 until handles.length()) {
                val handle = handles.optString(i)?.trim().orEmpty()
                if (handle.isNotEmpty()) {
                    val resolved = resolveHandleToUrl(appName, handle)
                    addChipButton(
                        text = handle,
                        onClick = { openExternal(resolved) }
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

    private fun sanitizeLinkLabel(url: String): String {
        return try {
            val u = url.toUri()
            val host = u.host?.replace("www.", "") ?: url
            val path = u.path.orEmpty().trim('/').takeIf { it.isNotEmpty() } ?: ""
            if (path.isEmpty()) host else "$host/$path"
        } catch (_: Exception) { url }
    }

    private fun resolveHandleToUrl(appName: String, raw: String): String {
        val handle = raw.removePrefix("@")
        return when (appName.lowercase()) {
            "youtube" -> "https://www.youtube.com/@$handle"
            "instagram" -> "https://www.instagram.com/$handle/"
            "twitter", "x" -> "https://x.com/$handle"
            "tiktok" -> "https://www.tiktok.com/@$handle"
            "reddit" -> "https://www.reddit.com/user/$handle"
            else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open $url: ${e.localizedMessage}")
        }
    }
}
