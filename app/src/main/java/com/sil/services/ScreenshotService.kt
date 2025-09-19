package com.sil.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.sil.others.Helpers
import com.sil.others.NotificationHelper
import com.sil.others.ScreenshotFileObserver
import com.sil.utils.ScreenshotServiceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ScreenshotService : Service() {
    // region Vars
    private val TAG = "Screenshot Service"

    private var screenshotObserver: ScreenshotFileObserver? = null

    private lateinit var notificationHelper: NotificationHelper
    private val monitoringNotificationId = 2

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // endregion

    // region Common
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        ScreenshotServiceUtils.markRunning(true)
        initRelated()
        return START_STICKY
    }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy | Screenshot monitor service destroyed")

        ScreenshotServiceUtils.markRunning(false)
        serviceScope.cancel()
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

            screenshotObserver = ScreenshotFileObserver(this, screenshotsPath, serviceScope)
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
    // endregion
}