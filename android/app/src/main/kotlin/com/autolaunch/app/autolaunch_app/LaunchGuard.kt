package com.autolaunch.app.autolaunch_app

import android.content.Context

object LaunchGuard {
    private const val PREFS_NAME = "autolaunch_prefs"
    private const val KEY_LAST_UI_MS = "last_ui_ms"
    private const val KEY_IN_PROGRESS = "launch_in_progress"

    fun canShow(context: Context, minIntervalMs: Long = 15_000L): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UI_MS, 0L)
        val inProgress = prefs.getBoolean(KEY_IN_PROGRESS, false)
        val now = System.currentTimeMillis()
        if (inProgress) return false
        if (now - last < minIntervalMs) return false
        return true
    }

    fun markStart(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IN_PROGRESS, true)
            .putLong(KEY_LAST_UI_MS, System.currentTimeMillis())
            .apply()
    }

    fun markEnd(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IN_PROGRESS, false)
            .apply()
    }
}


