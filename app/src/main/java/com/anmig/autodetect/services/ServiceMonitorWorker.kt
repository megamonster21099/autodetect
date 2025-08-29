package com.anmig.autodetect.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anmig.autodetect.models.AppMode
import com.anmig.autodetect.utils.Logger
import com.anmig.autodetect.utils.ModePreferences

class ServiceMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceMonitorWorker"
    }

    override suspend fun doWork(): Result {
        Logger.log("$TAG: doWork() started")
        val modePreferences = ModePreferences(applicationContext)
        val currentMode = modePreferences.getMode()
        val serviceRunning = isServiceRunning()

        Logger.log("$TAG: Current mode: $currentMode, Service running: $serviceRunning")

        if (currentMode == AppMode.TARGET && !serviceRunning) {
            Logger.log("$TAG: LocationService not running in TARGET mode, restarting...")
            val intent = Intent(applicationContext, LocationService::class.java)
            applicationContext.startForegroundService(intent)
            Logger.log("$TAG: LocationService restart initiated")
        } else if (currentMode == AppMode.TARGET) {
            Logger.log("$TAG: LocationService already running in TARGET mode")
        } else {
            Logger.log("$TAG: Not in TARGET mode, no action needed")
        }

        Logger.log("$TAG: doWork() completed successfully")
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        Logger.log("$TAG: isServiceRunning() called")
        return try {
            val activityManager =
                applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = activityManager.runningAppProcesses
            if (runningProcesses != null) {
                val myPackage = applicationContext.packageName
                val myProcess = runningProcesses.find { it.processName == myPackage }

                if (myProcess == null) {
                    Logger.log("$TAG: App process is not found")

                    @Suppress("DEPRECATION")
                    val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                    Logger.log("$TAG: Fallback check - examining ${runningServices.size} running services")

                    for (service in runningServices) {
                        if (LocationService::class.java.name == service.service.className) {
                            Logger.log("$TAG: LocationService found via deprecated API")
                            return true
                        }
                    }
                } else {
                    Logger.log("$TAG: App process not found in running processes")
                }
            } else {
                Logger.log("$TAG: Unable to get running processes")
            }

            Logger.log("$TAG: LocationService not found running")
            false
        } catch (e: Exception) {
            Logger.log("$TAG: Exception checking service status: ${e.message}")
            false
        }
    }
}