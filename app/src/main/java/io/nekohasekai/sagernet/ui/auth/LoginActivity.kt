package io.nekohasekai.sagernet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_login)

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerText = findViewById<TextView>(R.id.registerText)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)
        val loading = findViewById<ProgressBar>(R.id.loading)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            if (email.isBlank() || password.isBlank()) {
                // snackbar(getString(R.string.invalid_input)).show() // Assuming helper or string resource
                return@setOnClickListener
            }

            loading.isVisible = true
            loginButton.isEnabled = false

            runOnDefaultDispatcher {
                try {
                    ApiClient.service.login(email, password).enqueue(object : Callback<JsonObject> {
                        override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                            loading.isVisible = false
                            loginButton.isEnabled = true
                            
                            val body = response.body()
                            if (response.isSuccessful && body != null && body.has("data")) {
                                val data = body.getAsJsonObject("data")
                                if (data.has("auth_data")) {
                                    val token = data.get("auth_data").asString
                                    
                                    // Save Token
                                    DataStore.authToken = token
                                    
                                    // Navigate to Main
                                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                } else {
                                     onMainDispatcher {
                                        // Handle Login Fail
                                    }
                                }
                            } else {
                                onMainDispatcher {
                                    // Handle Error
                                }
                            }
                        }

                        override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                            loading.isVisible = false
                            loginButton.isEnabled = true
                            onMainDispatcher {
                                // Handle Network Error
                            }
                        }
                    })
                } catch (e: Exception) {
                    loading.isVisible = false
                    loginButton.isEnabled = true
                }
            }
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }
}
