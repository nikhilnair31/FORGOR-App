package com.sil.buildmode

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Settings : AppCompatActivity() {
    // region Vars
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var usernameText: TextView
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        usernameText = findViewById(R.id.usernameText)

        textSetup()
    }
    // endregion

    // region UI Related
    private fun textSetup() {
        usernameText.text = generalSharedPreferences.getString("userName", "")
    }
    // endregion
}