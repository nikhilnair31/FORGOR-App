package com.sil.buildmode

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.json.JSONObject
import androidx.core.graphics.drawable.toDrawable
import com.sil.others.Helpers

class ResultAdapter(private val context: Context, private val dataList: List<JSONObject>) :
    RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val linkIcon: ImageView = view.findViewById(R.id.linkIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_card, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = dataList[position]
        val rawUrl = item.optString("image_presigned_url", "")
        val imageUrl = if (rawUrl.startsWith("http")) rawUrl else BuildConfig.SERVER_URL + rawUrl
        val postUrl = item.optString("post_url", "").trim()

        val blankDrawable = R.color.accent_0.toDrawable() // or use Color.LTGRAY or any color you want
        if (imageUrl.isBlank()) {
            holder.imageView.setImageDrawable(blankDrawable)
        } else {
            val glideUrl = Helpers.getImageURL(context, imageUrl)
            Glide.with(context)
                .load(glideUrl)
                .apply(
                    RequestOptions()
                        .placeholder(blankDrawable)
                        .error(blankDrawable)
                )
                .into(holder.imageView)
        }

        // Show and handle link icon only if post_url is valid
        holder.itemView.setOnClickListener {
            if (imageUrl.isNotBlank()) {
                showImagePopup(imageUrl)
            }
        }
    }

    override fun getItemCount(): Int = dataList.size

    private fun showImagePopup(imageUrl: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_full_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImageView)

        Glide.with(context)
            .load(imageUrl)
            .into(imageView)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}