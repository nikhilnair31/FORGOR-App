package com.sil.receiver

import StartScreenshotWorker
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sil.services.ScreenshotService

class BootCompletedReceiver : android.content.BroadcastReceiver() {
    val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("com.sil.buildmode.generalSharedPrefs", Context.MODE_PRIVATE)

            // Check if screenshot services were active
            if (prefs.getBoolean("isScreenshotMonitoringEnabled", false)) {
                val workRequest = OneTimeWorkRequestBuilder<StartScreenshotWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}