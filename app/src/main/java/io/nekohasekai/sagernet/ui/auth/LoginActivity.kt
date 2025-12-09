package io.nekohasekai.sagernet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_login)

        val apiUrlInput = findViewById<TextInputEditText>(R.id.apiUrlInput)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerText = findViewById<TextView>(R.id.registerText)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)
        val loading = findViewById<ProgressBar>(R.id.loading)

        // Load saved API URL
        val savedUrl = DataStore.apiUrl
        if (!savedUrl.isNullOrEmpty()) {
            apiUrlInput.setText(savedUrl)
        }

        loginButton.setOnClickListener {
            val apiUrl = apiUrlInput.text.toString().trim()
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            if (apiUrl.isBlank() || !apiUrl.startsWith("http")) {
                Toast.makeText(this, "Please enter a valid API URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save API URL and rebuild client
            DataStore.apiUrl = apiUrl
            ApiClient.updateBaseUrl(apiUrl)

            loading.isVisible = true
            loginButton.isEnabled = false

            ApiClient.service.login(email, password).enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    loading.isVisible = false
                    loginButton.isEnabled = true
                    
                    val body = response.body()
                    if (response.isSuccessful && body != null && body.has("data")) {
                        val data = body.getAsJsonObject("data")
                        if (data.has("auth_data")) {
                            val token = data.get("auth_data").asString
                            DataStore.authToken = token
                            
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Login failed: Invalid response", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMsg = body?.get("message")?.asString ?: "Login failed"
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                    loading.isVisible = false
                    loginButton.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }
}
