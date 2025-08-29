package com.sil.buildmode

import android.util.Log
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

class Welcome : AppCompatActivity() {
    // region Vars
    private val TAG = "Welcome"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPrefs: SharedPreferences

    private lateinit var signInButton: Button
    private lateinit var signUpButton: Button
    private lateinit var rootConstraintLayout: ConstraintLayout
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
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        signInButton = findViewById(R.id.signInButton)
        signUpButton = findViewById(R.id.signUpButton)

        signInButton.setOnClickListener {
            startActivity(Intent(this, SignIn::class.java))
        }
        signUpButton.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            rootConstraintLayout.updatePadding(top = sys.top)
            v.updatePadding(bottom = bottom)
            insets
        }
    }
    // endregion
}