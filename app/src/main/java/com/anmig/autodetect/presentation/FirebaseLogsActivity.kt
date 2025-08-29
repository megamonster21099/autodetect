package com.anmig.autodetect.presentation

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anmig.autodetect.R
import com.anmig.autodetect.utils.FirebaseHelper
import com.anmig.autodetect.utils.Logger

class FirebaseLogsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FirebaseLogsActivity"
    }

    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnDeleteLogs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.log("$TAG: onCreate() called")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_firebase_logs)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Firebase Logs"
        
        tvLogs = findViewById(R.id.tv_firebase_logs)
        scrollView = findViewById(R.id.scroll_view_firebase_logs)
        btnDeleteLogs = findViewById(R.id.btn_delete_firebase_logs)
        
        setupDeleteButton()
        loadFirebaseLogs()
        Logger.log("$TAG: onCreate() completed")
    }

    override fun onSupportNavigateUp(): Boolean {
        Logger.log("$TAG: Back button pressed")
        finish()
        return true
    }

    private fun setupDeleteButton() {
        Logger.log("$TAG: setupDeleteButton() called")
        btnDeleteLogs.setOnClickListener {
            Logger.log("$TAG: Delete Firebase logs button clicked")
            deleteFirebaseLogs()
        }
    }

    private fun deleteFirebaseLogs() {
        Logger.log("$TAG: deleteFirebaseLogs() called")
        val firebaseHelper = FirebaseHelper()
        
        firebaseHelper.deleteLogsFromFirebase { success ->
            runOnUiThread {
                if (success) {
                    Logger.log("$TAG: Firebase logs deleted successfully")
                    tvLogs.text = getString(R.string.firebase_logs_deleted)
                    Toast.makeText(this, getString(R.string.firebase_logs_deleted), Toast.LENGTH_SHORT).show()
                } else {
                    Logger.log("$TAG: Failed to delete Firebase logs")
                    Toast.makeText(this, getString(R.string.firebase_logs_delete_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadFirebaseLogs() {
        Logger.log("$TAG: loadFirebaseLogs() called")
        tvLogs.text = getString(R.string.loading_firebase_logs)
        
        val firebaseHelper = FirebaseHelper()
        firebaseHelper.getLogsFromFirebase { logContent ->
            runOnUiThread {
                when {
                    logContent != null && logContent.isNotEmpty() -> {
                        Logger.log("$TAG: Successfully loaded Firebase logs, ${logContent.length} characters")
                        tvLogs.text = logContent
                        
                        // Scroll to bottom to show latest logs
                        scrollView.post {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                    else -> {
                        Logger.log("$TAG: No logs found in Firebase")
                        tvLogs.text = getString(R.string.no_firebase_logs)
                    }
                }
            }
        }
    }
}