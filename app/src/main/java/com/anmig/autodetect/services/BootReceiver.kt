package com.anmig.autodetect.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anmig.autodetect.models.AppMode
import com.anmig.autodetect.utils.ModePreferences
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val modePreferences = ModePreferences(context)
            
            if (modePreferences.getMode() == AppMode.TARGET) {
                val serviceIntent = Intent(context, LocationService::class.java)
                context.startForegroundService(serviceIntent)
                scheduleServiceMonitoring(context)
            }
        }
    }
    
    private fun scheduleServiceMonitoring(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(15, TimeUnit.MINUTES)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "service_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}