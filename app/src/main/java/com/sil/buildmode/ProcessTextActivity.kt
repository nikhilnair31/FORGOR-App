package com.sil.buildmode

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers

class ProcessTextActivity : AppCompatActivity() {
    // region Vars
    private val TAG = "ProcessTextActivity"
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()
        Log.d(TAG, "sharedText: $selectedText")
        if (android.util.Patterns.WEB_URL.matcher(selectedText).matches()) {
            handleSendUrl(selectedText)
        } else {
            handleSendText(selectedText)
        }

        finish()
    }
    private fun handleSendUrl(url: String?) {
        Log.d(TAG, "handleSendUrl | url: $url")
        url?.let {
            Helpers.uploadPostUrlToServer(this, it)
        }
    }
    private fun handleSendText(text: String?) {
        Log.d(TAG, "handleSendText | text: $text")
        text?.let {
            Helpers.uploadPostTextToServer(this, it)
        }
    }
}
