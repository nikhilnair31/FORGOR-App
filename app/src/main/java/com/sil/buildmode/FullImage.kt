package com.sil.buildmode

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class FullImage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        val postUrl = intent.getStringExtra("postUrl") ?: ""

        val imageView = findViewById<ImageView>(R.id.fullImageView)
        val linkTextView = findViewById<TextView>(R.id.linkTextView)
        val deleteButton = findViewById<ImageButton>(R.id.deleteButton)

        Glide.with(this)
            .load(imageUrl)
            .into(imageView)

        if (postUrl.isNotBlank() && postUrl != "-") {
            linkTextView.text = "View Post"
            linkTextView.setOnClickListener {
                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(postUrl))
                startActivity(browserIntent)
            }
        } else {
            linkTextView.visibility = android.view.View.GONE
        }

        deleteButton.setOnClickListener {
            // Handle delete button click
        }
    }
}
