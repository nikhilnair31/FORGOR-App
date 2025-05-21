package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sil.others.Helpers

class SignUp : AppCompatActivity() {
    // region Vars
    private val TAG = "SignIn"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signupButton: Button
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        uiInitRelated()
    }
    // endregion

    // region UI Related
    private fun uiInitRelated() {
        val minPasswordLength = resources.getInteger(R.integer.minPasswordLength)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        signupButton = findViewById(R.id.buttonSignup)

        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val username = usernameEditText.text.toString()
                val password = passwordEditText.text.toString()

                signupButton.isEnabled = username.isNotEmpty() && password.length >= minPasswordLength
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        usernameEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)

        signupButton.setOnClickListener {
            signupRelated()
        }
    }

    private fun highlightButtonEffects(button: Button, newText: String) {
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_0))
        button.setTextColor(ContextCompat.getColor(this, R.color.accent_1))
        button.text = newText
    }
    // endregion

    // region Auth Related
    private fun signupRelated() {
        Log.i(TAG, "Signing up user...")

        val userNameText = usernameEditText.text.toString()
        val passwordText = passwordEditText.text.toString()

        Helpers.authRegisterToServer(this, userNameText, passwordText) { registerSuccess ->
            runOnUiThread {
                if (registerSuccess) {
                    Log.i(TAG, "Sign up success!")

                    highlightButtonEffects(signupButton, getString(R.string.signupSuccessText))

                    generalSharedPreferences.edit {
                        putString("userName", userNameText)
                    }

                    val intent = Intent(this, Features::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Helpers.showToast(this, "Sign up failed.")
                }
            }
        }
    }
    // endregion
}