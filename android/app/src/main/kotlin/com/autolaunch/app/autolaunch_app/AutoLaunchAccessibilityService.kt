package com.autolaunch.app.autolaunch_app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoLaunchAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AutoLaunchAccessibilityService"
        
        @Volatile
        private var instance: AutoLaunchAccessibilityService? = null
        
        fun getInstance(): AutoLaunchAccessibilityService? = instance
        
        fun lockScreen(): Boolean {
            return try {
                val service = getInstance()
                if (service != null) {
                    val result = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    Log.d(TAG, "Lock screen via accessibility service: $result")
                    result
                } else {
                    Log.w(TAG, "Accessibility service not available")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error locking screen via accessibility service", e)
                false
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoLaunch Accessibility Service connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AutoLaunch Accessibility Service destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 이벤트 처리는 필요하지 않음 (화면 잠금 기능만 사용)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AutoLaunch Accessibility Service interrupted")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "AutoLaunch Accessibility Service unbound")
        return super.onUnbind(intent)
    }
} 