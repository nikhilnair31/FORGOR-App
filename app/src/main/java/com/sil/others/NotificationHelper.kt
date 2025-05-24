package com.sil.others

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sil.buildmode.Main
import com.sil.buildmode.R

class NotificationHelper(private val context: Context) {
    // region Vars
    private val TAG = "NotificationHelper"
    
    private val appChannelName = "${R.string.appName} Active Channel"
    private val appNotificationIcon = R.drawable.ic_stat_name

    // Constants for monitoring notification
    private val monitoringChannelId = "BuildmodeMonitoringChannel"
    private val monitoringChannelImportance = NotificationManager.IMPORTANCE_MIN
    private val monitoringNotificationTitle = "FORGOR monitoring..."
    // endregion

    // region Notification Related
    fun createMonitoringNotification(): Notification {
        Log.i(TAG, "Creating monitoring notification channel")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(monitoringChannelId, appChannelName, monitoringChannelImportance)
        notificationManager.createNotificationChannel(channel)

        // Intent to open the main activity
        val intent = Intent(context, Main::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, monitoringChannelId)
            .setContentTitle(monitoringNotificationTitle)
            .setSmallIcon(appNotificationIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(null)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    // endregion
}