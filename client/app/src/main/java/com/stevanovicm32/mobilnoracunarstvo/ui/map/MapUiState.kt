package com.stevanovicm32.mobilnoracunarstvo.ui.map

import com.stevanovicm32.mobilnoracunarstvo.data.dto.HeatmapCellDto
import com.stevanovicm32.mobilnoracunarstvo.data.dto.NearbyDropDto
import com.stevanovicm32.mobilnoracunarstvo.util.MapLatLng

data class MapUiState(
    val userLocation: MapLatLng? = null,
    val locationAccuracy: Float? = null,
    val heatmapCells: List<HeatmapCellDto> = emptyList(),
    val nearbyDrops: List<NearbyDropDto> = emptyList(),
    val totalPoints: Int = 0,
    val weeklyDropAvailable: Boolean = true,
    val isLoadingHeatmap: Boolean = false,
    val isCreatingDrop: Boolean = false,
    val isClaimingDrop: Boolean = false,
    val claimableDrop: NearbyDropDto? = null,
    val canCreateDrop: Boolean = false,
    val snackbarMessage: String? = null,
    val showPoorGpsDialog: Boolean = false,
    val showClaimSuccessDialog: Boolean = false,
    val lastPointsAwarded: Int? = null,
    val shouldLaunchCamera: Boolean = false,
)
