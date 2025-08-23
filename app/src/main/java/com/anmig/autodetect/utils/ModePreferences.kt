package com.anmig.autodetect.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.anmig.autodetect.models.AppMode

class ModePreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveMode(mode: AppMode) {
        sharedPreferences.edit(commit = true) { putString(KEY_MODE, mode.name) }
    }

    fun getMode(): AppMode? {
        val modeName = sharedPreferences.getString(KEY_MODE, null)
        return if (modeName != null) {
            try {
                AppMode.valueOf(modeName)
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
    }

    fun isModeSet(): Boolean {
        return sharedPreferences.contains(KEY_MODE)
    }

    companion object {
        private const val PREFS_NAME = "autodetect_prefs"
        private const val KEY_MODE = "app_mode"
    }
}