package com.stevanovicm32.mobilnoracunarstvo.ui.map

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import com.stevanovicm32.mobilnoracunarstvo.data.dto.HeatmapCellDto
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import com.stevanovicm32.mobilnoracunarstvo.util.DistanceUtils
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
                val latLng = LatLng(location.latitude, location.longitude)
                _uiState.update { state ->
                    val canCreate = canCreateDrop(
                        accuracy = location.accuracyMeters,
                        userLat = location.latitude,
                        userLng = location.longitude,
                        cells = state.heatmapCells,
                        weeklyAvailable = state.weeklyDropAvailable,
                    )
                    state.copy(
                        userLocation = latLng,
                        locationAccuracy = location.accuracyMeters,
                        canCreateDrop = canCreate,
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
                    _uiState.update { state ->
                        val userLoc = state.userLocation
                        val canCreate = if (userLoc != null && state.locationAccuracy != null) {
                            canCreateDrop(
                                accuracy = state.locationAccuracy,
                                userLat = userLoc.latitude,
                                userLng = userLoc.longitude,
                                cells = result.data,
                                weeklyAvailable = state.weeklyDropAvailable,
                            )
                        } else {
                            false
                        }
                        state.copy(
                            heatmapCells = result.data,
                            isLoadingHeatmap = false,
                            canCreateDrop = canCreate,
                        )
                    }
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
                            nearbyDrops = inRange,
                            claimableDrop = claimable,
                        )
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun onLeaveDropClicked() {
        val state = _uiState.value
        val accuracy = state.locationAccuracy
        if (accuracy == null || accuracy > DistanceUtils.GPS_MIN_ACCURACY_M) {
            _uiState.update { it.copy(showPoorGpsDialog = true) }
            return
        }
        if (!state.canCreateDrop) {
            _uiState.update {
                it.copy(snackbarMessage = "Cannot leave a drop here right now.")
            }
            return
        }
        _uiState.update { it.copy(shouldLaunchCamera = true) }
    }

    fun onCameraLaunched() {
        _uiState.update { it.copy(shouldLaunchCamera = false) }
    }

    fun dismissPoorGpsDialog() {
        _uiState.update { it.copy(showPoorGpsDialog = false) }
    }

    fun createDrop(photoUri: Uri) {
        val state = _uiState.value
        val location = state.userLocation ?: return
        val accuracy = state.locationAccuracy
        if (accuracy == null || accuracy > DistanceUtils.GPS_MIN_ACCURACY_M) {
            _uiState.update { it.copy(showPoorGpsDialog = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingDrop = true) }
            val photoUrl = PhotoUploader.upload(photoUri)
            when (
                val result = app.dropRepository.createDrop(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    photoUrl = photoUrl,
                )
            ) {
                is ApiResult.Success -> {
                    val activeAt = formatActiveAt(result.data.drop.activeAt)
                    _uiState.update {
                        it.copy(
                            isCreatingDrop = false,
                            weeklyDropAvailable = false,
                            canCreateDrop = false,
                            snackbarMessage = "Drop created! It becomes visible at $activeAt.",
                        )
                    }
                }
                is ApiResult.Error -> {
                    val weeklyUsed = result.code == 409 &&
                        result.message.contains("Weekly limit", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            isCreatingDrop = false,
                            weeklyDropAvailable = if (weeklyUsed) false else it.weeklyDropAvailable,
                            snackbarMessage = mapCreateError(result.message),
                        )
                    }
                }
            }
        }
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
                    app.tokenStore.updatePoints(newTotal)
                    _uiState.update {
                        it.copy(
                            isClaimingDrop = false,
                            claimableDrop = null,
                            nearbyDrops = emptyList(),
                            totalPoints = newTotal,
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
            it.copy(showClaimSuccessDialog = false, lastPointsAwarded = null)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun canCreateDrop(
        accuracy: Float,
        userLat: Double,
        userLng: Double,
        cells: List<HeatmapCellDto>,
        weeklyAvailable: Boolean,
    ): Boolean {
        if (!weeklyAvailable) return false
        if (accuracy > DistanceUtils.GPS_MIN_ACCURACY_M) return false
        val tooClose = cells.any { cell ->
            DistanceUtils.distanceMeters(userLat, userLng, cell.latitude, cell.longitude) <
                DistanceUtils.CREATE_MIN_DISTANCE_M
        }
        return !tooClose
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
