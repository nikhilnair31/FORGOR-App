package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sil.others.Helpers
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide

class FullContent : AppCompatActivity() {
    // region Vars
    private lateinit var textScrollView: View
    private lateinit var imageView: ImageView
    private lateinit var linkTextView: TextView
    private lateinit var textTextView: TextView
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
}
