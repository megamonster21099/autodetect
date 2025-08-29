package com.anmig.autodetect.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
    
    fun log(msg: Any?) {
        val message = msg?.toString() ?: "null"
        Log.d("AutoDetect", message)
        writeToFile(message)
    }

    fun removeLogs() {
        try {
            val context = appContext ?: return
            val fileName = "autodetect_logs.txt"
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d("AutoDetect", "Log file deleted.")
            } else {
                Log.d("AutoDetect", "Log file does not exist.")
            }
        } catch (e: Exception) {
            Log.e("AutoDetect", "Failed to delete log file: ${e.message}")
        }
    }

    private fun writeToFile(msg: String) {
        try {
            val context = appContext ?: return
            val fileName = "autodetect_logs.txt"
            val file = File(context.filesDir, fileName)
            val timestamp = dateFormat.format(Date())
            val timestampedMessage = "[$timestamp] $msg"
            file.appendText(timestampedMessage + "\n")
        } catch (e: Exception) {
            Log.e("AutoDetect", "Failed to write to log file: ${e.message}")
        }
    }

    fun readFromFile(): String {
        return try {
            val context = appContext ?: return "Logger not initialized"
            val fileName = "autodetect_logs.txt"
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.readText()
            } else {
                "Log file does not exist."
            }
        } catch (e: Exception) {
            Log.e("AutoDetect", "Failed to read log file: ${e.message}")
            "Error reading log file: ${e.message}"
        }
    }
}