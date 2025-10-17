package com.example.shaketilt

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val layout = ConstraintLayout(this).apply {
            id = ConstraintLayout.generateViewId()
            setBackgroundColor(Color.parseColor("#00bfff")) // match MainActivity
        }

        errorText = TextView(this).apply {
            id = ConstraintLayout.generateViewId()
            setTextColor(Color.RED)
            textSize = 16f
        }

        val emailInput = EditText(this).apply {
            hint = "Email"
            id = ConstraintLayout.generateViewId()
        }

        val passwordInput = EditText(this).apply {
            hint = "Password"
            id = ConstraintLayout.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val clearErrorTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                errorText.text = ""
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        emailInput.addTextChangedListener(clearErrorTextWatcher)
        passwordInput.addTextChangedListener(clearErrorTextWatcher)

        val loginButton = Button(this).apply {
            text = "Login / Sign Up"
            id = ConstraintLayout.generateViewId()
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                if (email.isBlank() || password.isBlank()) {
                    showError("Fill in both fields")
                    return@setOnClickListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            goToMain()
                        } else {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { createTask ->
                                    if (createTask.isSuccessful) {
                                        goToMain()
                                    } else {
                                        showError("Login/Signup failed: ${createTask.exception?.message}")
                                    }
                                }
                        }
                    }
            }
        }

        layout.addView(errorText)
        layout.addView(emailInput)
        layout.addView(passwordInput)
        layout.addView(loginButton)

        val set = ConstraintSet()
        set.clone(layout)

        val fieldWidth = 800
        val spacing = 50

        set.connect(errorText.id, ConstraintSet.TOP, layout.id, ConstraintSet.TOP, 150)
        set.connect(errorText.id, ConstraintSet.START, layout.id, ConstraintSet.START)
        set.connect(errorText.id, ConstraintSet.END, layout.id, ConstraintSet.END)

        set.constrainWidth(emailInput.id, fieldWidth)
        set.connect(emailInput.id, ConstraintSet.TOP, errorText.id, ConstraintSet.BOTTOM, spacing)
        set.connect(emailInput.id, ConstraintSet.START, layout.id, ConstraintSet.START)
        set.connect(emailInput.id, ConstraintSet.END, layout.id, ConstraintSet.END)

        set.constrainWidth(passwordInput.id, fieldWidth)
        set.connect(passwordInput.id, ConstraintSet.TOP, emailInput.id, ConstraintSet.BOTTOM, spacing)
        set.connect(passwordInput.id, ConstraintSet.START, layout.id, ConstraintSet.START)
        set.connect(passwordInput.id, ConstraintSet.END, layout.id, ConstraintSet.END)

        set.constrainWidth(loginButton.id, fieldWidth)
        set.connect(loginButton.id, ConstraintSet.TOP, passwordInput.id, ConstraintSet.BOTTOM, spacing)
        set.connect(loginButton.id, ConstraintSet.START, layout.id, ConstraintSet.START)
        set.connect(loginButton.id, ConstraintSet.END, layout.id, ConstraintSet.END)

        set.applyTo(layout)
        setContentView(layout)
    }

    private fun showError(message: String) {
        errorText.text = message
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}