package com.stevanovicm32.mobilnoracunarstvo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
)

@Serializable
data class MessageResponse(
    val message: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val points: Int,
)

@Serializable
data class CreateDropRequest(
    val latitude: Double,
    val longitude: Double,
    @SerialName("photo_url") val photoUrl: String,
    val description: String = "",
    val hint: String = "",
)

@Serializable
data class CreateDropResponse(
    val drop: DropDto,
)

@Serializable
data class DropDto(
    val id: String,
    @SerialName("creator_id") val creatorId: String,
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val hint: String = "",
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("active_at") val activeAt: String,
    @SerialName("first_claimer_id") val firstClaimerId: String? = null,
)

@Serializable
data class HeatmapResponse(
    val cells: List<HeatmapCellDto>? = null,
)

@Serializable
data class HeatmapCellDto(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
)

@Serializable
data class NearbyDropsResponse(
    val drops: List<NearbyDropDto>? = null,
)

@Serializable
data class NearbyDropDto(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("photo_url") val photoUrl: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("hint") val hint: String = "",
    @SerialName("distance_meters") val distanceMeters: Double = 0.0,
)

@Serializable
data class ClaimDropRequest(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class ClaimDropResponse(
    val claim: ClaimDto,
    val drop: NearbyDropDto? = null,
)

@Serializable
data class ClaimDto(
    val id: String,
    @SerialName("drop_id") val dropId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("points_awarded") val pointsAwarded: Int,
    @SerialName("claimed_at") val claimedAt: String,
)

@Serializable
data class UploadResponse(
    @SerialName("photo_url") val photoUrl: String,
)

@Serializable
data class LeaderboardResponse(
    val leaderboard: List<LeaderboardEntryDto>? = null,
)

@Serializable
data class LeaderboardEntryDto(
    val id: String,
    val username: String,
    @SerialName("total_points") val totalPoints: Int,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
