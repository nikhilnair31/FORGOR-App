package com.sil.buildmode

import android.content.Context
import android.content.Intent
import com.bumptech.glide.load.model.GlideUrl
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.json.JSONObject
import androidx.core.graphics.drawable.toDrawable
import com.sil.others.Helpers
import androidx.core.net.toUri
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ResultAdapter(private val context: Context, private val dataList: MutableList<JSONObject>) :
    RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {
    private val SERVER_URL = BuildConfig.SERVER_URL

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val linkIcon: ImageView = view.findViewById(R.id.linkIcon)
    }

    fun updateData(newData: List<JSONObject>) {
        dataList.clear()
        dataList.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_card, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = dataList[position]
        val rawUrl = item.optString("image_path", "")
        val imageUrl = if (rawUrl.startsWith("http")) rawUrl else "$SERVER_URL/api/get_image/$rawUrl"
        val postUrl = item.optString("post_url", "").trim()
        Log.i("ResultAdapter", "imageUrl: $imageUrl, postUrl: $postUrl")

        val blankDrawable = R.color.accent_0.toDrawable()
        val glideUrl = Helpers.getImageURL(context, imageUrl)

        if (glideUrl == null) {
            holder.imageView.setImageDrawable(blankDrawable)
            return
        }

        Glide.with(context)
            .load(glideUrl)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.color.accent_0.toDrawable())
                    .error(R.color.accent_0.toDrawable())
                    .fitCenter()  // or centerCrop() if you want more even alignment
            )
            .into(holder.imageView)

        // Show link icon if postUrl is valid
        if (postUrl.isBlank() || postUrl == "-") {
            holder.linkIcon.visibility = View.GONE
        } else {
            holder.linkIcon.visibility = View.VISIBLE
            holder.linkIcon.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
                context.startActivity(intent)
            }
        }

        holder.itemView.setOnClickListener {
            if (imageUrl.isNotBlank()) {
                val intent = Intent(context, FullImage::class.java)
                intent.putExtra("imageUrl", imageUrl)
                intent.putExtra("postUrl", postUrl)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = dataList.size

    private fun loadImageWithRetry(
        imageView: ImageView,
        glideUrl: GlideUrl,
        attempt: Int,
        maxRetries: Int,
        baseDelayMillis: Long = 300L
    ) {
        Glide.with(context)
            .load(glideUrl)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // ✅ cache both original and transformed
                    .placeholder(R.color.accent_0.toDrawable())
                    .error(R.color.accent_0.toDrawable())
                    .dontTransform()
            )
            .into(object : com.bumptech.glide.request.target.CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    imageView.setImageDrawable(placeholder)
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: com.bumptech.glide.request.transition.Transition<in Drawable>?
                ) {
                    imageView.setImageDrawable(resource)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w("ResultAdapter", "onLoadFailed for $glideUrl (attempt $attempt)")

                    if (attempt < maxRetries) {
                        val delay = baseDelayMillis * (1 shl attempt)

                        imageView.postDelayed({
                            Log.i("ResultAdapter", "Retrying $glideUrl (attempt ${attempt + 1}) after ${delay}ms")
                            loadImageWithRetry(imageView, glideUrl, attempt + 1, maxRetries, baseDelayMillis)
                        }, delay)
                    } else {
                        Log.e("ResultAdapter", "Image load failed after $maxRetries attempts: $glideUrl")
                        imageView.setImageDrawable(errorDrawable)
                    }
                }
            })
    }
}