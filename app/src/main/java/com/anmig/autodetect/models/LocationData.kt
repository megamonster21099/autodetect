package com.anmig.autodetect.models

import com.google.gson.Gson

data class LocationData(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
) {
    companion object {
        fun fromJson(json: String): LocationData? {
            return try {
                Gson().fromJson(json, LocationData::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return Gson().toJson(this)
    }
}