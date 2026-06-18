package com.stevanovicm32.mobilnoracunarstvo.data.repository

import com.stevanovicm32.mobilnoracunarstvo.data.api.GameApiService
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ErrorResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LoginRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LoginResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.RegisterRequest
import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class AuthRepository(
    private val api: GameApiService,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(username: String, password: String): ApiResult<LoginResponse> {
        return safeCall {
            val response = api.login(LoginRequest(username, password))
            tokenStore.saveSession(
                token = response.token,
                userId = response.user.id,
                username = response.user.username,
                points = response.user.points,
            )
            response
        }
    }

    suspend fun register(username: String, password: String): ApiResult<String> {
        return safeCall {
            api.register(RegisterRequest(username, password)).message
        }
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: HttpException) {
            ApiResult.Error(parseError(e), e.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun parseError(e: HttpException): String {
        val body = e.response()?.errorBody()?.string()
        if (!body.isNullOrBlank()) {
            return runCatching {
                json.decodeFromString<ErrorResponse>(body).error
            }.getOrDefault(body)
        }
        return "Request failed (${e.code()})"
    }
}
