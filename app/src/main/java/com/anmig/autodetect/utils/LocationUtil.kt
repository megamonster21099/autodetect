package com.anmig.autodetect.utils

import com.anmig.autodetect.models.LocationData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtil {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceTo(first: LocationData, other: LocationData): Double {
        return calculateDistance(
            first.latitude, first.longitude,
            other.latitude, other.longitude
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c * 1000
    }
}