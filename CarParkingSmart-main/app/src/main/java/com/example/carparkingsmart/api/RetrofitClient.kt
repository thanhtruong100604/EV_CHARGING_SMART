package com.example.carparkingsmart.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://carparkingsmart.onrender.com/"

    /** Trả về token đăng nhập hiện tại (MainActivity gán). Null = chưa đăng nhập. */
    @Volatile
    var tokenProvider: (() -> String?)? = null

    /** Được gọi khi server trả 401 (token hết hạn / sai) → MainActivity đưa người dùng về màn hình đăng nhập. */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    // Gắn "Authorization: Token ..." vào mọi request; server dùng token để biết đúng người dùng
    private val authInterceptor = Interceptor { chain ->
        val token = tokenProvider?.invoke()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder().header("Authorization", "Token $token").build()
        } else chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && !token.isNullOrBlank()) onUnauthorized?.invoke()
        response
    }

    // Render gói miễn phí "ngủ" khi không có người dùng; lần gọi đầu có thể mất 30–60 giây.
    // Chỉ tự thử lại các request đọc (GET) để không đặt chỗ 2 lần.
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.method != "GET") return@Interceptor chain.proceed(request)

        var lastError: IOException? = null
        for (attempt in 0..2) {
            try {
                val response = chain.proceed(request)
                if (response.code !in listOf(502, 503, 504) || attempt == 2) return@Interceptor response
                response.close()
            } catch (e: IOException) {
                lastError = e
                if (attempt == 2) throw e
            }
            try {
                Thread.sleep(3000)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Đã hủy", ie)
            }
        }
        throw lastError ?: IOException("Không kết nối được máy chủ")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
