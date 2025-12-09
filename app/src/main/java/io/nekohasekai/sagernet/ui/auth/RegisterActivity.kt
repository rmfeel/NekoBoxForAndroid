package io.nekohasekai.sagernet.ui.auth

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_register)

        val verifyLayout = findViewById<LinearLayout>(R.id.verifyLayout)
        val sendCodeButton = findViewById<Button>(R.id.sendCodeButton)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val verifyCodeInput = findViewById<TextInputEditText>(R.id.verifyCodeInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)

        // Check if verification is needed
        ApiClient.service.getConfig().enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                     val data = response.body()?.getAsJsonObject("data")
                     val emailVerify = data?.get("is_email_verify")?.asInt
                     verifyLayout.isVisible = emailVerify == 1
                }
            }
            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
            }
        })

        sendCodeButton.setOnClickListener {
             val email = emailInput.text.toString()
             if (email.isNotBlank()) {
                 ApiClient.service.sendEmailVerify(email).enqueue(object : Callback<JsonObject> {
                     override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                         if (response.isSuccessful) {
                             Toast.makeText(this@RegisterActivity, "Code sent", Toast.LENGTH_SHORT).show()
                         }
                     }
                     override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                         Toast.makeText(this@RegisterActivity, "Failed to send code", Toast.LENGTH_SHORT).show()
                     }
                 })
             }
        }

        registerButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val code = verifyCodeInput.text.toString()

            ApiClient.service.register(email, password, code).enqueue(object: Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                     if (response.isSuccessful) {
                         Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                         finish()
                     } else {
                         Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                     }
                }
                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                    Toast.makeText(this@RegisterActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
