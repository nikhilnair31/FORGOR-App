package com.sil.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        private val observedPath = path

        override fun onEvent(event: Int, path: String?) {
            Log.i(TAG, "onEvent | event: $event | path: $path")

            if (event == MOVED_TO && path != null) {
                val file = File(observedPath, path)
                if (Helpers.isImageFile(file.name)) {
                    Log.i(TAG, "New screenshot detected at ${file.absolutePath} with name: ${file.name}")

                    Helpers.uploadImageFile(this@ScreenshotService, file)
                }
            }
        }
    }
    // endregion

    companion object {
        private val TAG = "Screenshot Service"
    }
}