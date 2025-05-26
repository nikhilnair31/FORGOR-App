package com.sil.buildmode

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.sil.others.Helpers
import androidx.core.net.toUri

class FullImage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        val imagePath = intent.getStringExtra("imagePath") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        val postUrl = intent.getStringExtra("postUrl") ?: ""

        val imageView = findViewById<ImageView>(R.id.fullImageView)
        val linkTextView = findViewById<TextView>(R.id.linkTextView)
        val deleteButton = findViewById<ImageButton>(R.id.deleteButton)

        Glide.with(this)
            .load(imageUrl)
            .into(imageView)

        if (postUrl.isNotBlank() && postUrl != "-") {
            linkTextView.setOnClickListener {
                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, postUrl.toUri())
                startActivity(browserIntent)
            }
        } else {
            linkTextView.visibility = android.view.View.GONE
        }

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this image?")
                .setPositiveButton("Delete") { _, _ ->
                    Helpers.deleteImageFile(this, imagePath)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
