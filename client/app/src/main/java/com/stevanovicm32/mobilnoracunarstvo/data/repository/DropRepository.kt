package com.stevanovicm32.mobilnoracunarstvo.data.repository

import com.stevanovicm32.mobilnoracunarstvo.data.api.GameApiService
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ClaimDropRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ClaimDropResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.CreateDropRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.CreateDropResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ErrorResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.HeatmapCellDto
import com.stevanovicm32.mobilnoracunarstvo.data.dto.NearbyDropDto
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class DropRepository(private val api: GameApiService) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun getHeatmap(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): ApiResult<List<HeatmapCellDto>> = safeCall {
        api.getHeatmap(minLat, minLng, maxLat, maxLng).cells.orEmpty()
    }

    suspend fun getNearbyDrops(
        latitude: Double,
        longitude: Double,
        radius: Int = 50,
    ): ApiResult<List<NearbyDropDto>> = safeCall {
        api.getNearbyDrops(latitude, longitude, radius).drops.orEmpty()
    }

    suspend fun createDrop(
        latitude: Double,
        longitude: Double,
        photoUrl: String,
        description: String,
        hint: String,
    ): ApiResult<CreateDropResponse> = safeCall {
        api.createDrop(CreateDropRequest(latitude, longitude, photoUrl, description, hint))
    }

    suspend fun claimDrop(
        dropId: String,
        latitude: Double,
        longitude: Double,
    ): ApiResult<ClaimDropResponse> = safeCall {
        api.claimDrop(dropId, ClaimDropRequest(latitude, longitude))
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
