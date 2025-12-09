package io.nekohasekai.sagernet.api

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("/api/v1/passport/auth/login")
    @FormUrlEncoded
    fun login(@Field("email") email: String, @Field("password") password: String): Call<JsonObject>

    @POST("/api/v1/passport/auth/register")
    @FormUrlEncoded
    fun register(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("auth_code") authCode: String?
    ): Call<JsonObject>

    @POST("/api/v1/passport/auth/sendEmailVerify")
    @FormUrlEncoded
    fun sendEmailVerify(@Field("email") email: String): Call<JsonObject>

    @POST("/api/v1/passport/auth/forget")
    @FormUrlEncoded
    fun forget(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("email_code") emailCode: String
    ): Call<JsonObject>

    @GET("/api/v1/user/getSubscribe")
    fun getSubscribe(): Call<JsonObject>

    @GET("/api/v1/guest/comm/config")
    fun getConfig(): Call<JsonObject>
}
