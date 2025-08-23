package com.anmig.autodetect.utils

import com.anmig.autodetect.models.EncryptedLocationData
import com.anmig.autodetect.EncryptionKeyProvider
import com.anmig.autodetect.models.LocationData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseHelper {
    companion object {
        private const val MAX_LOCATIONS = 1000
        private const val MINIMUM_DISTANCE_METERS = 10.0
    }

    private val database = FirebaseDatabase.getInstance()
    private val locationsRef = database.getReference("locations")
    private val xorDecoder = XorDecoder()
    private val encryptionKey = EncryptionKeyProvider.ENCRYPTION_KEY

    fun saveLocationOptimized(newLocation: LocationData, callback: (Boolean) -> Unit = {}) {
        locationsRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // Check if we have any existing locations
                    if (dataSnapshot.exists()) {
                        // Get the single most recent location (since we used limitToLast(1))
                        val lastSnapshot = dataSnapshot.children.last()
                        val encryptedLocation =
                            lastSnapshot.getValue(EncryptedLocationData::class.java)
                        val lastLocationId = lastSnapshot.key

                        if (encryptedLocation != null && lastLocationId != null) {
                            val decryptedJson =
                                xorDecoder.decode(encryptedLocation.encryptedData, encryptionKey)
                            val lastLocation = LocationData.Companion.fromJson(decryptedJson)

                            if (lastLocation != null) {
                                val distance = LocationUtil.distanceTo(newLocation, lastLocation)

                                if (distance < MINIMUM_DISTANCE_METERS) {
                                    // Distance is less than 10 meters, just update timestamp of existing record
                                    val updatedLocation = lastLocation.copy(
                                        timestamp = newLocation.timestamp
                                    )
                                    val updatedJson = updatedLocation.toJson()
                                    val updatedEncryptedData =
                                        xorDecoder.encode(updatedJson, encryptionKey)
                                    val updatedEncryptedLocation = EncryptedLocationData(
                                        id = lastLocationId,
                                        encryptedData = updatedEncryptedData,
                                        timestamp = newLocation.timestamp
                                    )
                                    locationsRef.child(lastLocationId)
                                        .setValue(updatedEncryptedLocation)
                                        .addOnCompleteListener { task ->
                                            callback(task.isSuccessful)
                                        }
                                    return
                                }
                            }
                        }
                    }

                    checkAndMaintainLocationLimit {
                        // Save the new location
                        val key = locationsRef.push().key
                        if (key != null) {
                            val locationWithId = newLocation.copy(id = key)
                            val json = locationWithId.toJson()
                            val encryptedData = xorDecoder.encode(json, encryptionKey)
                            val encryptedLocation = EncryptedLocationData(
                                id = key,
                                encryptedData = encryptedData,
                                timestamp = newLocation.timestamp
                            )
                            locationsRef.child(key).setValue(encryptedLocation)
                                .addOnCompleteListener { task ->
                                    callback(task.isSuccessful)
                                }
                        } else {
                            callback(false)
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    callback(false)
                }
            })
    }

    private fun checkAndMaintainLocationLimit(onComplete: () -> Unit) {
        // Check total count and remove old records if necessary
        locationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val locationCount = dataSnapshot.childrenCount

                if (locationCount >= MAX_LOCATIONS) {
                    val recordsToRemove = (locationCount - MAX_LOCATIONS + 1).toInt()

                    locationsRef.orderByChild("timestamp").limitToFirst(recordsToRemove)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(oldestSnapshot: DataSnapshot) {
                                var removalCount = 0
                                val totalRemovals = oldestSnapshot.childrenCount.toInt()

                                if (totalRemovals == 0) {
                                    onComplete()
                                    return
                                }

                                for (snapshot in oldestSnapshot.children) {
                                    snapshot.key?.let { key ->
                                        locationsRef.child(key).removeValue()
                                            .addOnCompleteListener {
                                                removalCount++
                                                if (removalCount >= totalRemovals) {
                                                    onComplete()
                                                }
                                            }
                                    }
                                }
                            }

                            override fun onCancelled(databaseError: DatabaseError) {
                                onComplete()
                            }
                        })
                } else {
                    onComplete()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                onComplete()
            }
        })
    }

    fun getAllLocations(callback: (List<LocationData>) -> Unit) {
        locationsRef.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val locations = mutableListOf<LocationData>()
                    for (snapshot in dataSnapshot.children) {
                        val encryptedLocation = snapshot.getValue(EncryptedLocationData::class.java)
                        encryptedLocation?.let { encrypted ->
                            val decryptedJson =
                                xorDecoder.decode(encrypted.encryptedData, encryptionKey)
                            val location = LocationData.Companion.fromJson(decryptedJson)
                            location?.let {
                                val locationWithId = if (it.id.isEmpty()) {
                                    it.copy(id = snapshot.key ?: "")
                                } else {
                                    it
                                }
                                locations.add(locationWithId)
                            }
                        }
                    }
                    // Sort by timestamp descending (newest first)
                    locations.sortByDescending { it.timestamp }
                    callback(locations)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    callback(emptyList())
                }
            })
    }
}