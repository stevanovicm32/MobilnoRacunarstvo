package com.stevanovicm32.mobilnoracunarstvo.data.api

import com.stevanovicm32.mobilnoracunarstvo.data.dto.ClaimDropRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.ClaimDropResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.CreateDropRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.CreateDropResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.HeatmapResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LeaderboardResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LoginRequest
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LoginResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.MessageResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.NearbyDropsResponse
import com.stevanovicm32.mobilnoracunarstvo.data.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GameApiService {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): MessageResponse

    @POST("api/drops")
    suspend fun createDrop(@Body body: CreateDropRequest): CreateDropResponse

    @GET("api/drops/heatmap")
    suspend fun getHeatmap(
        @Query("min_lat") minLat: Double,
        @Query("min_lng") minLng: Double,
        @Query("max_lat") maxLat: Double,
        @Query("max_lng") maxLng: Double,
    ): HeatmapResponse

    @GET("api/drops/nearby")
    suspend fun getNearbyDrops(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius") radius: Int = 20,
    ): NearbyDropsResponse

    @POST("api/drops/{id}/claim")
    suspend fun claimDrop(
        @Path("id") dropId: String,
        @Body body: ClaimDropRequest,
    ): ClaimDropResponse

    @GET("api/leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponse
}
