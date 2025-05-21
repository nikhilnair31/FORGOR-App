package com.sil.buildmode

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sil.others.Helpers

class Setup : AppCompatActivity() {
    // region Vars
    private val TAG = "Setup"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private val initRequestCode = 100
    private val batteryUnrestrictedRequestCode = 103

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var permissionButton: Button
    private lateinit var updateAndNextButton: ImageButton
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        initRelated()
    }

    private fun initRelated() {
        Log.i(TAG, "initRelated")

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        // Setup UI related
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.buttonLogin)
        permissionButton = findViewById(R.id.buttonPermission)
        updateAndNextButton = findViewById(R.id.buttonUpdateAndNext)

        // Setup button related
        loginButton.setOnClickListener {
            loginRelated()
        }
        permissionButton.setOnClickListener {
            permissionRelated()
        }
        updateAndNextButton.setOnClickListener {
            goToMain()
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val username = usernameEditText.text.toString()
                val password = passwordEditText.text.toString()
                val minPasswordLength = resources.getInteger(R.integer.minPasswordLength)

                loginButton.isEnabled = username.isNotEmpty() && password.length >= minPasswordLength
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        // Attach the watcher to both fields
        usernameEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)

        // Check for permissions
        if (areAllPermissionsGranted()) {
            highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
        }
    }

    private fun goToMain() {
        val userNameText = usernameEditText.text.toString()

        if (userNameText.isNotEmpty()) {
            Log.i(TAG, "goToMain\nuserName: $userNameText")

            generalSharedPreferences.edit { putString("userName", userNameText) }

            launchMainActivity()
        }
        else {
            Helpers.showToast(this, "Invalid username")
        }
    }
    private fun launchMainActivity() {
        val intent = Intent(this, Main::class.java)
        startActivity(intent)
        finish()
    }
    // endregion

    // region UI Related
    private fun highlightButtonEffects(button: Button, newText: String) {
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_0))
        button.setTextColor(ContextCompat.getColor(this, R.color.accent_1))
        button.text = newText
    }
    // endregion

    // region Auth Related
    private fun loginRelated() {
        Log.i(TAG, "Logging in user...")

        val userNameText = usernameEditText.text.toString()
        val passwordText = passwordEditText.text.toString()
        val minPasswordLength = resources.getInteger(R.integer.minPasswordLength)

        if (userNameText.isEmpty() || passwordText.isEmpty()) {
            Helpers.showToast(this, "Username or password cannot be empty.")
            return
        }
        if (passwordText.length < minPasswordLength) {
            passwordEditText.error = "Password must be at least $minPasswordLength characters"
        } else {
            // Proceed with valid password
        }

        Helpers.authLoginToServer(this, userNameText, passwordText) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Login success")
                    highlightButtonEffects(loginButton, getString(R.string.setupLoginSuccessText))
                    generalSharedPreferences.edit {
                        putString("userName", userNameText)
                    }
                } else {
                    Log.i(TAG, "Login failed — prompting registration")
                    showRegistrationPrompt(userNameText, passwordText)
                }
            }
        }
    }
    private fun showRegistrationPrompt(username: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Account not found")
            .setMessage("This account doesn't exist. Do you want to register and sign in?")
            .setPositiveButton("Register") { _, _ ->
                Helpers.authRegisterToServer(this, username, password) { registerSuccess ->
                    runOnUiThread {
                        if (registerSuccess) {
                            Helpers.showToast(this, "Registered and signed in.")
                            highlightButtonEffects(loginButton, getString(R.string.setupLoginSuccessText))
                            generalSharedPreferences.edit {
                                putString("userName", username)
                            }
                        } else {
                            Helpers.showToast(this, "Registration failed.")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    // endregion

    // region Permissions Related
    private fun permissionRelated() {
        Log.i(TAG, "Requesting initial permissions")

        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        if (!hasRuntimePermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, initRequestCode)
        } else if (!isBatteryOptimized()) {
            requestIgnoreBatteryOptimizations()
        } else {
            highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
        }
    }

    private fun hasRuntimePermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startActivityForResult(intent, batteryUnrestrictedRequestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == initRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (!isBatteryOptimized()) {
                    requestIgnoreBatteryOptimizations()
                } else {
                    highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == batteryUnrestrictedRequestCode && isBatteryOptimized()) {
            highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        return hasRuntimePermissions(arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )) && isBatteryOptimized()
    }
    // endregion
}