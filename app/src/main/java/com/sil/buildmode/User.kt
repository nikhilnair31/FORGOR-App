package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

class User : AppCompatActivity() {
    // region Vars
    private val TAG = "Settings"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val KEY_SCREENSHOT_ENABLED = "isScreenshotMonitoringEnabled"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var usernameEditText: EditText
    private lateinit var editUsernameButton: Button
    private lateinit var emailEditText: EditText
    private lateinit var editEmailButton: Button
    private lateinit var userLogoutButton: Button
    private lateinit var accountDeleteButton: Button
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }
    private fun initRelated() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        usernameEditText = findViewById(R.id.usernameEditText)
        editUsernameButton = findViewById(R.id.editUsername)
        emailEditText = findViewById(R.id.emailEditText)
        editEmailButton = findViewById(R.id.editEmail)
        userLogoutButton = findViewById(R.id.userLogoutButton)
        accountDeleteButton = findViewById(R.id.accountDeleteButton)

        val username = generalSharedPreferences.getString("username", "")
        usernameEditText.setText(username)
        usernameEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateButtonState(editUsernameButton, usernameEditText)
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                editUsernameButton.isEnabled = s.toString() != username
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val email = generalSharedPreferences.getString("email", "")
        emailEditText.setText(email)
        emailEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateButtonState(editEmailButton, emailEditText)
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                editEmailButton.isEnabled = s.toString() != username
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editUsernameButton.setOnClickListener {
            editUsernameRelated()
        }
        editEmailButton.setOnClickListener {
            editEmailRelated()
        }
        userLogoutButton.setOnClickListener {
            userLogoutRelated()
        }
        accountDeleteButton.setOnClickListener {
            showConfirmAccountDelete()
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

    private fun updateButtonState(button: Button, editText: EditText) {
        val isValid = editText.text.isNullOrBlank().not()

        button.isEnabled = isValid
        val tintColor = if (isValid)
            ContextCompat.getColor(this, R.color.accent_1)     // enabled color
        else
            ContextCompat.getColor(this, android.R.color.darker_gray) // disabled color

        button.backgroundTintList = ColorStateList.valueOf(tintColor)
    }

    private fun showConfirmAccountDelete() {
        MaterialAlertDialogBuilder (this)
            .setIcon(R.drawable.outline_exclamation_24)
            .setTitle("Delete account?")
            .setMessage("This will permanently delete your account and all your data.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Proceed") { _, _ ->
                // optional: small haptic
                accountDeleteButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                accountDeleteRelated()
            }
            .show()
    }
    // endregion

    // region Auth Related
    private fun editUsernameRelated() {
        Log.i(TAG, "editUsernameRelated")

        val newUsername = usernameEditText.text.toString()
        Helpers.editUserUsernameToServer(this, newUsername) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Edit username success")
                    this.showToast("Edit username successful!")
                    editUsernameButton.isEnabled = false
                    generalSharedPreferences.edit {
                        putString("username", newUsername)
                    }
                } else {
                    Log.i(TAG, "Edit username failed!")
                    this.showToast("Edit username failed!")
                }
            }
        }
    }
    private fun editEmailRelated() {
        Log.i(TAG, "editEmailRelated")

        val newEmail = emailEditText.text.toString()
        Helpers.editUserEmailToServer(this, newEmail) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Edit email success")
                    this.showToast("Edit email successful!")
                    editEmailButton.isEnabled = false
                    generalSharedPreferences.edit {
                        putString("email", newEmail)
                    }
                } else {
                    Log.i(TAG, "Edit email failed!")
                    this.showToast("Edit email failed!")
                }
            }
        }
    }
    private fun userLogoutRelated() {
        Log.i(TAG, "userLogoutRelated")

        generalSharedPreferences.edit(commit = true) {
            remove("username")
            .remove("access_token")
            .remove("refresh_token")
            .remove("last_query")
            .remove("last_results_json")
            .remove("cached_saves_left")
            .putBoolean(KEY_SCREENSHOT_ENABLED, false)
        }  // ← block until written

        val serviceIntent = Intent(this@User, ScreenshotService::class.java)
        stopService(serviceIntent)

        val intent = Intent(this, Welcome::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    private fun accountDeleteRelated() {
        Log.i(TAG, "accountDeleteRelated")

        Helpers.authAccountDelete(this) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Account delete success!")
                    this.showToast("Account deleted!")
                } else {
                    Log.i(TAG, "Account delete failed!")
                    this.showToast("Account delete failed!")
                }
            }
        }

        generalSharedPreferences.edit(commit = true) { clear() }

        val serviceIntent = Intent(this@User, ScreenshotService::class.java)
        stopService(serviceIntent)

        val intent = Intent(this, Welcome::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    // endregion
}