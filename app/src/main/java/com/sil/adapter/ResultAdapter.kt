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
        // Log.i(TAG, "item: $item")

        val timestampText = item.optString("timestamp_str", "")
        val postUrl = item.optString("post_url", "").trim()
        val tagsText = item.optString("tags_text", "")
        val fileName = item.optString("file_name", "")
        val fileUrl = if (fileName.startsWith("http")) fileName else "$SERVER_URL/api/get_file/$fileName"
        // Log.i(TAG, "fileUrl: $fileUrl")

        // Handle link icon
        holder.linkIcon.visibility = if (postUrl.isBlank() || postUrl == "-") View.GONE else View.VISIBLE
        holder.linkIcon.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, postUrl.toUri())
            context.startActivity(intent)
        }

        // Handle PDFs
        if (Helpers.isPdfFile(fileName)) {
            // Show a generic PDF icon
            holder.imageView.setImageResource(android.R.drawable.ic_popup_disk_full) // Add your own `ic_pdf` icon to res/drawable

            holder.itemView.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val localFile = Helpers.downloadPdfToCache(context, fileUrl)  // ✅ Use raw imageUrl, not GlideUrl.toString()
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

        // Handle text files
        else if (Helpers.isTextFile(fileName)) {
            holder.textText.visibility = View.VISIBLE
            holder.textText.text = "${R.string.loadingContent}"

            // Default click opens placeholder intent so it’s not unresponsive
            holder.itemView.setOnClickListener {
                Helpers.showToast(context, "Loading text... please wait.")
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localFile = Helpers.getTxtToCache(context, fileUrl)
                        ?: throw IOException("File download returned null")

                    val text = localFile.readText().trim()

                    withContext(Dispatchers.Main) {
                        holder.textText.text = text
                        holder.itemView.setOnClickListener {
                            openFileIntent(context, fileName, fileUrl, postUrl, textContent = text)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load TXT", e)
                    withContext(Dispatchers.Main) {
                        holder.textText.text = "${R.string.failedLoadingContent}"
                        holder.itemView.setOnClickListener {
                            Helpers.showToast(context, "Failed to open text file.")
                        }
                    }
                }
            }
        }

        // Handle image files
        else {
            val blankDrawable = R.color.accent_0.toDrawable()
            val glideUrl = Helpers.getImageURL(context, fileUrl)

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
                openFileIntent(context, fileName, fileUrl, postUrl, textContent = "")
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

    private fun openFileIntent(context: Context, fileName: String, fileUrl: String, postUrl: String, textContent: String? = null) {
        val intent = Intent(context, FullContent::class.java).apply {
            putExtra("fileName", fileName)
            putExtra("fileUrl", fileUrl)
            putExtra("postUrl", postUrl)
            textContent?.let { putExtra("textContent", it) }
        }
        context.startActivity(intent)
    }
}