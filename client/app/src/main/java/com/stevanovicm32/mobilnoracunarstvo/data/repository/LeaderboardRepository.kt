package com.stevanovicm32.mobilnoracunarstvo.data.repository

import com.stevanovicm32.mobilnoracunarstvo.data.api.GameApiService
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ErrorResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LeaderboardEntryDto
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class LeaderboardRepository(private val api: GameApiService) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLeaderboard(): ApiResult<List<LeaderboardEntryDto>> = safeCall {
        api.getLeaderboard().leaderboard.orEmpty()
    }

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
