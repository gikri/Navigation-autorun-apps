package com.autolaunch.app.autolaunch_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class StatusActivity : Activity() {
    
    companion object {
        private const val TAG = "StatusActivity"
        const val EXTRA_STATUS_TYPE = "status_type" // "launch" or "shutdown"
        const val EXTRA_TARGET_APP = "target_app"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        
        @Volatile
        private var isLaunching = false
    }
    
    private fun enableSlideToCancel() {
        try {
            val root = window.decorView.findViewById<android.view.View>(android.R.id.content)
            var downX = 0f
            var totalDx = 0f
            val threshold = 180f // 우측으로 충분히 슬라이드 시 취소
            root.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        totalDx = 0f
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        totalDx = event.rawX - downX
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (totalDx > threshold) {
                            Log.d(TAG, "Slide-to-cancel detected → cancel launch and close UI")
                            // 실행 취소: 진행 중 플래그 리셋 및 가드 종료
                            isLaunching = false
                            try { LaunchGuard.markEnd(this) } catch (_: Exception) {}
                            finish()
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling slide-to-cancel", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "StatusActivity created")
        
        // 전체 화면 설정
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 배경을 반투명하게 설정
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        val statusType = intent.getStringExtra(EXTRA_STATUS_TYPE) ?: "launch"
        val targetApp = intent.getStringExtra(EXTRA_TARGET_APP) ?: "네비게이션"
        val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 3)
        
        // 화면 깨우기 기능 추가
        if (statusType == "launch") {
            wakeUpScreen()
        }
        
        setupStatusUI(statusType, targetApp, delaySeconds)
        // 중복 표시 방지 가드: 시작 표시
        try { LaunchGuard.markStart(this) } catch (_: Exception) {}
        
        // 안내 화면은 충분히 보이도록 delaySeconds+1초 유지(최소 2초)
        val holdMs = (delaySeconds + 1).coerceAtLeast(2) * 1000L
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, holdMs)
        
        // 실행 상태일 때만 앱 실행 (중복 실행 방지)
        if (statusType == "launch") {
            val launchDelayMs = (delaySeconds.coerceAtLeast(0)) * 1000L
            val message = if (launchDelayMs == 0L) "바로 앱 실행" else "${delaySeconds}초 후 앱 실행"
            Log.d(TAG, "🔥 StatusActivity: $message")

            // 잠금화면에서 키가드 해제 후 실행: Android O+에서 콜백 사용
            val runLaunch = Runnable {
                if (!isLaunching) {
                    isLaunching = true
                    Log.d(TAG, "🔥 StatusActivity: 앱 실행 시작")
                    launchTargetApp(targetApp)
                } else {
                    Log.d(TAG, "App launch already in progress, skipping duplicate")
                }
            }

            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    km.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            Log.d(TAG, "Keyguard dismissed, scheduling app launch")
                            Handler(Looper.getMainLooper()).postDelayed(runLaunch, launchDelayMs)
                        }
                        override fun onDismissError() {
                            Log.w(TAG, "Keyguard dismiss error, launching anyway")
                            Handler(Looper.getMainLooper()).postDelayed(runLaunch, launchDelayMs)
                        }
                        override fun onDismissCancelled() {
                            Log.w(TAG, "Keyguard dismiss cancelled, launching anyway")
                            Handler(Looper.getMainLooper()).postDelayed(runLaunch, launchDelayMs)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Keyguard dismiss request failed", e)
                    Handler(Looper.getMainLooper()).postDelayed(runLaunch, launchDelayMs)
                }
            } else {
                // 구버전은 기존 방식으로 진행
                Handler(Looper.getMainLooper()).postDelayed(runLaunch, launchDelayMs)
            }
        }
        
        // 슬라이드 제스처로 실행 취소 기능 추가 (잠금 해제처럼 우측 슬라이드)
        enableSlideToCancel()
    }
    
    private fun setupStatusUI(statusType: String, targetApp: String, delaySeconds: Int) {
        try {
            val backgroundColor = when (statusType) {
                "launch" -> Color.parseColor("#8000FF00") // 반투명 초록
                "shutdown" -> Color.parseColor("#80FF0000") // 반투명 빨강
                else -> Color.parseColor("#80000000") // 반투명 검정
            }
            
            val icon = when (statusType) {
                "launch" -> "🚗"
                "shutdown" -> "⏹️"
                else -> "ℹ️"
            }
            
            val title = when (statusType) {
                "launch" -> "네비게이션 실행 준비"
                "shutdown" -> "네비게이션 종료 준비"
                else -> "상태 알림"
            }
            
            val message = when (statusType) {
                "launch" -> if (delaySeconds <= 0) "지금 ${targetApp}을 실행합니다" else "${delaySeconds}초 후 ${targetApp}이 실행됩니다"
                "shutdown" -> "${delaySeconds}초 후 ${targetApp}이 종료됩니다"
                else -> "처리 중..."
            }
            
            // 레이아웃 설정
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(backgroundColor)
                
                // 아이콘과 제목
                addView(TextView(this@StatusActivity).apply {
                    text = "$icon $title"
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(50, 50, 50, 20)
                })
                
                // 메시지
                addView(TextView(this@StatusActivity).apply {
                    text = message
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(50, 20, 50, 50)
                })
            }
            
            setContentView(layout)
            
            Log.d(TAG, "Status UI setup completed: $statusType")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up status UI", e)
        }
    }
    
    private fun wakeUpScreen() {
        try {
            Log.d(TAG, "🔥🔥🔥 Starting screen wake up process 🔥🔥🔥")
            
            // 1. 화면 깨우기 플래그 설정
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            
            // 2. Android O 이상에서 추가 설정
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            
            // 3. PowerManager를 통한 화면 깨우기
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "AutoLaunch::StatusActivityWakeLock"
                )
                wakeLock.acquire(10000) // 10초간 유지
                
                // 5초 후 WakeLock 해제
                Handler(Looper.getMainLooper()).postDelayed({
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        Log.d(TAG, "🔥 WakeLock released")
                    }
                }, 5000)
            }
            
            // 4. 키가드 해제 시도
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    keyguardManager.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            Log.d(TAG, "🔥 Keyguard dismissed successfully")
                        }
                        override fun onDismissError() {
                            Log.w(TAG, "🔥 Keyguard dismiss failed")
                        }
                        override fun onDismissCancelled() {
                            Log.w(TAG, "🔥 Keyguard dismiss cancelled")
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting keyguard dismiss", e)
                }
            }
            
            Log.d(TAG, "🔥 Screen wake up process completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error waking up screen", e)
        }
    }
    
    private fun launchTargetApp(targetApp: String) {
        try {
            Log.d(TAG, "🔥 Launching target app: $targetApp")
            
            val intent = packageManager.getLaunchIntentForPackage(targetApp)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                Log.d(TAG, "🔥 Target app launched successfully: $targetApp")
            } else {
                Log.w(TAG, "No direct launch intent. Trying URL scheme fallback for: $targetApp")
                val scheme = when (targetApp) {
                    "com.skt.tmap.ku" -> "tmap://"
                    "com.kakao.navi" -> "kakaonavi://"
                    "net.daum.android.map" -> "daummaps://"
                    "com.nhn.android.nmap" -> "nmap://"
                    "com.google.android.apps.maps" -> "googlemaps://"
                    "com.waze" -> "waze://"
                    else -> null
                }
                if (scheme != null) {
                    try {
                        val uriIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(scheme))
                        uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(uriIntent)
                        Log.d(TAG, "🔥 Launched via URL scheme: $scheme")
                    } catch (e: Exception) {
                        Log.e(TAG, "URL scheme launch failed: $scheme", e)
                    }
                } else {
                    Log.e(TAG, "No URL scheme known for: $targetApp")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching target app: $targetApp", e)
        } finally {
            // 실행 완료 후 플래그 리셋
            isLaunching = false
            try { LaunchGuard.markEnd(this) } catch (_: Exception) {}
        }
    }
} 