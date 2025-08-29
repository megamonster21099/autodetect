package com.anmig.autodetect.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anmig.autodetect.models.AppMode
import com.anmig.autodetect.utils.Logger
import com.anmig.autodetect.utils.ModePreferences
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.log("$TAG: onReceive() called with action: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.log("$TAG: Boot completed detected")
            val modePreferences = ModePreferences(context)
            val currentMode = modePreferences.getMode()
            Logger.log("$TAG: Current app mode: $currentMode")
            
            if (currentMode == AppMode.TARGET) {
                Logger.log("$TAG: Starting LocationService in TARGET mode")
                val serviceIntent = Intent(context, LocationService::class.java)
                context.startForegroundService(serviceIntent)
                scheduleServiceMonitoring(context)
                Logger.log("$TAG: LocationService started and monitoring scheduled")
            } else {
                Logger.log("$TAG: App not in TARGET mode, skipping service start")
            }
        } else {
            Logger.log("$TAG: Received non-boot intent: ${intent.action}")
        }
    }
    
    private fun scheduleServiceMonitoring(context: Context) {
        Logger.log("$TAG: scheduleServiceMonitoring() called")
        val workRequest = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(15, TimeUnit.MINUTES)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "service_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Logger.log("$TAG: Service monitoring scheduled with 15-minute intervals")
    }
}