package com.sil.buildmode

import android.util.Log
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class Welcome : AppCompatActivity() {
    // region Vars
    private val TAG = "Welcome"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPrefs: SharedPreferences

    private lateinit var signInButton: Button
    private lateinit var signUpButton: Button
    private lateinit var googleSignInButton: Button
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        generalSharedPrefs = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        generalInitRelated()
        uiInitRelated()
    }

    private fun generalInitRelated() {
        val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
        Log.i(TAG, "access_token: $accessToken")
        if (!accessToken.isEmpty()) {
            launchNextActivity(Main::class.java)
        }
    }

    private fun launchNextActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        finish()
    }
    // endregion

    // region UI Related
    private fun uiInitRelated() {
        signInButton = findViewById(R.id.signInButton)
        signUpButton = findViewById(R.id.signUpButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)

        signInButton.setOnClickListener {
            launchNextActivity(SignIn::class.java)
        }
        signUpButton.setOnClickListener {
            launchNextActivity(SignUp::class.java)
        }
        googleSignInButton.setOnClickListener {
            // launchNextActivity(Setup::class.java)
        }
    }
    // endregion
}