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
        
        Log.d(TAG, "WakeUpActivity created")
        
        // 화면 깨우기 및 잠금 해제
        wakeUpAndUnlockScreen()
        
        // 타겟 앱 실행
        val targetApp = intent.getStringExtra(EXTRA_TARGET_APP)
        if (targetApp != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                launchTargetApp(targetApp)
            }, 1000) // 1초 딜레이
        }
        
        // 2초 후 자동 종료
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }
    
    private fun wakeUpAndUnlockScreen() {
        try {
            Log.d(TAG, "Starting enhanced screen wake up process")
            
            // 1. 화면 깨우기 플래그 설정 (강화된 버전)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
            
            // 2. Android O 이상에서 추가 설정
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            
            // 3. 강화된 WakeLock 획득
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                PowerManager.ON_AFTER_RELEASE or
                PowerManager.FULL_WAKE_LOCK,
                "AutoLaunch::WakeUpLock"
            )
            wakeLock?.acquire(30000) // 30초간 유지 (시간 증가)
            
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
            
            // 5. 추가적인 화면 깨우기 시도
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // 화면이 여전히 꺼져있다면 다시 시도
                    if (!powerManager.isInteractive) {
                        Log.d(TAG, "Screen still off, retrying wake up")
                        wakeLock?.acquire(10000)
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
    
    private fun launchTargetApp(packageName: String) {
        try {
            Log.d(TAG, "Attempting to launch target app: $packageName")
            
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                
                // 화면이 꺼져있다면 추가 플래그 설정
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isInteractive) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                
                startActivity(intent)
                Log.d(TAG, "Successfully launched target app: $packageName")
                
                // 앱 실행 확인
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val runningTasks = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        val tasks = runningTasks.getRunningTasks(10)
                        val isAppRunning = tasks.any { it.topActivity?.packageName == packageName }
                        
                        if (isAppRunning) {
                            Log.d(TAG, "Target app is confirmed running: $packageName")
                        } else {
                            Log.w(TAG, "Target app may not be running, retrying: $packageName")
                            // 재시도
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking app running status", e)
                    }
                }, 2000)
                
            } else {
                Log.w(TAG, "No launch intent found for package: $packageName")
                // URL 스킴으로 시도
                launchAppByUrlScheme(packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching target app: $packageName", e)
            // 대안 방법 시도
            launchAppByAlternativeMethod(packageName)
        }
    }
    
    private fun launchAppByAlternativeMethod(packageName: String) {
        try {
            Log.d(TAG, "Trying alternative launch method for: $packageName")
            
            // 1. 직접 인텐트 생성
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.setPackage(packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            startActivity(intent)
            Log.d(TAG, "Alternative launch method completed for: $packageName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Alternative launch method failed for: $packageName", e)
        }
    }
    }
    
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