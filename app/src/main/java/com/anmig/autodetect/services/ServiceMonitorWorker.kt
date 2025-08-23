package com.anmig.autodetect.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anmig.autodetect.models.AppMode
import com.anmig.autodetect.utils.ModePreferences

class ServiceMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modePreferences = ModePreferences(applicationContext)

        if (modePreferences.getMode() == AppMode.TARGET && !isServiceRunning()) {
            val intent = Intent(applicationContext, LocationService::class.java)
            applicationContext.startForegroundService(intent)
        }

        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (LocationService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}