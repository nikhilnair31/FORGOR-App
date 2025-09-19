package com.sil.utils

import android.content.Context

class ScreenshotServiceUtils {
    companion object {
        @Volatile
        private var isRunning: Boolean = false

        internal fun markRunning(running: Boolean) {
            isRunning = running
        }

        fun isServiceRunning(): Boolean {
            return isRunning
        }
    }
}
