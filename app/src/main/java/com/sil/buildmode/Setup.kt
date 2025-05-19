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

        generalSharedPreferences = getSharedPreferences("com.sil.buildmode.generalSharedPrefs", MODE_PRIVATE)

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

        if (userNameText.isEmpty() || passwordText.isEmpty()) {
            Helpers.showToast(this, "Username or password cannot be empty.")
            return
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

        if (!areAllPermissionsGranted()) {
            val permList = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            ActivityCompat.requestPermissions(this, permList, initRequestCode)
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        // Check for all regular permissions
        val hasNotificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val hasMediaImagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

        // Check for battery optimization exemption
        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager
        val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(this.packageName)

        val hasAllPermissions = hasNotificationPermission && isBatteryOptimizationIgnored && hasMediaImagesPermission
        Log.i(TAG, "hasAllPermissions: $hasAllPermissions")

        return hasAllPermissions
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == batteryUnrestrictedRequestCode) {
            // Check if permission was granted
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.i("Permissions", "Battery optimization ignored")
                // Update button color
                if (areAllPermissionsGranted()) {
                    highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            initRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permissions", "initRequestCode granted")
                    getBatteryUnrestrictedPermission()
                }
                return
            }
            batteryUnrestrictedRequestCode -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permissions", "batteryUnrestrictedRequestCode granted")
                    if (areAllPermissionsGranted()) {
                        highlightButtonEffects(permissionButton, getString(R.string.gavePermissionsText))
                        launchMainActivity()
                    }
                }
                return
            }
        }
    }
    private fun getBatteryUnrestrictedPermission() {
        Log.i(TAG, "Requesting getBatteryUnrestrictedPermission")

        if (!(this.getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(this.packageName)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivityForResult(intent, batteryUnrestrictedRequestCode)
        }
    }
    // endregion
}