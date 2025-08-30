package com.anmig.autodetect.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anmig.autodetect.R
import com.anmig.autodetect.models.AppMode
import com.anmig.autodetect.services.LocationService
import com.anmig.autodetect.services.ServiceMonitorWorker
import com.anmig.autodetect.utils.FirebaseHelper
import com.anmig.autodetect.utils.Logger
import com.anmig.autodetect.utils.ModePreferences
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var modePreferences: ModePreferences
    private var shouldCloseAsap = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Logger.log("$TAG: Permission result received")
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        Logger.log("$TAG: Fine location: $fineLocationGranted, Coarse location: $coarseLocationGranted")

        if (fineLocationGranted || coarseLocationGranted) {
            Logger.log("$TAG: Location permissions granted, starting target mode")
            startTargetMode()
        } else {
            Logger.log("$TAG: Location permissions denied")
            Toast.makeText(
                this,
                getString(R.string.location_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private lateinit var tvModeStatus: TextView
    private lateinit var btnShowAsList: Button
    private lateinit var viewStarter: View

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.initialize(this)
        Logger.log("\n\n\n$TAG: onCreate() called")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setDisplayShowTitleEnabled(false);
        tvModeStatus = findViewById(R.id.tv_mode_status)
        btnShowAsList = findViewById(R.id.btn_show_as_list)
        viewStarter = findViewById(R.id.view_starter)

        modePreferences = ModePreferences(this)
        val modeSet = modePreferences.isModeSet()
        Logger.log("$TAG: Mode set: $modeSet")

        if (!modeSet) {
            Logger.log("$TAG: No mode set, showing selection dialog")
            showModeSelectionDialog()
        } else {
            Logger.log("$TAG: Mode already set, checking current mode")
            checkMode()
        }
        Logger.log("$TAG: onCreate() completed")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Logger.log("$TAG: onCreateOptionsMenu() called")
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Logger.log("$TAG: onOptionsItemSelected() called with item: ${item.title}")
        return when (item.itemId) {
            R.id.action_change_mode -> {
                Logger.log("$TAG: Change mode option selected")
                showModeSelectionDialog()
                true
            }

            R.id.action_view_logs -> {
                Logger.log("$TAG: View logs option selected")
                val intent = Intent(this, LogsActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.action_send_logs -> {
                Logger.log("$TAG: Send logs option selected")
                sendLogsToFirebase()
                true
            }

            R.id.action_get_logs -> {
                Logger.log("$TAG: Get logs option selected")
                val intent = Intent(this, FirebaseLogsActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showModeSelectionDialog() {
        Logger.log("$TAG: showModeSelectionDialog() called")
        val modes = arrayOf(AppMode.CLIENT.name, AppMode.TARGET.name)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_app_mode))
            .setItems(modes) { _, which ->
                val selectedMode = if (which == 0) AppMode.CLIENT else AppMode.TARGET
                Logger.log("$TAG: Mode selected: $selectedMode")
                modePreferences.saveMode(selectedMode)
                checkMode()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPermissions(): Boolean {
        Logger.log("$TAG: checkPermissions() called")
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        Logger.log("$TAG: Missing permissions: ${permissions.joinToString(", ")}")
        if (permissions.isNotEmpty()) {
            Logger.log("$TAG: Requesting ${permissions.size} permissions")
            locationPermissionLauncher.launch(permissions.toTypedArray())
            return false
        } else {
            Logger.log("$TAG: All permissions already granted")
            return true
        }
    }

    private fun checkMode() {
        val currentMode = modePreferences.getMode()
        Logger.log("$TAG: checkMode() called - current mode: $currentMode")
        when (currentMode) {
            AppMode.TARGET -> {
                Logger.log("$TAG: Target mode selected, checking permissions")
                shouldCloseAsap = true
                if (checkPermissions()) {
                    viewStarter.setOnClickListener {
                        shouldCloseAsap = false
                    }
                    startTargetMode()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (shouldCloseAsap) finish()
                    }, 500)
                }
            }

            AppMode.CLIENT -> {
                Logger.log("$TAG: Client mode selected")
                startClientMode()
            }

            null -> {
                Logger.log("$TAG: No mode selected, showing dialog")
                showModeSelectionDialog()
            }
        }
    }

    private fun startTargetMode() {
        Logger.log("$TAG: startTargetMode() called")
        tvModeStatus.isVisible = false
        btnShowAsList.isVisible = false
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
        scheduleServiceMonitoring()
        Logger.log("$TAG: Target mode started - LocationService launched and monitoring scheduled")
    }

    private fun startClientMode() {
        Logger.log("$TAG: startClientMode() called")
        tvModeStatus.isVisible = false
        btnShowAsList.isVisible = true
        btnShowAsList.setOnClickListener {
            Logger.log("$TAG: Show as list button clicked")
            val intent = Intent(this, LocationListActivity::class.java)
            startActivity(intent)
        }
        stopService(Intent(this, LocationService::class.java))
        stopServiceMonitoring()
        Logger.log("$TAG: Client mode started - LocationService stopped and monitoring cancelled")
    }

    private fun scheduleServiceMonitoring() {
        Logger.log("$TAG: scheduleServiceMonitoring() called")
        val workRequest = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "service_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Logger.log("$TAG: Service monitoring scheduled with 15-minute intervals")
    }

    private fun stopServiceMonitoring() {
        Logger.log("$TAG: stopServiceMonitoring() called")
        WorkManager.getInstance(this).cancelUniqueWork("service_monitor")
        Logger.log("$TAG: Service monitoring cancelled")
    }

    private fun sendLogsToFirebase() {
        Logger.log("$TAG: sendLogsToFirebase() called")

        // Show loading toast
        Toast.makeText(this, getString(R.string.sending_logs), Toast.LENGTH_SHORT).show()

        val firebaseHelper = FirebaseHelper()
        firebaseHelper.loadLogsToFirebase { success ->
            runOnUiThread {
                if (success) {
                    Logger.log("$TAG: Logs sent to Firebase successfully")
                    Toast.makeText(
                        this,
                        getString(R.string.logs_sent_successfully),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Logger.log("$TAG: Failed to send logs to Firebase")
                    Toast.makeText(this, getString(R.string.logs_send_failed), Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}