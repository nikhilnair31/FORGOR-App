package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sil.others.Helpers
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class FullContent : AppCompatActivity() {
    // region Vars
    private val TAG = "FullContent"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private val APP_KEY = BuildConfig.APP_KEY
    private val USER_AGENT = BuildConfig.USER_AGENT

    private lateinit var textScrollView: View
    private lateinit var imageView: ImageView
    private lateinit var linkTextView: TextView
    private lateinit var textTextView: TextView
    private lateinit var shareButton: ImageButton
    private lateinit var deleteButton: ImageButton
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.base_1)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        textScrollView = findViewById(R.id.textScrollView)
        imageView = findViewById(R.id.fullImageView)
        linkTextView = findViewById(R.id.linkTextView)
        textTextView = findViewById(R.id.textText)
        shareButton = findViewById(R.id.shareButton)
        deleteButton = findViewById(R.id.deleteButton)

        val fileName = intent.getStringExtra("fileName") ?: ""
        val fileUrl = intent.getStringExtra("fileUrl") ?: ""
        val postUrl = intent.getStringExtra("postUrl") ?: ""
        val textContent = intent.getStringExtra("textContent")

        initRelated(fileName, fileUrl, postUrl, textContent)
    }

    fun initRelated(fileName: String, fileUrl: String, postUrl: String, textContent: String?) {
        // Handle image content
        if (fileName.isNotBlank()) {
            imageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(fileUrl)
                .into(imageView)
        }

        // Handle text content
        if (!textContent.isNullOrBlank()) {
            imageView.visibility = View.GONE
            textScrollView.visibility = View.VISIBLE
            textTextView.text = textContent
        }

        // Handle post URL
        if (postUrl.isNotBlank() && postUrl != "-") {
            linkTextView.visibility = View.VISIBLE
            linkTextView.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
                startActivity(browserIntent)
            }
        }

        // Handle sharing
        shareButton.setOnClickListener {
            if (fileName.isNotBlank()) {
                downloadAndShareFile(fileName, fileUrl)
            }
        }

        // Handle delete
        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete this file?")
                .setPositiveButton("Delete") { _, _ ->
                    Helpers.deleteFile(this, fileName)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun downloadAndShareFile(fileName: String, downloadUrl: String) {
        Log.d(TAG, "Attempting to download from: $downloadUrl")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPrefs = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
                val accessToken = sharedPrefs.getString("access_token", "") ?: ""
                if (accessToken.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Helpers.showToast(this@FullContent, "Not logged in")
                    }
                    return@launch
                }

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val file = File(cacheDir, fileName)
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val fileUri = FileProvider.getUriForFile(
                        this@FullContent,
                        "${BuildConfig.APPLICATION_ID}.provider",
                        file
                    )

                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = getMimeType(file.name) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share file via"))
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Error ${response.code}: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
            }
        }
    }

    fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
