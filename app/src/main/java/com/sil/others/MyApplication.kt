package com.sil.others

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set default crash handler
        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { thread: Thread?, throwable: Throwable? ->
            logCrashToFile(throwable)
        })
    }

    private fun logCrashToFile(throwable: Throwable?) {
        try {
            val logFile = File(filesDir, "crash_log.txt")
            val writer = FileWriter(logFile, true)
            writer.write("=== CRASH at: " + Date().toString() + " ===\n")
            writer.write(Log.getStackTraceString(throwable))
            writer.write("\n\n")
            writer.close()
        } catch (e: IOException) {
            Log.e("CrashLogger", "Failed to write crash log", e)
        }
    }
}