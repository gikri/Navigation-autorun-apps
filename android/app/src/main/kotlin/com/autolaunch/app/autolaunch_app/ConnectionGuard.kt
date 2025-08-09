package com.autolaunch.app.autolaunch_app

import android.content.Context

object ConnectionGuard {
    private const val PREFS_NAME = "autolaunch_prefs"
    private const val KEY_IS_CHARGING = "conn_is_charging"
    private const val KEY_CONNECTION_SEQ = "conn_seq"
    private const val KEY_HANDLED_SEQ = "conn_handled_seq"

    fun beginConnectionIfNew(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isCharging = prefs.getBoolean(KEY_IS_CHARGING, false)
        return if (!isCharging) {
            val seq = prefs.getInt(KEY_CONNECTION_SEQ, 0) + 1
            prefs.edit()
                .putBoolean(KEY_IS_CHARGING, true)
                .putInt(KEY_CONNECTION_SEQ, seq)
                .apply()
            seq
        } else {
            null
        }
    }

    fun endConnection(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_CHARGING, false)
            .apply()
    }

    fun shouldHandle(context: Context, seq: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val handled = prefs.getInt(KEY_HANDLED_SEQ, 0)
        return seq > handled
    }

    fun markHandled(context: Context, seq: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_HANDLED_SEQ, seq)
            .apply()
    }

    fun currentSeq(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CONNECTION_SEQ, 0)
    }

    fun isCharging(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CHARGING, false)
    }
}


