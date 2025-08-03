package com.autolaunch.app.autolaunch_app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {
    
    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled successfully")
        Log.d(TAG, "Context package: ${context.packageName}")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        
        Toast.makeText(context, "디바이스 관리자 권한이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device admin disabled")
        Log.d(TAG, "Context package: ${context.packageName}")
        Log.d(TAG, "Intent action: ${intent.action}")
        
        Toast.makeText(context, "디바이스 관리자 권한이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "DeviceAdminReceiver received intent: ${intent.action}")
        Log.d(TAG, "Intent data: ${intent.data}")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        
        when (intent.action) {
            ACTION_DEVICE_ADMIN_ENABLED -> {
                Log.d(TAG, "Device admin enabled action received")
            }
            ACTION_DEVICE_ADMIN_DISABLED -> {
                Log.d(TAG, "Device admin disabled action received")
            }
            else -> {
                Log.d(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }
    
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "Device admin disable requested")
        return "AutoLaunch 앱의 화면 제어 기능을 사용하려면 디바이스 관리자 권한이 필요합니다."
    }
} 