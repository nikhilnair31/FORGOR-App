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
import kotlin.toString

class SignIn : AppCompatActivity() {
    // region Vars
    private val TAG = "SignIn"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

    private lateinit var generalSharedPreferences: SharedPreferences

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    // endregion

    // region Common
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        generalSharedPreferences = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)

        uiInitRelated()
    }
    // endregion

    // region UI Related
    private fun uiInitRelated() {
        val minPasswordLength = resources.getInteger(R.integer.minPasswordLength)
        usernameEditText = findViewById(R.id.usernameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.buttonSignup)

        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val username = usernameEditText.text.toString()
                val email = emailEditText.text.toString()
                val password = passwordEditText.text.toString()

                loginButton.isEnabled =
                    username.isNotEmpty()
                    && email.isNotEmpty()
                    && password.isNotEmpty()
                    && password.length >= minPasswordLength
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        usernameEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)

        loginButton.setOnClickListener {
            loginRelated()
        }
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
        val emailText = emailEditText.text.toString()
        val passwordText = passwordEditText.text.toString()

        Helpers.authLoginToServer(this, userNameText, emailText, passwordText) { success ->
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "Login success!")

                    highlightButtonEffects(loginButton, getString(R.string.loginSuccessText))

                    generalSharedPreferences.edit {
                        putString("username", userNameText)
                        putString("email", emailText)
                    }

                    val intent = Intent(this, Features::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.i(TAG, "Login failed.")
                }
            }
        }
    }
    // endregion
}