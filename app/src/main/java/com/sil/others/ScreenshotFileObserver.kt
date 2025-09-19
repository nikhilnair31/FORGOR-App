package com.sil.others

import android.app.Service
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenshotFileObserver(
    private val service: Service,
    path: String,
    private val coroutineScope: CoroutineScope
) : FileObserver(path, MOVED_TO) {

    private val TAG = "ScreenshotFileObserver"

    override fun onEvent(event: Int, path: String?) {
        if (event == MOVED_TO && path != null) {
            Log.i(TAG, "Screenshot detected: $path")

            coroutineScope.launch {
                try {
                    delay(500)

                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED
                    )

                    val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
                    val selectionArgs = arrayOf(path)

                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                    service.applicationContext.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idCol =
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            val id = cursor.getLong(idCol)
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                            Log.i(TAG, "Uploading screenshot via URI: $uri")

                            val tempFile = Helpers.copyUriToTempFile(service, uri)
                            if (tempFile != null) {
                                Helpers.uploadImageFileToServer(service, tempFile)
                            } else {
                                Log.e(TAG, "Failed to copy screenshot to temp file")
                            }
                        } else {
                            Log.e(TAG, "No MediaStore match for $path")
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.i(TAG, "Screenshot processing coroutine cancelled gracefully.")
                    } else {
                        Log.e(TAG, "Error processing screenshot: ${e.localizedMessage}", e)
                    }
                }
            }
        }
    }
}
