package com.sil.buildmode

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.view.Surface
import android.media.Image

class MediaProjectionRequestActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private var callback: ((File?) -> Unit)? = null

        fun start(context: Context, cb: (File?) -> Unit) {
            callback = cb
            val intent = Intent(context, MediaProjectionRequestActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun startWithExistingPermission(context: Context, resultCode: Int, dataIntent: Intent, cb: (File?) -> Unit) {
            callback = cb
            val intent = Intent(context, MediaProjectionRequestActivity::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("data_intent", dataIntent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val resultCode = intent.getIntExtra("result_code", -1)
        val dataIntent = intent.getParcelableExtra<Intent>("data_intent")

        if (resultCode != -1 && dataIntent != null) {
            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent)
            captureScreenshot(mediaProjection)
        } else {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            val prefs = getSharedPreferences("screenshot_prefs", MODE_PRIVATE)
            prefs.edit().apply {
                putInt("result_code", resultCode)
                putString("data_intent_uri", data.toUri(0))
                apply()
            }

            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            captureScreenshot(mediaProjection)
        } else {
            callback?.invoke(null)
            finish()
        }
    }

    private fun captureScreenshot(mediaProjection: MediaProjection) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, ImageFormat.FLEX_RGBA_8888, 1)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )

        imageReader.setOnImageAvailableListener({
            val image: Image = imageReader.acquireLatestImage()
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)

            val file = File.createTempFile("screenshot_", ".png", cacheDir)
            FileOutputStream(file).use {
                cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.flush()
            }

            mediaProjection.stop()
            virtualDisplay.release()
            imageReader.close()

            callback?.invoke(file)
            finish()
        }, null)
    }
}