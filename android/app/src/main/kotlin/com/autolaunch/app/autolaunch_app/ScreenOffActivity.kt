package com.autolaunch.app.autolaunch_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager

class ScreenOffActivity : Activity() {
    
    companion object {
        private const val TAG = "ScreenOffActivity"
        
        fun startScreenOff(context: Context) {
            val intent = Intent(context, ScreenOffActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🔥🔥🔥 ScreenOffActivity created - STARTING AGGRESSIVE SCREEN OFF! 🔥🔥🔥")
        
        // 🔥 즉시 다중 방식 화면 끄기 시도
        turnOffScreen()
        
        // 🔥 500ms 후 추가 시도
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔥 Secondary screen off attempt")
            turnOffScreenAlternative()
        }, 500)
        
        // 🔥 1초 후 마지막 시도
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔥 Final screen off attempt")
            turnOffScreenLastResort()
        }, 1000)
        
        // 🔥 1.5초 후 자동 종료
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔥 ScreenOffActivity finishing")
            finish()
        }, 1500)
    }
    
    private fun turnOffScreen() {
        try {
            Log.d(TAG, "Starting comprehensive screen off sequence")
            
            // 1. 디바이스 관리자 권한으로 화면 끄기 시도 (최우선)
            if (tryDeviceAdminScreenOff()) {
                Log.d(TAG, "Screen off via device admin successful")
                return
            }
            
            // 2. 접근성 서비스로 화면 끄기 시도 (두 번째 우선순위)
            if (tryAccessibilityServiceScreenOff()) {
                Log.d(TAG, "Screen off via accessibility service successful")
                return
            }
            
            // 3. 대안 방법들 (우선순위 낮음)
            Log.d(TAG, "Using alternative screen off methods")
            
            // 3-1. 화면 밝기를 최소로 설정
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 0.0f
            window.attributes = layoutParams
            Log.d(TAG, "Screen brightness set to minimum")
            
            // 3-2. 화면 플래그 설정
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 3-3. PowerManager를 통한 화면 끄기 시도
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // WakeLock 방식
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK,
                "AutoLaunch::ScreenOffLock"
            )
            
            // 매우 짧은 시간 동안 WakeLock을 획득했다가 해제
            wakeLock?.acquire(100)
            
            Handler(Looper.getMainLooper()).postDelayed({
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "WakeLock released for screen off")
                    }
                }
            }, 50)
            
            // 3-4. 시스템 화면 끄기 브로드캐스트 시도
            try {
                val screenOffIntent = Intent(Intent.ACTION_SCREEN_OFF)
                sendBroadcast(screenOffIntent)
                Log.d(TAG, "Screen off broadcast sent")
            } catch (e: Exception) {
                Log.w(TAG, "Screen off broadcast failed", e)
            }
            
            // 3-5. 화면 보호기 활성화 시도
            try {
                val screenSaverIntent = Intent("android.intent.action.SCREEN_SAVER")
                screenSaverIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(screenSaverIntent)
                Log.d(TAG, "Screen saver activated")
            } catch (e: Exception) {
                Log.w(TAG, "Screen saver activation failed", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off screen", e)
            // 최종 대안: 홈 화면으로 이동
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)
                Log.d(TAG, "Emergency fallback to home screen")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Emergency fallback failed", fallbackError)
            }
        }
    }
    
    private fun tryDeviceAdminScreenOff(): Boolean {
        return try {
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
            
            if (devicePolicyManager.isAdminActive(componentName)) {
                devicePolicyManager.lockNow()
                Log.d(TAG, "Device admin screen lock executed")
                true
            } else {
                Log.d(TAG, "Device admin not active")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device admin screen off failed", e)
            false
        }
    }
    
    private fun tryAccessibilityServiceScreenOff(): Boolean {
        return try {
            val result = AutoLaunchAccessibilityService.lockScreen()
            if (result) {
                Log.d(TAG, "Accessibility service screen lock executed")
            } else {
                Log.d(TAG, "Accessibility service not available or failed")
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility service screen off failed", e)
            false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released in onDestroy")
            }
        }
        
        Log.d(TAG, "ScreenOffActivity destroyed")
    }
    
    // 🔥 추가 화면 끄기 방법들
    private fun turnOffScreenAlternative() {
        try {
            Log.d(TAG, "🔥 Alternative screen off methods")
            
            // 방법 1: AccessibilityService로 다시 시도
            if (tryAccessibilityServiceScreenOff()) {
                Log.d(TAG, "🔥 Alternative: Accessibility service successful")
                return
            }
            
            // 방법 2: DeviceAdmin으로 다시 시도
            if (tryDeviceAdminScreenOff()) {
                Log.d(TAG, "🔥 Alternative: Device admin successful")
                return
            }
            
            // 방법 3: 화면 밝기 0으로 설정하고 검은색 화면 표시
            setContentView(android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            })
            
            window.attributes = window.attributes.apply {
                screenBrightness = 0.0f
                flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
            
            Log.d(TAG, "🔥 Alternative: Set black screen with zero brightness")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error in alternative screen off", e)
        }
    }
    
    private fun turnOffScreenLastResort() {
        try {
            Log.d(TAG, "🔥 LAST RESORT screen off methods")
            
            // 최종 방법 1: 시스템 브로드캐스트 시도
            try {
                val powerOffIntent = Intent("android.intent.action.SCREEN_OFF")
                sendBroadcast(powerOffIntent)
                Log.d(TAG, "🔥 Last resort: Sent SCREEN_OFF broadcast")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 SCREEN_OFF broadcast failed", e)
            }
            
            // 최종 방법 2: 잠금 화면 활성화 브로드캐스트
            try {
                val lockIntent = Intent("android.intent.action.USER_PRESENT")
                sendBroadcast(lockIntent)
                Log.d(TAG, "🔥 Last resort: Sent USER_PRESENT broadcast")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 USER_PRESENT broadcast failed", e)
            }
            
            // 최종 방법 3: 화면을 완전히 검은색으로 만들고 터치 차단
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            
            // 검은색 오버레이 뷰 생성
            val blackOverlay = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            setContentView(blackOverlay)
            
            window.attributes = window.attributes.apply {
                screenBrightness = 0.0f
            }
            
            Log.d(TAG, "🔥 Last resort: Black overlay applied")
            
            // 최종 방법 4: 2초 후 액티비티 완전 종료
            Handler(Looper.getMainLooper()).postDelayed({
                finishAffinity()
                Log.d(TAG, "🔥 Last resort: Activity finished completely")
            }, 2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 LAST RESORT also failed!", e)
        }
    }
} 