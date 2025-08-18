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
        if (accessToken.isNotEmpty()) {
            startActivity(Intent(this, Main::class.java))
            finish()
        }
    }
    private fun uiInitRelated() {
        signInButton = findViewById(R.id.signInButton)
        signUpButton = findViewById(R.id.signUpButton)

        signInButton.setOnClickListener {
            startActivity(Intent(this, SignIn::class.java))
        }
        signUpButton.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }
    }
    // endregion
}