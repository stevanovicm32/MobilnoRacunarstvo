package com.stevanovicm32.mobilnoracunarstvo.domain

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationState(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMs: Long,
)

/**
 * Wraps [FusedLocationProviderClient] to deliver high-accuracy GPS updates.
 *
 * The fused provider merges GPS, Wi-Fi, and cell signals. [LocationState.accuracyMeters]
 * is the radius (in meters) within which the true position likely lies — lower is better.
 */
class LocationTracker(context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3_000L,
    ).apply {
        setMinUpdateIntervalMillis(2_000L)
        setMinUpdateDistanceMeters(2f)
    }.build()

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<LocationState> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    LocationState(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        timestampMs = location.time,
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper(),
        )

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }
}
