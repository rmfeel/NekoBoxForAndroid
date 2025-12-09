package io.nekohasekai.sagernet.api

import io.nekohasekai.sagernet.database.DataStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://your.api.endpoint.com" // TODO: Change this or make it configurable

    private var retrofit: Retrofit? = null

    // Helper to get token (Assumes DataStore has been updated to store token)
    private fun getToken(): String? {
        // We will need to add auth_token to DataStore
        return DataStore.authToken
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        val token = getToken()
        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", token)
        }
        val request = builder.build()
        chain.proceed(request)
    }

    val client: Retrofit
        get() {
            if (retrofit == null) {
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                retrofit = Retrofit.Builder()
                    .baseUrl(DataStore.apiUrl.ifEmpty { BASE_URL })
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!
        }

    val service: ApiService
        get() = client.create(ApiService::class.java)
}
