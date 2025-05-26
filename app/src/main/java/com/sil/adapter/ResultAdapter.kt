package com.sil.buildmode

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ResultAdapter(private val context: Context, private val dataList: MutableList<JSONObject>) :
RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {
    // region Vars
    private val TAG = "ResultAdapter"
    private val SERVER_URL = BuildConfig.SERVER_URL
    // endregion

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
        Log.i(TAG, "item: $item")

        val imagePath = item.optString("image_path", "")
        val imageUrl = if (imagePath.startsWith("http")) imagePath else "$SERVER_URL/api/get_image/$imagePath"
        val postUrl = item.optString("post_url", "").trim()
        Log.i(TAG, "imageUrl: $imageUrl, postUrl: $postUrl")

        val blankDrawable = R.color.accent_0.toDrawable()

        if (Helpers.isPdfFile(imagePath)) {
            // Show a generic PDF icon
            holder.imageView.setImageResource(android.R.drawable.ic_popup_disk_full) // Add your own `ic_pdf` icon to res/drawable

            holder.itemView.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val localFile = Helpers.downloadPdfToCache(context, imageUrl)  // ✅ Use raw imageUrl, not GlideUrl.toString()
                        val pdfUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            localFile
                        )

                        val viewIntent = Intent(Intent.ACTION_VIEW)
                        viewIntent.setDataAndType(pdfUri, "application/pdf")
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(viewIntent)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Helpers.showToast(context, "Failed to open PDF")
                            Log.e(TAG, "Failed to open PDF", e)
                        }
                    }
                }
            }
        }
        else {
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
                    intent.putExtra("imagePath", imagePath)
                    intent.putExtra("imageUrl", imageUrl)
                    intent.putExtra("postUrl", postUrl)
                    context.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount(): Int = dataList.size

    private fun loadImageWithRetry(imageView: ImageView, glideUrl: GlideUrl, attempt: Int, maxRetries: Int, baseDelayMillis: Long = 300L) {
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
                    Log.w(TAG, "onLoadFailed for $glideUrl (attempt $attempt)")

                    if (attempt < maxRetries) {
                        val delay = baseDelayMillis * (1 shl attempt)

                        imageView.postDelayed({
                            Log.i(TAG, "Retrying $glideUrl (attempt ${attempt + 1}) after ${delay}ms")
                            loadImageWithRetry(imageView, glideUrl, attempt + 1, maxRetries, baseDelayMillis)
                        }, delay)
                    } else {
                        Log.e(TAG, "Image load failed after $maxRetries attempts: $glideUrl")
                        imageView.setImageDrawable(errorDrawable)
                    }
                }
            })
    }
}