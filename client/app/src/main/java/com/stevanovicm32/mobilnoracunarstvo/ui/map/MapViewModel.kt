package com.stevanovicm32.mobilnoracunarstvo.ui.map

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import com.stevanovicm32.mobilnoracunarstvo.util.DistanceUtils
import com.stevanovicm32.mobilnoracunarstvo.util.MapLatLng
import com.stevanovicm32.mobilnoracunarstvo.util.PhotoUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MapViewModel(
    private val app: GameApp,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.tokenStore.pointsFlow.collect { points ->
                _uiState.update { it.copy(totalPoints = points) }
            }
        }
    }

    private var trackingStarted = false

    fun startLocationTracking() {
        if (trackingStarted) return
        trackingStarted = true
        viewModelScope.launch {
            app.locationTracker.locationUpdates().collect { location ->
                val latLng = MapLatLng(location.latitude, location.longitude)
                _uiState.update { state ->
                    val eligibility = evaluateDropEligibility(
                        accuracy = location.accuracyMeters,
                        weeklyAvailable = state.weeklyDropAvailable,
                    )
                    state.copy(
                        userLocation = latLng,
                        locationAccuracy = location.accuracyMeters,
                        canCreateDrop = eligibility.canCreate,
                        dropBlockedReason = eligibility.reason,
                    )
                }
                if (location.accuracyMeters <= DistanceUtils.GPS_MIN_ACCURACY_M) {
                    fetchNearbyDrops(location.latitude, location.longitude)
                }
            }
        }
    }

    fun onCameraIdle(bounds: CameraBounds) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHeatmap = true) }
            when (
                val result = app.dropRepository.getHeatmap(
                    minLat = bounds.minLat,
                    minLng = bounds.minLng,
                    maxLat = bounds.maxLat,
                    maxLng = bounds.maxLng,
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(heatmapCells = result.data, isLoadingHeatmap = false) }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingHeatmap = false,
                            snackbarMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun fetchNearbyDrops(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            when (val result = app.dropRepository.getNearbyDrops(latitude, longitude)) {
                is ApiResult.Success -> {
                    val inRange = result.data.filter { drop ->
                        DistanceUtils.distanceMeters(
                            latitude,
                            longitude,
                            drop.latitude,
                            drop.longitude,
                        ) <= DistanceUtils.CLAIM_MAX_DISTANCE_M
                    }
                    val claimable = inRange.minByOrNull { drop ->
                        DistanceUtils.distanceMeters(
                            latitude,
                            longitude,
                            drop.latitude,
                            drop.longitude,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            nearbyDrops = result.data,
                            claimableDrop = claimable,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(snackbarMessage = result.message)
                    }
                }
            }
        }
    }

    fun onLeaveDropClicked() {
        val state = _uiState.value
        val eligibility = evaluateDropEligibility(
            accuracy = state.locationAccuracy,
            weeklyAvailable = state.weeklyDropAvailable,
        )
        if (!eligibility.canCreate) {
            if (state.locationAccuracy == null ||
                state.locationAccuracy > DistanceUtils.GPS_MIN_ACCURACY_M
            ) {
                _uiState.update { it.copy(showPoorGpsDialog = true) }
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = eligibility.reason ?: "Cannot leave a drop here right now.")
                }
            }
            return
        }
        _uiState.update {
            it.copy(
                showCreateDropSheet = true,
                dropDescription = "",
                dropHint = "",
                dropPhotoUri = null,
            )
        }
    }

    fun dismissCreateDropSheet() {
        _uiState.update {
            it.copy(
                showCreateDropSheet = false,
                dropDescription = "",
                dropHint = "",
                dropPhotoUri = null,
            )
        }
    }

    fun onDropDescriptionChange(value: String) {
        _uiState.update { it.copy(dropDescription = value) }
    }

    fun onDropHintChange(value: String) {
        _uiState.update { it.copy(dropHint = value) }
    }

    fun onDropPhotoSelected(uri: Uri) {
        _uiState.update { it.copy(dropPhotoUri = uri) }
    }

    fun submitDrop() {
        val state = _uiState.value
        val location = state.userLocation ?: return
        val photoUri = state.dropPhotoUri
        if (photoUri == null) {
            _uiState.update { it.copy(snackbarMessage = "Add a photo before submitting.") }
            return
        }

        val eligibility = evaluateDropEligibility(
            accuracy = state.locationAccuracy,
            weeklyAvailable = state.weeklyDropAvailable,
        )
        if (!eligibility.canCreate) {
            _uiState.update {
                it.copy(snackbarMessage = eligibility.reason ?: "Cannot leave a drop here right now.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingDrop = true) }
            try {
                val photoUrl = PhotoUploader.upload(app, app.tokenStore, app.sessionManager, photoUri)
                when (
                    val result = app.dropRepository.createDrop(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        photoUrl = photoUrl,
                        description = state.dropDescription.trim(),
                        hint = state.dropHint.trim(),
                    )
                ) {
                    is ApiResult.Success -> {
                        val activeAt = formatActiveAt(result.data.drop.activeAt)
                        _uiState.update {
                            it.copy(
                                isCreatingDrop = false,
                                showCreateDropSheet = false,
                                dropDescription = "",
                                dropHint = "",
                                dropPhotoUri = null,
                                weeklyDropAvailable = false,
                                canCreateDrop = false,
                                dropBlockedReason = "Weekly drop already used.",
                                snackbarMessage = "Drop created! It becomes visible at $activeAt.",
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        val weeklyUsed = result.code == 409 &&
                            result.message.contains("Weekly limit", ignoreCase = true)
                        val eligibilityAfter = evaluateDropEligibility(
                            accuracy = state.locationAccuracy,
                            weeklyAvailable = if (weeklyUsed) false else state.weeklyDropAvailable,
                        )
                        _uiState.update {
                            it.copy(
                                isCreatingDrop = false,
                                weeklyDropAvailable = if (weeklyUsed) false else it.weeklyDropAvailable,
                                canCreateDrop = eligibilityAfter.canCreate,
                                dropBlockedReason = eligibilityAfter.reason,
                                snackbarMessage = mapCreateError(result.message),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingDrop = false,
                        snackbarMessage = e.message ?: "Failed to upload photo",
                    )
                }
            }
        }
    }

    fun dismissPoorGpsDialog() {
        _uiState.update { it.copy(showPoorGpsDialog = false) }
    }

    fun claimDrop() {
        val state = _uiState.value
        val drop = state.claimableDrop ?: return
        val location = state.userLocation ?: return

        val distance = DistanceUtils.distanceMeters(
            location.latitude,
            location.longitude,
            drop.latitude,
            drop.longitude,
        )
        if (distance > DistanceUtils.CLAIM_MAX_DISTANCE_M) {
            _uiState.update {
                it.copy(snackbarMessage = "Move closer to claim this drop.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isClaimingDrop = true) }
            when (
                val result = app.dropRepository.claimDrop(
                    dropId = drop.id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                )
            ) {
                is ApiResult.Success -> {
                    val awarded = result.data.claim.pointsAwarded
                    val newTotal = state.totalPoints + awarded
                    val claimedDrop = result.data.drop ?: drop
                    app.tokenStore.updatePoints(newTotal)
                    _uiState.update {
                        it.copy(
                            isClaimingDrop = false,
                            claimableDrop = null,
                            nearbyDrops = emptyList(),
                            totalPoints = newTotal,
                            claimedDrop = claimedDrop,
                            lastPointsAwarded = awarded,
                            showClaimSuccessDialog = true,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isClaimingDrop = false,
                            snackbarMessage = mapClaimError(result.message, result.code),
                        )
                    }
                }
            }
        }
    }

    fun dismissClaimSuccessDialog() {
        _uiState.update {
            it.copy(
                showClaimSuccessDialog = false,
                claimedDrop = null,
                lastPointsAwarded = null,
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private data class DropEligibility(
        val canCreate: Boolean,
        val reason: String?,
    )

    private fun evaluateDropEligibility(
        accuracy: Float?,
        weeklyAvailable: Boolean,
    ): DropEligibility {
        if (!weeklyAvailable) {
            return DropEligibility(false, "Weekly drop already used.")
        }
        if (accuracy == null) {
            return DropEligibility(false, "Waiting for GPS fix.")
        }
        if (accuracy > DistanceUtils.GPS_MIN_ACCURACY_M) {
            return DropEligibility(false, "GPS accuracy is poor (> 20 m).")
        }
        return DropEligibility(true, null)
    }

    private fun mapCreateError(message: String): String = when {
        message.contains("Weekly limit", ignoreCase = true) ->
            "You've already dropped this week. Try again next week."
        message.contains("within 50 meters", ignoreCase = true) ->
            "Move further away — another drop is too close."
        else -> message
    }

    private fun mapClaimError(message: String, code: Int?): String = when {
        code == 403 -> "GPS drift detected — move closer and try again."
        message.contains("not active", ignoreCase = true) ->
            "This drop isn't active yet."
        message.contains("already claimed", ignoreCase = true) ->
            "You already claimed this drop."
        else -> message
    }

    private fun formatActiveAt(activeAt: String): String {
        return runCatching {
            ZonedDateTime.parse(activeAt).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault(activeAt)
    }

    fun logout() {
        viewModelScope.launch {
            app.authRepository.logout()
            _uiState.update { it.copy(logoutRequested = true) }
        }
    }

    fun onLogoutHandled() {
        _uiState.update { it.copy(logoutRequested = false) }
    }

    companion object {
        fun factory(app: GameApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MapViewModel(app) as T
                }
            }
    }
}

data class CameraBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)
