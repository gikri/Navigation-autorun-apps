package com.autolaunch.app.autolaunch_app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

class WakeUpActivity : Activity() {
    
    companion object {
        private const val TAG = "WakeUpActivity"
        const val EXTRA_TARGET_APP = "target_app"
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "WakeUpActivity created - DISABLED FOR STATUSACTIVITY")
        
        // WakeUpActivity는 StatusActivity로 대체되었으므로 즉시 종료
        finish()
    }
    
    private fun setupVisualUI() {
        try {
            // 전체 화면 설정
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            
            // 배경을 반투명하게 설정
            window.setBackgroundDrawableResource(android.R.color.transparent)
            
            // 레이아웃 설정
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#80000000")) // 반투명 검정
                
                // 로딩 텍스트 추가
                addView(android.widget.TextView(this@WakeUpActivity).apply {
                    text = "🚗 네비게이션 앱 실행 중..."
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    setPadding(50, 50, 50, 50)
                })
            }
            
            setContentView(layout)
            
            Log.d(TAG, "Visual UI setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up visual UI", e)
        }
    }
    
    private fun wakeUpAndUnlockScreen() {
        try {
            Log.d(TAG, "🔥🔥🔥 Starting ULTRA ENHANCED screen wake up process 🔥🔥🔥")
            
            // 0. 화면이 꺼져있으면 즉시 WakeLock 획득
            val powerManagerForWake = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManagerForWake.isInteractive) {
                wakeLock = powerManagerForWake.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE or
                    PowerManager.FULL_WAKE_LOCK or
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "AutoLaunch:WakeUpActivity"
                )
                wakeLock?.acquire(30000) // 30초
                Log.d(TAG, "🔥 Immediate WakeLock acquired for screen wake")
            }
            
            // 1. 화면 깨우기 플래그 설정 (최강화된 버전)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // 2. Android O 이상에서 추가 설정
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            
            // 3. 강화된 WakeLock 획득 (더 강력한 설정)
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                PowerManager.ON_AFTER_RELEASE or
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "AutoLaunch::WakeUpLock"
            )
            wakeLock?.acquire(60000) // 60초간 유지 (시간 증가)
            
            // 4. 키가드 해제 시도 (강화된 버전)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            Log.d(TAG, "Keyguard dismissed successfully")
                        }
                        override fun onDismissError() {
                            Log.w(TAG, "Keyguard dismiss failed")
                        }
                        override fun onDismissCancelled() {
                            Log.w(TAG, "Keyguard dismiss cancelled")
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting keyguard dismiss", e)
                }
            }
            
            // 5. 즉시 화면 깨우기 시도
            if (!powerManager.isInteractive) {
                Log.d(TAG, "Screen is off, attempting immediate wake up")
                wakeLock?.acquire(30000)
            }
            
            // 6. 추가적인 화면 깨우기 시도 (더 자주 시도)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // 화면이 여전히 꺼져있다면 다시 시도
                    if (!powerManager.isInteractive) {
                        Log.d(TAG, "Screen still off, retrying wake up")
                        wakeLock?.acquire(15000)
                        
                        // 추가 시도
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!powerManager.isInteractive) {
                                Log.d(TAG, "Final wake up attempt")
                                wakeLock?.acquire(10000)
                            }
                        }, 2000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in delayed wake up attempt", e)
                }
            }, 1000)
            
            Log.d(TAG, "Enhanced screen wake up and unlock completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error waking up screen", e)
        }
    }
    
    // launchTargetApp 메서드 제거 - StatusActivity만 사용
    // private fun launchTargetApp(packageName: String) { ... }
    // private fun verifyAndRetryAppLaunch(packageName: String, originalIntent: Intent) { ... }
    // private fun launchAppByAlternativeMethod(packageName: String) { ... }
    
    private fun launchAppByUrlScheme(packageName: String) {
        try {
            val urlScheme = when (packageName) {
                "com.skt.tmap.ku" -> "tmap://"
                "com.kakao.navi" -> "kakaonavi://"
                "net.daum.android.map" -> "daummaps://"
                "com.nhn.android.nmap" -> "nmap://"
                "com.google.android.apps.maps" -> "googlemaps://"
                "com.waze" -> "waze://"
                else -> null
            }
            
            if (urlScheme != null) {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(urlScheme))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "Successfully launched app via URL scheme: $urlScheme")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app via URL scheme", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        
        Log.d(TAG, "WakeUpActivity destroyed")
    }
} 