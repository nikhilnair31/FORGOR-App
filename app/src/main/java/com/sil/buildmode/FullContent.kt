package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sil.others.Helpers
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.bhuvaneshw.pdf.PdfViewer
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullContent : AppCompatActivity() {
    // region Vars
    private val TAG = "FullContent"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private val APP_KEY = BuildConfig.APP_KEY
    private val USER_AGENT = BuildConfig.USER_AGENT
    private val SERVER_URL = BuildConfig.SERVER_URL

    private lateinit var imageView: PhotoView
    private lateinit var pdfViewer: PdfViewer
    private lateinit var textScrollView: View
    private lateinit var textTextView: TextView
    private lateinit var linkButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var deleteButton: ImageButton
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.base_1)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        pdfViewer = findViewById(R.id.pdf_viewer)
        imageView = findViewById(R.id.fullImageView)
        textScrollView = findViewById(R.id.textScrollView)
        textTextView = findViewById(R.id.textText)
        linkButton = findViewById(R.id.linkButton)
        shareButton = findViewById(R.id.shareButton)
        deleteButton = findViewById(R.id.deleteButton)

        val fileName = intent.getStringExtra("fileName") ?: ""
        val postUrl = intent.getStringExtra("postUrl") ?: ""
        Log.i(TAG, "fileName: $fileName postUrl: $postUrl")

        val fileUrl = "$SERVER_URL/api/get_file/$fileName"

        initRelated(fileName, fileUrl, postUrl, "")
    }

    fun initRelated(fileName: String, fileUrl: String, postUrl: String, textContent: String?) {
        // PDF Handling
        if (fileName.endsWith(".pdf", ignoreCase = true)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localFile = Helpers.downloadPdfToCache(this@FullContent, fileUrl)

                    withContext(Dispatchers.Main) {
                        pdfViewer.visibility = View.VISIBLE

                        pdfViewer.onReady {
                            // ✅ Load local file only after viewer is initialized
                            try {
                                pdfViewer.load(localFile.absolutePath)
                            } catch (e: Exception) {
                                Log.e(TAG, "PDF load failed inside onReady", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load PDF", e)
                }
            }
        }

        // Image Handling
        else if (fileName.isNotBlank()) {
            val glideUrl = Helpers.getImageURL(this, fileUrl)
            if (glideUrl != null) {
                imageView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(glideUrl)
                    .dontTransform()
                    .into(imageView)
            }
        }

        // Text Handling
        else if (!textContent.isNullOrBlank()) {
            imageView.visibility = View.GONE
            textScrollView.visibility = View.VISIBLE
            textTextView.text = textContent
        }

        // Handle post URL
        if (postUrl.isNotBlank() && postUrl != "-") {
            linkButton.visibility = View.VISIBLE
            linkButton.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
                startActivity(browserIntent)
            }
        }

        // Sharing
        shareButton.setOnClickListener {
            if (fileName.isNotBlank()) {
                Helpers.downloadAndShareFile(this, fileName, fileUrl, postUrl)
            }
        }

        // Deletion
        deleteButton.setOnClickListener {
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
}
