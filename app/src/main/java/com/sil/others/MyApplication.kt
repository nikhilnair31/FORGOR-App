// app/src/main/java/com/sil/others/MyApplication.kt
package com.sil.others

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import android.app.Activity
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.system.exitProcess

class MyApplication : Application() {
    private val TAG = "MyApplication"
    private lateinit var freezeDetector: FreezeDetector

    override fun onCreate() {
        super.onCreate()

        // Initialize FreezeDetector
        freezeDetector = FreezeDetector(this)

        // Set default crash handler (your existing one)
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, throwable: Throwable? ->
            logCrashToFile(throwable)
            // It's crucial to still let the system handle the crash after logging,
            // otherwise, the app will just hang after logging.
            // If the default handler is null, then a simple exit(1) might be needed.
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler != null && defaultHandler != this) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }

        // Register Activity lifecycle callbacks to manage FreezeDetector
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                Log.d(TAG, "Activity Started: ${activity.javaClass.simpleName}")
                // Start monitoring when an activity comes to foreground
                if (activity is com.sil.buildmode.Main || activity is com.sil.buildmode.Welcome) {
                    freezeDetector.onForeground()
                }
            }
            override fun onActivityResumed(activity: Activity) {
                Log.d(TAG, "Activity Resumed: ${activity.javaClass.simpleName}")
                // You might also want to ensure the detector is running here if not started in onActivityStarted
                // freezeDetector.onForeground()
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                Log.d(TAG, "Activity Stopped: ${activity.javaClass.simpleName}")
                // Consider stopping when all activities are in background, but for resume freezes,
                // you might want it to keep running, or restart aggressively on resume.
                // For now, let's keep it simpler for "freeze on opening"
            }
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    private fun logCrashToFile(throwable: Throwable?) {
        try {
            val logFile = File(filesDir, "crash_log.txt")
            val writer = FileWriter(logFile, true)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            writer.write("=== CRASH at: $timestamp ===\n")
            writer.write(Log.getStackTraceString(throwable))
            writer.write("\n\n")
            writer.close()
            Log.e(TAG, "Crash logged to ${logFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("CrashLogger", "Failed to write crash log (inner exception)", e)
        }
    }
}