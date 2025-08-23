package com.anmig.autodetect.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.anmig.autodetect.models.LocationData
import com.anmig.autodetect.R
import com.anmig.autodetect.utils.FirebaseHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

class LocationService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_service_channel"
        private const val LOCATION_INTERVAL = 30_000L
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var isLocationUpdatesStarted = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firebaseHelper = FirebaseHelper()
        createNotificationChannel()
        setupLocationRequest()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running location tracking service"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(".")
            .setContentText(".")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

//    private fun updateNotification(text: String) {
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(".")
//            .setContentText(text)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setOngoing(true)
//            .build()
//
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        notificationManager.notify(NOTIFICATION_ID, notification)
//    }

    private fun setupLocationRequest() {
        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL / 2)
                .setMaxUpdateDelayMillis(LOCATION_INTERVAL * 2)
                .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    firebaseHelper.saveLocationOptimized(locationData) { success ->
                        if (success) {
                            //updateNotification("Location encrypted & saved: ${location.latitude}, ${location.longitude}")
                            Log.d("LocationService", "Location encrypted and saved successfully")
                        } else {
                            Log.e("LocationService", "Failed to save encrypted location")
                        }
                    }
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (isLocationUpdatesStarted) {
            return
        }
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesStarted = true
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesStarted = false
    }
}