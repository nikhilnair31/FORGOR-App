package com.sil.buildmode

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions
import com.sil.adapter.ResultDiffCallback
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class ResultAdapter(private val context: Context, private val dataList: MutableList<JSONObject>) :
RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {
    // region Vars
    private val TAG = "ResultAdapter"

    private val SERVER_URL = BuildConfig.SERVER_URL
    // endregion

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    fun getData(): MutableList<JSONObject> {
        return dataList
    }
    fun updateData(newData: List<JSONObject>) {
        val diffCallback = ResultDiffCallback(dataList, newData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        dataList.clear()
        dataList.addAll(newData)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val item = dataList[position]
        val key = item.optString("file_name", item.optString("thumbnail_name", ""))
        return key.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_card, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = dataList[position]

        val fileName = item.optString("file_name", "")
        val thumbnailName = item.optString("thumbnail_name", "")
        val tags = item.optString("tags", "")

        val thumbnailUrl = "${SERVER_URL}/api/get_thumbnail/$thumbnailName"

        // Handle image files
        val blankDrawable = R.color.accent_0.toDrawable()
        val glideUrl = Helpers.getImageURL(context, thumbnailUrl)

        if (glideUrl == null) {
            holder.imageView.setImageDrawable(blankDrawable)
            return
        }

        loadThumbnailWithRetry(glideUrl, holder)

        holder.itemView.setOnClickListener {
            Log.i(TAG, "Item clicked: $item")

            val intent = Intent(context, FullContent::class.java).apply {
                putExtra("fileName", fileName)
            }

            if (context is Main) {
                context.startActivityForResult(intent, 101)
            } else {
                context.startActivity(intent) // fallback, just in case
            }
        }
    }

    override fun getItemCount(): Int = dataList.size

    private fun loadThumbnailWithRetry(glideUrl: GlideUrl, holder: ResultViewHolder, retries: Int = 2) {
        Glide.with(context)
            .load(glideUrl)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.color.accent_0.toDrawable())
                    .error(R.color.accent_0.toDrawable())
                    .fitCenter()
            )
            .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "Thumbnail load failed: ${e?.localizedMessage}")
                    if (retries > 0) {
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(300) // slight delay before retry
                            loadThumbnailWithRetry(glideUrl, holder, retries - 1)
                        }
                    }
                    return false // allow error placeholder to be shown
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?, target: com.bumptech.glide.request.target.Target<Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource?, isFirstResource: Boolean
                ): Boolean {
                    return false // allow normal behavior
                }
            })
            .into(holder.imageView)
    }
}