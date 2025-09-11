package com.sil.services

import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class ScreenshotService : Service() {
    // region Vars
    private val TAG = "Screenshot Service"

    private var screenshotObserver: ScreenshotFileObserver? = null

    private lateinit var notificationHelper: NotificationHelper
    private val monitoringNotificationId = 2
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        initRelated()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy | Screenshot monitor service destroyed")

        stopMonitoring()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun initRelated() {
        Log.i(TAG, "initRelated")

        // Service related
        notificationHelper = NotificationHelper(this)
        startForeground(monitoringNotificationId, notificationHelper.createMonitoringNotification())

        // Observer related
        if (screenshotObserver == null) {
            startMonitoring()
        }
    }
    // endregion

    // region Monitoring Related
    private fun startMonitoring() {
        Log.i(TAG, "startMonitoring")

        val screenshotsPath = Helpers.getScreenshotsPath()
        Log.i(TAG, "screenshotsPath: $screenshotsPath")

        if (screenshotsPath != null) {
            Log.i(TAG, "Starting to monitor screenshots folder: $screenshotsPath")

            screenshotObserver = ScreenshotFileObserver(screenshotsPath)
            screenshotObserver?.startWatching()
        } else {
            Log.e(TAG, "Could not determine screenshots directory path")
        }
    }
    private fun stopMonitoring() {
        Log.i(TAG, "Stopped monitoring screenshots folder")

        screenshotObserver?.stopWatching()
        screenshotObserver = null
    }

    private inner class ScreenshotFileObserver(path: String) : FileObserver(path, MOVED_TO) {
        override fun onEvent(event: Int, path: String?) {
            if (event == MOVED_TO && path != null) {
                Log.i(TAG, "Screenshot detected: $path")

                // Delay to ensure MediaStore has indexed it
                CoroutineScope(Dispatchers.IO).launch {
                    delay(1000)

                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED
                    )

                    val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
                    val selectionArgs = arrayOf(path)

                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                    contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            val id = cursor.getLong(idCol)
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                            Log.i(TAG, "Uploading screenshot via URI: $uri")

                            val tempFile = Helpers.copyUriToTempFile(this@ScreenshotService, uri)
                            if (tempFile != null) {
                                Helpers.uploadImageFileToServer(this@ScreenshotService, tempFile)
                            } else {
                                Log.e(TAG, "Failed to copy screenshot to temp file")
                            }
                        } else {
                            Log.e(TAG, "No MediaStore match for $path")
                        }
                    }
                }
            }
        }
    }
    // endregion
}