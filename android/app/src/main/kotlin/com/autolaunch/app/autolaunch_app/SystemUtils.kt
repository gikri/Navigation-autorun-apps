package com.autolaunch.app.autolaunch_app

import android.os.Build
import java.lang.reflect.Method

object SystemUtils {
    fun isMiui(): Boolean {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get: Method = c.getMethod("get", String::class.java)
            val miuiVer = get.invoke(null, "ro.miui.ui.version.name") as String
            if (miuiVer.isNotEmpty()) return true
            val manufacturer = Build.MANUFACTURER.lowercase()
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
        } catch (_: Exception) {
            false
        }
    }
}


