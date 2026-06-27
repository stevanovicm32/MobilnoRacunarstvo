package com.stevanovicm32.mobilnoracunarstvo.util

import android.content.Context
import android.net.Uri
import com.stevanovicm32.mobilnoracunarstvo.BuildConfig
import com.stevanovicm32.mobilnoracunarstvo.data.auth.SessionManager
import com.stevanovicm32.mobilnoracunarstvo.data.dto.UploadResponse
import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object PhotoUploader {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    suspend fun upload(context: Context, tokenStore: TokenStore, sessionManager: SessionManager, photoUri: Uri): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
                ?: throw IOException("Could not read photo")

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "drop.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .build()

            val token = tokenStore.getTokenSync()
                ?: throw IOException("Not authenticated")

            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}api/uploads")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: throw IOException("Empty upload response")
                if (response.code == 401) {
                    sessionManager.notifyUnauthorized()
                    throw IOException("Session expired")
                }
                if (!response.isSuccessful) {
                    throw IOException(responseBody)
                }
                val upload = json.decodeFromString<UploadResponse>(responseBody)
                upload.photoUrl
            }
        }
}
