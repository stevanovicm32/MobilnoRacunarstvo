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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import com.stevanovicm32.mobilnoracunarstvo.util.PhotoUrlUtils
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenLeaderboard: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(GameApp.instance)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && pendingCameraUri != null) {
            viewModel.onDropPhotoSelected(pendingCameraUri!!)
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera(context) { uri ->
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    fun launchDropCamera() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            launchCamera(context) { uri ->
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.logoutRequested) {
        if (uiState.logoutRequested) {
            viewModel.onLogoutHandled()
            onLogout()
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

    uiState.claimedDrop?.let { claimedDrop ->
        if (uiState.showClaimSuccessDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissClaimSuccessDialog,
                title = { Text("Drop claimed!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (claimedDrop.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = PhotoUrlUtils.resolve(claimedDrop.photoUrl),
                                contentDescription = "Claimed drop photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (claimedDrop.description.isNotBlank()) {
                            Text(
                                text = claimedDrop.description,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (claimedDrop.hint.isNotBlank()) {
                            Text(
                                text = "Hint: ${claimedDrop.hint}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        uiState.lastPointsAwarded?.let { points ->
                            Text(
                                text = "You earned $points points!" +
                                    if (points == 500) " (First claimer bonus)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissClaimSuccessDialog) {
                        Text("OK")
                    }
                },
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to continue playing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.showCreateDropSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissCreateDropSheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Leave a Drop",
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedTextField(
                    value = uiState.dropDescription,
                    onValueChange = viewModel::onDropDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.dropHint,
                    onValueChange = viewModel::onDropHintChange,
                    label = { Text("Hint") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.dropPhotoUri != null) {
                    AsyncImage(
                        model = uiState.dropPhotoUri,
                        contentDescription = "Drop photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    OutlinedButton(
                        onClick = ::launchDropCamera,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Text("Take photo", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                uiState.dropBlockedReason?.let { reason ->
                    Text(
                        text = reason,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::submitDrop,
                    enabled = uiState.dropPhotoUri != null && !uiState.isCreatingDrop,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isCreatingDrop) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Submit Drop")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            StatusBar(
                totalPoints = uiState.totalPoints,
                weeklyDropAvailable = uiState.weeklyDropAvailable,
                accuracy = uiState.locationAccuracy,
                onOpenLeaderboard = onOpenLeaderboard,
                onLogout = { showLogoutDialog = true },
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                uiState.claimableDrop?.let { drop ->
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (drop.description.isNotBlank()) {
                                Text(
                                    text = drop.description,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (drop.hint.isNotBlank()) {
                                Text(
                                    text = "Hint: ${drop.hint}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

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
                        .padding(top = 8.dp),
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
    onOpenLeaderboard: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(
                        text = "Points: $totalPoints",
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = if (weeklyDropAvailable) "Weekly drop: available" else "Weekly drop: used",
                    color = if (weeklyDropAvailable) Color(0xFF4CAF50) else Color(0xFFE57373),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (accuracy != null) {
                Text(
                    text = "GPS ±${accuracy.toInt()}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenLeaderboard,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leaderboard")
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log out")
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
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
