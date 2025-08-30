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
        private const val TAG = "FirebaseHelper"
        private const val MAX_LOCATIONS = 1000
        private const val MINIMUM_DISTANCE_METERS = 10.0
    }

    private val database = FirebaseDatabase.getInstance()
    private val locationsRef = database.getReference("locations")
    private val xorDecoder = XorDecoder()
    private val encryptionKey = EncryptionKeyProvider.ENCRYPTION_KEY

    fun saveLocationOptimized(newLocation: LocationData, callback: (Boolean) -> Unit = {}) {
        Logger.log("$TAG: saveLocationOptimized() called - Lat: ${newLocation.latitude}, Lng: ${newLocation.longitude}")
        locationsRef.orderByChild("timestamp").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    Logger.log("$TAG: onDataChange() called in saveLocationOptimized")
                    // Check if we have any existing locations
                    if (dataSnapshot.exists()) {
                        Logger.log("$TAG: Found existing location data")
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
                                Logger.log("$TAG: Distance to last location: ${distance}m")

                                if (distance < MINIMUM_DISTANCE_METERS) {
                                    Logger.log("$TAG: Distance < ${MINIMUM_DISTANCE_METERS}m, updating timestamp only")
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
                                            Logger.log("$TAG: Timestamp update completed - success: ${task.isSuccessful}")
                                            callback(task.isSuccessful)
                                        }
                                    return
                                }
                            }
                        }
                    } else {
                        Logger.log("$TAG: No existing location data found")
                    }

                    Logger.log("$TAG: Proceeding to save new location")
                    checkAndMaintainLocationLimit {
                        // Save the new location
                        val key = locationsRef.push().key
                        Logger.log("$TAG: Generated new key: $key")
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
                                    Logger.log("$TAG: New location save completed - success: ${task.isSuccessful}")
                                    callback(task.isSuccessful)
                                }
                        } else {
                            Logger.log("$TAG: Failed to generate key for new location")
                            callback(false)
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Logger.log("$TAG: Database error in saveLocationOptimized: ${databaseError.message}")
                    callback(false)
                }
            })
    }

    private fun checkAndMaintainLocationLimit(onComplete: () -> Unit) {
        Logger.log("$TAG: checkAndMaintainLocationLimit() called")
        // Check total count and remove old records if necessary
        locationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val locationCount = dataSnapshot.childrenCount
                Logger.log("$TAG: Current location count: $locationCount")

                if (locationCount >= MAX_LOCATIONS) {
                    Logger.log("$TAG: Location count >= $MAX_LOCATIONS, removing old records")
                    val recordsToRemove = (locationCount - MAX_LOCATIONS + 1).toInt()

                    locationsRef.orderByChild("timestamp").limitToFirst(recordsToRemove)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(oldestSnapshot: DataSnapshot) {
                                var removalCount = 0
                                val totalRemovals = oldestSnapshot.childrenCount.toInt()
                                Logger.log("$TAG: Removing $totalRemovals old location records")

                                if (totalRemovals == 0) {
                                    Logger.log("$TAG: No records to remove")
                                    onComplete()
                                    return
                                }

                                for (snapshot in oldestSnapshot.children) {
                                    snapshot.key?.let { key ->
                                        locationsRef.child(key).removeValue()
                                            .addOnCompleteListener {
                                                removalCount++
                                                Logger.log("$TAG: Removed old record $removalCount/$totalRemovals")
                                                if (removalCount >= totalRemovals) {
                                                    Logger.log("$TAG: All old records removed successfully")
                                                    onComplete()
                                                }
                                            }
                                    }
                                }
                            }

                            override fun onCancelled(databaseError: DatabaseError) {
                                Logger.log("$TAG: Database error during old record removal: ${databaseError.message}")
                                onComplete()
                            }
                        })
                } else {
                    Logger.log("$TAG: Location count within limit, no cleanup needed")
                    onComplete()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Logger.log("$TAG: Database error during location count check: ${databaseError.message}")
                onComplete()
            }
        })
    }

    fun getAllLocations(callback: (List<LocationData>) -> Unit) {
        Logger.log("$TAG: getAllLocations() called")
        locationsRef.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    Logger.log("$TAG: onDataChange() called in getAllLocations")
                    val locations = mutableListOf<LocationData>()
                    val totalSnapshots = dataSnapshot.childrenCount
                    Logger.log("$TAG: Processing $totalSnapshots location records")
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
                    Logger.log("$TAG: Successfully retrieved and processed ${locations.size} locations")
                    callback(locations)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Logger.log("$TAG: Database error in getAllLocations: ${databaseError.message}")
                    callback(emptyList())
                }
            })
    }

    fun loadLogsToFirebase(callback: (Boolean) -> Unit = {}) {
        Logger.log("$TAG: loadLogsToFirebase() called")
        try {
            val logContent = Logger.readFromFile()
            if (logContent.isEmpty() || logContent == "Log file does not exist.") {
                Logger.log("$TAG: No logs to upload")
                callback(false)
                return
            }

            val logsRef = database.getReference("logs")
            val timestamp = System.currentTimeMillis()
            val logKey = logsRef.push().key

            if (logKey != null) {
                Logger.log("$TAG: Generated log key: $logKey")
                
                // Encrypt the log content
                val encryptedLogData = xorDecoder.encode(logContent, encryptionKey)
                
                val logEntry = mapOf(
                    "id" to logKey,
                    "encryptedData" to encryptedLogData,
                    "timestamp" to timestamp,
                    "size" to logContent.length
                )

                logsRef.child(logKey).setValue(logEntry)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Logger.log("$TAG: Logs uploaded successfully to Firebase")
                            callback(true)
                        } else {
                            Logger.log("$TAG: Failed to upload logs: ${task.exception?.message}")
                            callback(false)
                        }
                    }
            } else {
                Logger.log("$TAG: Failed to generate key for log upload")
                callback(false)
            }
        } catch (e: Exception) {
            Logger.log("$TAG: Exception during log upload: ${e.message}")
            callback(false)
        }
    }

    fun getLogsFromFirebase(callback: (String?) -> Unit) {
        Logger.log("$TAG: getLogsFromFirebase() called")
        try {
            val logsRef = database.getReference("logs")
            
            // Get the most recent log entry
            logsRef.orderByChild("timestamp").limitToLast(1)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        Logger.log("$TAG: onDataChange() called in getLogsFromFirebase")
                        
                        if (dataSnapshot.exists()) {
                            Logger.log("$TAG: Found log entries in Firebase")
                            val latestLogSnapshot = dataSnapshot.children.last()
                            
                            try {
                                val logData = latestLogSnapshot.value as? Map<*, *>
                                val encryptedData = logData?.get("encryptedData")
                                val timestamp = logData?.get("timestamp") as? Long
                                
                                if (encryptedData != null) {
                                    Logger.log("$TAG: Decrypting log data from timestamp: $timestamp")
                                    
                                    // Handle different data types for encrypted data
                                    val decryptedContent = when (encryptedData) {
                                        is String -> {
                                            // If stored as string, convert back to List<Int>
                                            val intList = encryptedData.split(",").map { it.toInt() }
                                            xorDecoder.decode(intList, encryptionKey)
                                        }
                                        is List<*> -> {
                                            // If stored as List<Int> already
                                            @Suppress("UNCHECKED_CAST")
                                            xorDecoder.decode(encryptedData as List<Int>, encryptionKey)
                                        }
                                        else -> {
                                            Logger.log("$TAG: Unknown encrypted data format: ${encryptedData::class.java}")
                                            null
                                        }
                                    }
                                    
                                    if (decryptedContent != null) {
                                        Logger.log("$TAG: Successfully decrypted ${decryptedContent.length} characters")
                                        callback(decryptedContent)
                                    } else {
                                        Logger.log("$TAG: Failed to decrypt log data")
                                        callback(null)
                                    }
                                } else {
                                    Logger.log("$TAG: No encrypted data found in log entry")
                                    callback(null)
                                }
                            } catch (e: Exception) {
                                Logger.log("$TAG: Error processing log data: ${e.message}")
                                callback(null)
                            }
                        } else {
                            Logger.log("$TAG: No log entries found in Firebase")
                            callback(null)
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        Logger.log("$TAG: Database error in getLogsFromFirebase: ${databaseError.message}")
                        callback(null)
                    }
                })
        } catch (e: Exception) {
            Logger.log("$TAG: Exception during log retrieval: ${e.message}")
            callback(null)
        }
    }

    fun deleteLogsFromFirebase(callback: (Boolean) -> Unit = {}) {
        Logger.log("$TAG: deleteLogsFromFirebase() called")
        try {
            val logsRef = database.getReference("logs")
            
            logsRef.removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.log("$TAG: All logs deleted from Firebase successfully")
                        callback(true)
                    } else {
                        Logger.log("$TAG: Failed to delete logs from Firebase: ${task.exception?.message}")
                        callback(false)
                    }
                }
        } catch (e: Exception) {
            Logger.log("$TAG: Exception during log deletion: ${e.message}")
            callback(false)
        }
    }

    fun deleteAllLocations(callback: (Boolean) -> Unit = {}) {
        Logger.log("$TAG: deleteAllLocations() called")
        try {
            locationsRef.removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.log("$TAG: All locations deleted from Firebase successfully")
                        callback(true)
                    } else {
                        Logger.log("$TAG: Failed to delete locations from Firebase: ${task.exception?.message}")
                        callback(false)
                    }
                }
        } catch (e: Exception) {
            Logger.log("$TAG: Exception during location deletion: ${e.message}")
            callback(false)
        }
    }
}