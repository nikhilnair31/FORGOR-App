package com.sil.buildmode

import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.json.JSONObject

class ResultAdapter(private val context: Context, private val dataList: List<JSONObject>) :
    RecyclerView.Adapter<ResultAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val imageText: TextView = view.findViewById(R.id.imageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_card, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val item = dataList[position]
        val imageUrl = BuildConfig.SERVER_URL + item.getString("image_presigned_url")
        val text = item.getString("image_text")

        Glide.with(context).load(imageUrl).into(holder.imageView)
        holder.imageText.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    override fun getItemCount(): Int = dataList.size
}