package com.stevanovicm32.mobilnoracunarstvo.util

import android.location.Location

object DistanceUtils {
    const val CREATE_MIN_DISTANCE_M = 50f
    const val CLAIM_MAX_DISTANCE_M = 20f
    const val GPS_MIN_ACCURACY_M = 20f

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val from = Location("from").apply {
            latitude = lat1
            longitude = lng1
        }
        val to = Location("to").apply {
            latitude = lat2
            longitude = lng2
        }
        return from.distanceTo(to)
    }
}
