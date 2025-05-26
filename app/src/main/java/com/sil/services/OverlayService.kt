package com.sil.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.sil.buildmode.MediaProjectionRequestActivity
import com.sil.buildmode.R
import com.sil.buildmode.Share
import com.sil.others.NotificationHelper

class OverlayService : Service() {
    // region Vars
    private val TAG = "Overlay Service"

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

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
        Log.i(TAG, "onDestroy | Overlay service destroyed")

        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
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
        startOverlay()
    }
    // endregion

    private fun startOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_button, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 300

        val overlayIcon = overlayView.findViewById<ImageView>(R.id.overlay_icon)

        // Drag logic
        @Suppress("ClickableViewAccessibility")
        overlayIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v?.performClick()
                        return true
                    }
                }
                return false
            }
        })

        // Tap logic
        overlayIcon.setOnClickListener {
            val prefs = getSharedPreferences("screenshot_prefs", MODE_PRIVATE)
            val resultCode = prefs.getInt("result_code", -1)
            val dataUriString = prefs.getString("data_intent_uri", null)

            if (resultCode != -1 && dataUriString != null) {
                val dataIntent = Intent.parseUri(dataUriString, 0)
                MediaProjectionRequestActivity.startWithExistingPermission(this, resultCode, dataIntent) { screenshotFile ->
                    screenshotFile?.let { file ->
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(this, Share::class.java).apply {
                            action = Intent.ACTION_SEND
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                }
            } else {
                // First-time permission request
                MediaProjectionRequestActivity.start(this) { screenshotFile ->
                    screenshotFile?.let { file ->
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(this, Share::class.java).apply {
                            action = Intent.ACTION_SEND
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, layoutParams)
    }
}
