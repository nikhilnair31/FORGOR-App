package com.sil.buildmode

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers
import androidx.core.net.toUri
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

        textScrollView = findViewById(R.id.textScrollView)
        imageView = findViewById(R.id.fullImageView)
        linkTextView = findViewById(R.id.linkTextView)
        textTextView = findViewById(R.id.textText)
        deleteButton = findViewById(R.id.deleteButton)

        val imagePath = intent.getStringExtra("imagePath") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        val postUrl = intent.getStringExtra("postUrl") ?: ""
        val textContent = intent.getStringExtra("textContent")

        initRelated(imagePath, imageUrl, postUrl, textContent)
    }

    fun initRelated(imagePath: String, imageUrl: String, postUrl: String, textContent: String?) {
        // Handle image content
        if (imagePath.isNotBlank()) {
            imageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
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
            linkTextView.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
                startActivity(browserIntent)
            }
        } else {
            linkTextView.visibility = View.GONE
        }

        // Handle delete
        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete this file?")
                .setPositiveButton("Delete") { _, _ ->
                    Helpers.deleteImageFile(this, imagePath)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
