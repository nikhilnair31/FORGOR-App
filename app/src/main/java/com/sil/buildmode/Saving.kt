package com.sil.buildmode

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sil.others.Helpers
import kotlin.math.max

class Saving : AppCompatActivity() {
    // region Vars
    private val TAG = "Saving"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var tierText: TextView
    private lateinit var savesLeftAmountText: TextView
    private lateinit var requestDataExportButton: Button
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
        requestDataExportButton = findViewById(R.id.requestDataExportButton)
        savesLeftAmountText = findViewById(R.id.savesLeftAmount)
        tierText = findViewById(R.id.tierText)

        requestDataExportButton.setOnClickListener {
            showConfirmBulkDownload()
        }

        // Get cached values
        val cachedCurrTier = generalSharedPreferences.getString("cached_curr_tier", "FREE")
        val cachedCurrSaves = generalSharedPreferences.getInt("cached_curr_saves", 0)
        val cachedMaxSaves = generalSharedPreferences.getInt("cached_max_saves", 0)
        // Update the UI
        tierText.text = getString(R.string.currentTier, cachedCurrTier)
        savesLeftAmountText.text = getString(R.string.savesLeftAmount, cachedCurrSaves, cachedMaxSaves)
        // Then update the values from server
        Helpers.getUserTierInfo(this) { tier, currSaves, maxSaves ->
            Log.i(TAG, "You're on the $tier tier and have $currSaves/$maxSaves uploads left today!")
            savesLeftAmountText.text = getString(R.string.savesLeftAmount, currSaves, maxSaves)
            generalSharedPreferences.edit {
                putInt("cached_curr_saves", currSaves)
                putInt("cached_max_saves", maxSaves)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
        }
    }
    // endregion

    // region Data Related
    private fun requestDataExport() {
        requestDataExportButton.isEnabled = false
        Helpers.requestDataExport(this) { success ->
            runOnUiThread {
                requestDataExportButton.isEnabled = true
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
                requestDataExportButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                requestDataExport()
            }
            .show()
    }
    // endregion
}