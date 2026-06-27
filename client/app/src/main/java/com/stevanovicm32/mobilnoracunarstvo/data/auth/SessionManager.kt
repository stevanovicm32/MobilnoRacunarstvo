package com.stevanovicm32.mobilnoracunarstvo.data.auth

import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

class SessionManager(
    private val tokenStore: TokenStore,
) {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    suspend fun clearSession() {
        tokenStore.clear()
    }

    suspend fun notifyUnauthorized() {
        tokenStore.clear()
        _sessionExpired.emit(Unit)
    }

    fun notifyUnauthorizedSync() {
        runBlocking { notifyUnauthorized() }
    }
}
