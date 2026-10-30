package com.dxn.stockpoint.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Default: Android emulator → localhost. Change in Settings for real device / tunnel.
    private var baseUrl: String = "http://10.0.2.2:3000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient
        get() = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private var retrofit: Retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService get() = retrofit.create(ApiService::class.java)

    /** Call this when the user saves a new server URL in Settings */
    fun setBaseUrl(url: String) {
        var normalized = url.trim()
        if (!normalized.endsWith("/")) normalized += "/"
        baseUrl = normalized
        retrofit = buildRetrofit()
    }

    fun getBaseUrl(): String = baseUrl
}

