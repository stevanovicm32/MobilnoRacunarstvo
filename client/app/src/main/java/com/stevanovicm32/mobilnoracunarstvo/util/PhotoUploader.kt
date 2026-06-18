package com.stevanovicm32.mobilnoracunarstvo.util

import android.net.Uri
import java.util.UUID

/**
 * Placeholder photo uploader. Replace with real storage integration later.
 */
object PhotoUploader {
    suspend fun upload(photoUri: Uri): String {
        return "https://placeholder.example/photos/${UUID.randomUUID()}.jpg"
    }
}
