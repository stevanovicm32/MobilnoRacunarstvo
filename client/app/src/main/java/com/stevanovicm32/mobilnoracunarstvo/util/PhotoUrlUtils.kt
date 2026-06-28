package com.stevanovicm32.mobilnoracunarstvo.util

import com.stevanovicm32.mobilnoracunarstvo.BuildConfig

object PhotoUrlUtils {
    fun resolve(photoUrl: String): String {
        if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
            return photoUrl
        }
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val path = if (photoUrl.startsWith("/")) photoUrl else "/$photoUrl"
        return base + path
    }
}
