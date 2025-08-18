package com.sil.buildmode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sil.others.Helpers
import java.io.File


class Share : AppCompatActivity() {
    // region Vars
    private val TAG = "Share"
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }
    // endregion

    // region Share Related
    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        Log.d(TAG, "Intent action: $action")

        when {
            type?.startsWith("image/") == true -> {
                when (action) {
                    Intent.ACTION_SEND -> {
                        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        handleSendImage(imageUri)
                    }
                    Intent.ACTION_SEND_MULTIPLE -> {
                        val imageUriList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        handleSendMultipleImages(imageUriList)
                    }
                }
            }
        }

        finish()
    }

    private fun handleSendImage(imageUri: Uri?) {
        Log.d(TAG, "handleSendImage | imageUri: $imageUri")

        imageUri?.let {
            var realPath = Helpers.getRealPathFromUri(this, it)
            if (realPath == null) {
                val tempFile = Helpers.copyUriToTempFile(this, it)
                realPath = tempFile?.absolutePath
            }

            realPath?.let { path ->
                val file = File(path)
                Helpers.uploadImageFileToServer(this, file)
            }
        }
    }
    private fun handleSendMultipleImages(imageUris: ArrayList<Uri>?) {
        Log.d(TAG, "handleSendMultipleImages | imageUris.size: ${imageUris?.size}")

        imageUris?.forEach { uri ->
            var realPath = Helpers.getRealPathFromUri(this, uri)
            if (realPath == null) {
                val tempFile = Helpers.copyUriToTempFile(this, uri)
                realPath = tempFile?.absolutePath
            }
            Log.d(TAG, "handleSendMultipleImages | realPath: $realPath")

            realPath?.let { path ->
                val file = File(path)
                Helpers.uploadImageFileToServer(this, file)
            }
        }
    }
    // endregion
}