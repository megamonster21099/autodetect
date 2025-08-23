package com.anmig.autodetect.models

data class EncryptedLocationData(
    val id: String = "",
    val encryptedData: List<Int> = emptyList(),
    val timestamp: Long = 0L
)