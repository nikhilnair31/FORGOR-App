package com.sil.buildmode

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class Settings : AppCompatActivity() {
    // region Vars
    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var backButton: ImageButton

    private lateinit var usernameText: TextView

    private lateinit var imageSaveCheckbox: CheckBox
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        generalSharedPreferences = getSharedPreferences("com.sil.buildmode.generalSharedPrefs", MODE_PRIVATE)

        usernameText = findViewById(R.id.usernameText)
        imageSaveCheckbox = findViewById(R.id.imageSaveCheckbox)
        backButton = findViewById(R.id.buttonBack)

        textSetup()
        checkboxSetup()
        buttonSetup()
    }
    // endregion

    // region UI Related
    private fun textSetup() {
        usernameText.text = generalSharedPreferences.getString("userName", "")
    }

    private fun checkboxSetup() {
        imageSaveCheckbox.isChecked =  generalSharedPreferences.getString("saveImageFiles", "false").toBoolean()
        imageSaveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            generalSharedPreferences.edit { putString("saveImageFiles", isChecked.toString()) }
        }
    }

    private fun buttonSetup() {
        backButton.setOnClickListener {
            onBackPressed()
        }
    }
    // endregion
}