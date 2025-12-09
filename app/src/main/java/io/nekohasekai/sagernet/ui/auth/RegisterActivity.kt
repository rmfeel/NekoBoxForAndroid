package io.nekohasekai.sagernet.ui.auth

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
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
        runOnDefaultDispatcher {
             ApiClient.service.getConfig().enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (response.isSuccessful) {
                         val data = response.body()?.getAsJsonObject("data")
                         val emailVerify = data?.get("is_email_verify")?.asInt
                         onMainDispatcher {
                             verifyLayout.isVisible = emailVerify == 1
                         }
                    }
                }
                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                }
             })
        }

        sendCodeButton.setOnClickListener {
             val email = emailInput.text.toString()
             if (email.isNotBlank()) {
                 runOnDefaultDispatcher {
                     ApiClient.service.sendEmailVerify(email).enqueue(object : Callback<JsonObject> {
                         override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                             // Handle success/fail toast
                         }
                         override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                         }
                     })
                 }
             }
        }

        registerButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val code = verifyCodeInput.text.toString()

            runOnDefaultDispatcher {
                ApiClient.service.register(email, password, code).enqueue(object: Callback<JsonObject> {
                    override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                         if (response.isSuccessful) {
                             // Auto login or finish
                             onMainDispatcher {
                                 finish()
                             }
                         }
                    }
                    override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                    }
                })
            }
        }
    }
}
