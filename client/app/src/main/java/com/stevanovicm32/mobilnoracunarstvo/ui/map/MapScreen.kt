package com.stevanovicm32.mobilnoracunarstvo.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(GameApp.instance)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    var pendingCreateDrop by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && cameraPhotoUri != null) {
            viewModel.createDrop(cameraPhotoUri!!)
        }
        pendingCreateDrop = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingCreateDrop) {
            launchCamera(context) { uri ->
                cameraPhotoUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    LaunchedEffect(uiState.shouldLaunchCamera) {
        if (uiState.shouldLaunchCamera) {
            pendingCreateDrop = true
            val hasCamera = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCamera) {
                launchCamera(context) { uri ->
                    cameraPhotoUri = uri
                    cameraLauncher.launch(uri)
                }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            viewModel.onCameraLaunched()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.startLocationTracking()
        }
    }

    if (!locationPermissions.allPermissionsGranted) {
        PermissionRationale(
            onRequest = { locationPermissions.launchMultiplePermissionRequest() },
        )
        return
    }

    if (uiState.showPoorGpsDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPoorGpsDialog,
            title = { Text("Waiting for GPS") },
            text = { Text("GPS accuracy is poor (> 20 m). Wait for a better signal before leaving a drop.") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPoorGpsDialog) {
                    Text("OK")
                }
            },
        )
    }

    if (uiState.showClaimSuccessDialog && uiState.lastPointsAwarded != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClaimSuccessDialog,
            title = { Text("Drop claimed!") },
            text = {
                Text(
                    "You earned ${uiState.lastPointsAwarded} points!" +
                        if (uiState.lastPointsAwarded == 500) " (First claimer bonus)" else "",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissClaimSuccessDialog) {
                    Text("OK")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StatusBar(
                totalPoints = uiState.totalPoints,
                weeklyDropAvailable = uiState.weeklyDropAvailable,
                accuracy = uiState.locationAccuracy,
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (uiState.claimableDrop != null) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::claimDrop,
                        icon = {
                            if (uiState.isClaimingDrop) {
                                CircularProgressIndicator()
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            }
                        },
                        text = { Text("Claim Drop") },
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = viewModel::onLeaveDropClicked,
                    icon = {
                        if (uiState.isCreatingDrop) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Default.AddLocation, contentDescription = null)
                        }
                    },
                    text = { Text("Leave a Drop") },
                    containerColor = if (uiState.canCreateDrop) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation,
                heatmapCells = uiState.heatmapCells,
                nearbyDrops = uiState.nearbyDrops,
                onCameraIdle = viewModel::onCameraIdle,
            )

            if (uiState.isLoadingHeatmap) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    totalPoints: Int,
    weeklyDropAvailable: Boolean,
    accuracy: Float?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null)
                Text(
                    text = "Points: $totalPoints",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = if (weeklyDropAvailable) "Weekly drop: available" else "Weekly drop: used",
                color = if (weeklyDropAvailable) Color(0xFF4CAF50) else Color(0xFFE57373),
            )
            if (accuracy != null) {
                Text(text = "GPS ±${accuracy.toInt()}m")
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Location permission is required to play.")
            TextButton(onClick = onRequest) {
                Text("Grant permission")
            }
        }
    }
}

private fun launchCamera(context: android.content.Context, onUriReady: (Uri) -> Unit) {
    val photoFile = File.createTempFile("drop_", ".jpg", context.cacheDir)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile,
    )
    onUriReady(uri)
}
