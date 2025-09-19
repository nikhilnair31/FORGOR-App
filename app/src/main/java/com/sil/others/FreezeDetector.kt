// app/src/main/java/com/sil/others/FreezeDetector.kt
package com.sil.others

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sil.utils.ThreadUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreezeDetector(private val context: android.content.Context) {

    private val TAG = "FreezeDetector"
    private val uiHandler = Handler(Looper.getMainLooper())
    private val checkIntervalMs = 5000L // Check every 5 seconds
    private val freezeThresholdMs = 3000L // If UI thread doesn't respond in 3 seconds, consider it frozen

    private val logFile: File by lazy {
        File(context.filesDir, "freeze_log.txt")
    }

    private val logRunnable: Runnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "UI thread responsiveness check...")
            val startTime = System.currentTimeMillis()

            uiHandler.post {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                if (responseTime > freezeThresholdMs) {
                    Log.e(TAG, "UI thread was unresponsive for ${responseTime}ms! Capturing stack trace.")
                    logFreezeEvent("UI thread unresponsive for ${responseTime}ms")
                }
            }
            uiHandler.postDelayed(this, checkIntervalMs)
        }
    }

    private fun logFreezeEvent(reason: String) {
        try {
            val writer = FileWriter(logFile, true)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            writer.append("=== FREEZE EVENT: $timestamp ===\n")
            writer.append("Reason: $reason\n")
            writer.append(ThreadUtils.getAllThreadStackTraces())
            writer.append("\n\n")
            writer.flush()
            writer.close()
            Log.i(TAG, "Freeze event logged to ${logFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write freeze log", e)
        }
    }

    fun start() {
        Log.i(TAG, "Starting FreezeDetector with checkInterval=${checkIntervalMs}ms, threshold=${freezeThresholdMs}ms")
        uiHandler.post(logRunnable)
    }

    fun stop() {
        Log.i(TAG, "Stopping FreezeDetector")
        uiHandler.removeCallbacks(logRunnable)
    }

    /**
     * Call this when your app is explicitly being put into the background
     * to avoid unnecessary logging or resource use when it's not active.
     */
    fun onBackground() {
        // You might want to stop the detector or increase its interval significantly
        // if you find it's too aggressive in the background.
        // For now, let's keep it running to catch resume freezes.
    }

    /**
     * Call this when your app is brought to the foreground.
     */
    fun onForeground() {
        // Ensure it's running when foregrounded
        start()
    }
}