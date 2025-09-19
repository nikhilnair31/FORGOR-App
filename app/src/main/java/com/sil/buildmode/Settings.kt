package com.sil.buildmode

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

class Settings : AppCompatActivity() {
    // region Vars
    private val TAG = "Settings"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var userButton: Button
    private lateinit var savingButton: Button
    private lateinit var featuresButton: Button
    private lateinit var rootConstraintLayout: ConstraintLayout
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        initRelated()
    }
    private fun initRelated() {
        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        userButton = findViewById(R.id.userActivityButton)
        savingButton = findViewById(R.id.savingActivityButton)
        featuresButton = findViewById(R.id.featuresActivityButton)

        userButton.setOnClickListener {
            startActivity(Intent(this, User::class.java))
        }
        savingButton.setOnClickListener {
            startActivity(Intent(this, Saving::class.java))
        }
        featuresButton.setOnClickListener {
            startActivity(Intent(this, FeatureToggles::class.java))
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
}