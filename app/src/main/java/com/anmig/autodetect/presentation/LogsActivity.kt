package com.anmig.autodetect.presentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.anmig.autodetect.R
import com.anmig.autodetect.utils.Logger

class LogsActivity : AppCompatActivity() {
    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnClearLogs: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var refreshRunnable: Runnable
    private val refreshInterval = 1000L // 1 second
    
    private var lastLogContent = ""
    private var shouldAutoScroll = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Application Logs"
        
        tvLogs = findViewById(R.id.tv_logs)
        scrollView = findViewById(R.id.scroll_view_logs)
        btnClearLogs = findViewById(R.id.btn_clear_logs)

        btnClearLogs.setOnClickListener {
            clearLogs()
        }
        
        setupScrollChangeDetection()
        setupAutoRefresh()
        loadLogs()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
    }

    private fun setupAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadLogs()
                handler.postDelayed(this, refreshInterval)
            }
        }
    }

    private fun startAutoRefresh() {
        handler.post(refreshRunnable)
    }

    private fun stopAutoRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun setupScrollChangeDetection() {
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Check if user is at the bottom of the scroll view
            val diff = (scrollView.getChildAt(0).bottom - (scrollView.height + scrollY))
            shouldAutoScroll = diff <= 10 // Allow small margin for rounding errors
        }
    }

    private fun clearLogs() {
        try {
            Logger.removeLogs()
            tvLogs.text = getString(R.string.logs_cleared)
            lastLogContent = getString(R.string.logs_cleared)
            shouldAutoScroll = true // Reset auto-scroll after clearing
        } catch (e: Exception) {
            //
        }
    }

    private fun loadLogs() {
        try {
            val logs = Logger.readFromFile()
            
            // Only update if content has changed
            if (logs != lastLogContent) {
                tvLogs.text = logs
                lastLogContent = logs
                
                // Only auto-scroll if user is at the bottom or it's the initial load
                if (shouldAutoScroll) {
                    scrollView.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to load logs: ${e.message}"
            tvLogs.text = errorMsg
        }
    }
}