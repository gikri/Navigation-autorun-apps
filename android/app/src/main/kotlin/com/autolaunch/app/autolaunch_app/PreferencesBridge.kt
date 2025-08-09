package com.autolaunch.app.autolaunch_app

import android.content.Context
import android.util.Log

object PreferencesBridge {
    private const val TAG = "PreferencesBridge"

    private const val FLUTTER_PREFS = "FlutterSharedPreferences"
    private const val AUTO_PREFS = "autolaunch_prefs"
    private const val SETTINGS_PREFS = "settings"

    private const val KEY_TARGET_APP = "target_app"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    // Flutter SharedPreferences keys are prefixed with 'flutter.'
    private const val FLUTTER_KEY_PREFIX = "flutter."

    data class Values(
        val serviceEnabled: Boolean,
        val targetApp: String?
    )

    fun readValues(context: Context): Values {
        try {
            val auto = context.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE)
            val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val flutter = context.getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)

            // Read candidates from all stores
            val serviceFromAuto = auto.getBoolean(KEY_SERVICE_ENABLED, false)
            val serviceFromSettings = settings.getBoolean(KEY_SERVICE_ENABLED, false)
            val serviceFromFlutter = flutter.getBoolean(FLUTTER_KEY_PREFIX + KEY_SERVICE_ENABLED, false)

            val targetFromAuto = auto.getString(KEY_TARGET_APP, null)
            val targetFromSettings = settings.getString(KEY_TARGET_APP, null)
            val targetFromFlutter = flutter.getString(FLUTTER_KEY_PREFIX + KEY_TARGET_APP, null)

            // Merge policy: prefer Flutter → autolaunch_prefs → settings
            val mergedService = serviceFromFlutter || serviceFromAuto || serviceFromSettings
            val mergedTarget = targetFromFlutter ?: targetFromAuto ?: targetFromSettings

            // Sync back into autolaunch_prefs for native-only paths
            auto.edit()
                .putBoolean(KEY_SERVICE_ENABLED, mergedService)
                .apply()
            if (mergedTarget != null) {
                auto.edit().putString(KEY_TARGET_APP, mergedTarget).apply()
            }

            Log.d(TAG, "Merged values → enabled=$mergedService, target=$mergedTarget")
            return Values(mergedService, mergedTarget)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading preferences", e)
            return Values(false, null)
        }
    }
}


