package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sil.others.Helpers
import com.sil.others.Helpers.Companion.showToast
import com.sil.services.ScreenshotService

class Settings : AppCompatActivity() {
    // region Vars
    private val TAG = "Settings"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var usernameText: EditText

    private lateinit var editUsernameButton: Button
    private lateinit var screenshotToggleButton: ToggleButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        usernameText = findViewById(R.id.usernameEditText)
        editUsernameButton = findViewById(R.id.editUsername)
        screenshotToggleButton = findViewById(R.id.screenshotToggleButton)

        usernameText.text = Editable.Factory.getInstance().newEditable(generalSharedPreferences.getString("userName", ""))

        // Check if the service is running and update the toggle button accordingly
        val isScreenshotServiceRunning = Helpers.isServiceRunning(this, ScreenshotService::class.java)
        updateToggle(screenshotToggleButton, isScreenshotServiceRunning)

        screenshotToggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.i(TAG, "Screenshot toggle changed: isChecked=$isChecked")

            val serviceIntent = Intent(this@Settings, ScreenshotService::class.java)
            if (isChecked) {
                Log.i(TAG, "ScreenshotService created")
                startForegroundService(serviceIntent)
            } else {
                Log.i(TAG, "ScreenshotService stopped")
                stopService(serviceIntent)
            }

            generalSharedPreferences.edit { putBoolean(KEY_SCREENSHOT_ENABLED, isChecked) }

            updateToggle(screenshotToggleButton, isChecked)
            screenshotToggleButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        editUsernameButton.setOnClickListener {
            editUsernameRelated()
        }
    }
    // endregion

    // region Auth Related
    private fun editUsernameRelated() {
        Log.i(TAG, "editUsernameRelated")

        val newUsername = usernameText.text.toString()
        Helpers.authEditUsernameToServer(this, newUsername) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Edit username success")
                    showToast(this, "Edit username successful!")
                    generalSharedPreferences.edit {
                        putString("userName", newUsername)
                    }
                } else {
                    Log.i(TAG, "Edit username failed!")
                    showToast(this, "Edit username failed!")
                }
            }
        }
    }
    // endregion

    // region UI Related
    private fun updateToggle(toggle: ToggleButton, isChecked: Boolean) {
        toggle.isChecked = isChecked
        toggle.background = ContextCompat.getDrawable(this, if (isChecked) R.color.accent_0 else R.color.base_0)
        toggle.text = getString(if (isChecked) R.string.screenshotToggleOnText else R.string.screenshotToggleOffText)
    }
    // endregion
}