package com.sil.adapter

import androidx.recyclerview.widget.DiffUtil
import org.json.JSONObject

class ResultDiffCallback(
    private val oldList: List<JSONObject>,
    private val newList: List<JSONObject>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].optString("file_name") ==
                newList[newItemPosition].optString("file_name")
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].toString() == newList[newItemPosition].toString()
    }
}
