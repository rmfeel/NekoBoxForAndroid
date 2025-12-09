package io.nekohasekai.sagernet.ui.auth

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_forgot_password)

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val verifyCodeInput = findViewById<TextInputEditText>(R.id.verifyCodeInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val sendCodeButton = findViewById<Button>(R.id.sendCodeButton)
        val resetButton = findViewById<Button>(R.id.resetButton)

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

        resetButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val code = verifyCodeInput.text.toString()

            runOnDefaultDispatcher {
                ApiClient.service.forget(email, password, code).enqueue(object: Callback<JsonObject> {
                    override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                         if (response.isSuccessful) {
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
