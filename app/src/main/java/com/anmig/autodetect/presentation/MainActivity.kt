package com.anmig.autodetect.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.anmig.autodetect.utils.ModePreferences
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var modePreferences: ModePreferences

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startTargetMode()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private lateinit var tvModeStatus: TextView
    private lateinit var btnShowAsList: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvModeStatus = findViewById(R.id.tv_mode_status)
        btnShowAsList = findViewById(R.id.btn_show_as_list)

        modePreferences = ModePreferences(this)

        if (!modePreferences.isModeSet()) {
            showModeSelectionDialog()
        } else {
            checkMode()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_mode -> {
                showModeSelectionDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showModeSelectionDialog() {
        val modes = arrayOf(AppMode.CLIENT.name, AppMode.TARGET.name)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_app_mode))
            .setItems(modes) { _, which ->
                val selectedMode = if (which == 0) AppMode.CLIENT else AppMode.TARGET
                modePreferences.saveMode(selectedMode)
                checkMode()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkPermissions(): Boolean {
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

        if (permissions.isNotEmpty()) {
            locationPermissionLauncher.launch(permissions.toTypedArray())
            return false
        } else {
            return true
        }
    }

    private fun checkMode() {
        when (modePreferences.getMode()) {
            AppMode.TARGET -> {
                if (checkPermissions()) {
                    startTargetMode()
                }
            }

            AppMode.CLIENT -> startClientMode()
            null -> showModeSelectionDialog()
        }
    }

    private fun startTargetMode() {
        tvModeStatus.isVisible = false
        btnShowAsList.isVisible = false
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
        scheduleServiceMonitoring()
    }

    private fun startClientMode() {
        tvModeStatus.isVisible = false
        btnShowAsList.isVisible = true
        btnShowAsList.setOnClickListener {
            val intent = Intent(this, LocationListActivity::class.java)
            startActivity(intent)
        }
        stopService(Intent(this, LocationService::class.java))
        stopServiceMonitoring()
    }

    private fun scheduleServiceMonitoring() {
        val workRequest = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "service_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun stopServiceMonitoring() {
        WorkManager.getInstance(this).cancelUniqueWork("service_monitor")
    }
}