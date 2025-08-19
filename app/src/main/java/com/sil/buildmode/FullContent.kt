package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
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
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private val APP_KEY = BuildConfig.APP_KEY
    private val USER_AGENT = BuildConfig.USER_AGENT
    private val SERVER_URL = BuildConfig.SERVER_URL

    private lateinit var imageView: PhotoView
    private lateinit var similarPostButton: ImageButton
    private lateinit var sharePostButton: ImageButton
    private lateinit var deletePostButton: ImageButton
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        imageView = findViewById(R.id.fullImageView)
        similarPostButton = findViewById(R.id.similarPostButton)
        sharePostButton = findViewById(R.id.sharePostButton)
        deletePostButton = findViewById(R.id.deletePostButton)

        val fileName = intent.getStringExtra("fileName") ?: ""
        Log.i(TAG, "fileName: $fileName")

        val fileUrl = "$SERVER_URL/api/get_file/$fileName"
        Log.i(TAG, "fileUrl: $fileUrl")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
        }

        initRelated(fileName, fileUrl)
    }

    private fun initRelated(fileName: String, fileUrl: String) {
        // Image Handling
        if (Helpers.isImageFile(fileUrl)) {
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
}
