package com.sil.buildmode

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService
import kotlin.math.max

class Saving : AppCompatActivity() {
    // region Vars
    private val TAG = "Saving"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var savesLeftText: TextView
    private lateinit var bulkDownloadButton: Button
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saving)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }
    private fun initRelated() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        bulkDownloadButton = findViewById(R.id.bulkDownloadButton)
        savesLeftText = findViewById(R.id.savesLeftText)

        bulkDownloadButton.setOnClickListener {
            showConfirmBulkDownload()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
        }

        val cachedSavesLeft = generalSharedPreferences.getInt("cached_saves_left", -1)
        if (cachedSavesLeft != -1) {
            savesLeftText.text = getString(R.string.savesLeftText, cachedSavesLeft)
        }
        Helpers.getSavesLeft(this) { savesLeft ->
            Log.i(TAG, "You have $savesLeft uploads left today!")
            savesLeftText.text = getString(R.string.savesLeftText, savesLeft)
            generalSharedPreferences.edit {
                putInt("cached_saves_left", savesLeft)
            }
        }
    }
    // endregion

    // region Data Related
    private fun bulkDownloadAllData() {
        bulkDownloadButton.isEnabled = false
        Helpers.bulkDownloadAll(this) { success ->
            runOnUiThread {
                bulkDownloadButton.isEnabled = true
                if (success) showToast(this, "Backup emailed!") else showToast(this, "Download failed")
            }
        }
    }
    // endregion

    // region UI Related
    private fun showConfirmBulkDownload() {
        MaterialAlertDialogBuilder (this)
            .setIcon(R.drawable.outline_attach_email_24)
            .setTitle("Send backup?")
            .setMessage("This will bundle your saved items and email the backup to your account address.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                // optional: small haptic
                bulkDownloadButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                bulkDownloadAllData()
            }
            .show()
    }
    // endregion
}