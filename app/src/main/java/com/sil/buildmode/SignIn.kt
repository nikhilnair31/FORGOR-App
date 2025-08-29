package com.sil.buildmode

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sil.others.Helpers
import kotlin.math.max
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
    private lateinit var rootConstraintLayout: ConstraintLayout
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

        rootConstraintLayout = findViewById(R.id.rootConstraintLayout)
        usernameEditText = findViewById(R.id.usernameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.buttonSignup)

        // single place to update enabled + color
        fun updateLoginButtonState() {
            val isValid = usernameEditText.text.isNullOrBlank().not() &&
                    emailEditText.text.isNullOrBlank().not() &&
                    (passwordEditText.text?.length ?: 0) >= minPasswordLength

            loginButton.isEnabled = isValid
            val tintColor = if (isValid)
                ContextCompat.getColor(this, R.color.accent_1)     // enabled color
            else
                ContextCompat.getColor(this, android.R.color.darker_gray) // disabled color

            loginButton.backgroundTintList = ColorStateList.valueOf(tintColor)
        }

        // lightweight watcher shared by all three fields
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateLoginButtonState()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        usernameEditText.addTextChangedListener(watcher)
        emailEditText.addTextChangedListener(watcher)        // ← you were missing this
        passwordEditText.addTextChangedListener(watcher)

        // set initial state based on any prefilled text
        updateLoginButtonState()

        loginButton.setOnClickListener { loginRelated() }

        // insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootConstraintLayout) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = max(ime.bottom + 24, sys.bottom)
            v.updatePadding(bottom = bottom)
            insets
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

                    highlightButtonEffects(loginButton, getString(R.string.loginSuccess))

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