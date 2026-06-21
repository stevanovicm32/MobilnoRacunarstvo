package com.stevanovicm32.mobilnoracunarstvo.ui.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stevanovicm32.mobilnoracunarstvo.data.dto.HeatmapCellDto
import com.stevanovicm32.mobilnoracunarstvo.data.dto.NearbyDropDto
import com.stevanovicm32.mobilnoracunarstvo.util.MapLatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val defaultLocation = MapLatLng(44.816, 20.460)

@Composable
fun OsmMap(
    userLocation: MapLatLng?,
    heatmapCells: List<HeatmapCellDto>,
    nearbyDrops: List<NearbyDropDto>,
    onCameraIdle: (CameraBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var idleJob by remember { mutableStateOf<Job?>(null) }

    fun notifyCameraIdle(view: MapView) {
        val bounds = view.boundingBox
        onCameraIdle(
            CameraBounds(
                minLat = bounds.latSouth,
                minLng = bounds.lonWest,
                maxLat = bounds.latNorth,
                maxLng = bounds.lonEast,
            ),
        )
    }

    fun scheduleCameraIdle(view: MapView) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(350)
            notifyCameraIdle(view)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                val start = userLocation ?: defaultLocation
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(start.latitude, start.longitude))

                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                    enableMyLocation()
                }
                overlays.add(locationOverlay)

                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            scheduleCameraIdle(this@apply)
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            scheduleCameraIdle(this@apply)
                            return false
                        }
                    },
                )

                mapView = this
                post { notifyCameraIdle(this) }
            }
        },
        update = { view ->
            updateOverlays(view, heatmapCells, nearbyDrops)
        },
    )

    LaunchedEffect(userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }
}

private fun updateOverlays(
    mapView: MapView,
    heatmapCells: List<HeatmapCellDto>,
    nearbyDrops: List<NearbyDropDto>,
) {
    val locationOverlay = mapView.overlays.firstOrNull { it is MyLocationNewOverlay }
    mapView.overlays.clear()
    locationOverlay?.let { mapView.overlays.add(it) }

    heatmapCells.forEach { cell ->
        val alpha = (0.15f + (cell.count.coerceAtMost(10) * 0.05f)).coerceAtMost(0.6f)
        val polygon = Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(GeoPoint(cell.latitude, cell.longitude), 550.0)
            fillPaint.color = Color.argb((alpha * 255).toInt(), 76, 175, 80)
            outlinePaint.color = Color.TRANSPARENT
        }
        mapView.overlays.add(polygon)
    }

    nearbyDrops.forEach { drop ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(drop.latitude, drop.longitude)
            title = "Claimable drop"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
}
