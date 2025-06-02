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
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions
import com.sil.adapter.ResultDiffCallback
import com.sil.others.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val linkIcon: ImageView = view.findViewById(R.id.linkIcon)
        val textText: TextView = view.findViewById(R.id.textText)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_card, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = dataList[position]

        val fileName = item.optString("file_name", "")
        val thumbnailName = item.optString("thumbnail_name", "")
        val postUrl = item.optString("post_url", "")
        val thumbnailUrl = "$SERVER_URL/api/get_thumbnail/$thumbnailName"

        // Handle link icon
        holder.linkIcon.visibility = if (postUrl.isBlank() || postUrl == "-") View.GONE else View.VISIBLE
        holder.linkIcon.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
            context.startActivity(intent)
        }

        // Handle image files
        val blankDrawable = R.color.accent_0.toDrawable()
        val glideUrl = Helpers.getImageURL(context, thumbnailUrl)

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
        if (postUrl.isNotBlank() && postUrl != "-") {
            holder.linkIcon.visibility = View.VISIBLE
            holder.linkIcon.setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_VIEW, postUrl.toUri()))
            }
        } else {
            holder.linkIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            Log.i(TAG, "Item clicked: $item")

            val intent = Intent(context, FullContent::class.java).apply {
                putExtra("fileName", fileName)
                putExtra("postUrl", postUrl)
            }

            if (context is Main) {
                context.startActivityForResult(intent, 101)
            } else {
                context.startActivity(intent) // fallback, just in case
            }
        }
    }

    override fun getItemCount(): Int = dataList.size
}