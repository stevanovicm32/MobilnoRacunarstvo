package com.stevanovicm32.mobilnoracunarstvo.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.stevanovicm32.mobilnoracunarstvo.BuildConfig
import com.stevanovicm32.mobilnoracunarstvo.data.auth.SessionManager
import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(tokenStore: TokenStore, sessionManager: SessionManager): GameApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.getTokenSync()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val unauthorizedInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val path = request.url.encodedPath
            val isAuthRequest = path.endsWith("/auth/login") || path.endsWith("/auth/register")
            if (response.code == 401 && !isAuthRequest) {
                sessionManager.notifyUnauthorizedSync()
            }
            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GameApiService::class.java)
    }
}
