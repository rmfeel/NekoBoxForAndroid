package io.nekohasekai.sagernet.api

import io.nekohasekai.sagernet.database.DataStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val DEFAULT_BASE_URL = "https://your.api.endpoint.com/"

    @Volatile
    private var retrofit: Retrofit? = null
    
    @Volatile
    private var currentBaseUrl: String = ""

    private fun getToken(): String? {
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

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        // Ensure URL ends with /
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun updateBaseUrl(newUrl: String) {
        synchronized(this) {
            val normalizedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
            if (normalizedUrl != currentBaseUrl) {
                currentBaseUrl = normalizedUrl
                retrofit = buildRetrofit(normalizedUrl)
            }
        }
    }

    val client: Retrofit
        get() {
            synchronized(this) {
                val savedUrl = DataStore.apiUrl
                val baseUrl = if (!savedUrl.isNullOrEmpty()) savedUrl else DEFAULT_BASE_URL
                val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                
                if (retrofit == null || currentBaseUrl != normalizedUrl) {
                    currentBaseUrl = normalizedUrl
                    retrofit = buildRetrofit(normalizedUrl)
                }
                return retrofit!!
            }
        }

    val service: ApiService
        get() = client.create(ApiService::class.java)
}
