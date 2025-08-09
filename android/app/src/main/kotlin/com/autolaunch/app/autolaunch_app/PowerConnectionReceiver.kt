package com.autolaunch.app.autolaunch_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.content.ComponentName
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.os.Handler
import android.os.Looper
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock

class PowerConnectionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PowerConnectionReceiver"
        private const val PREFS_NAME = "autolaunch_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_TARGET_APP = "target_app"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔥🔥🔥 PowerConnectionReceiver triggered: ${intent.action} 🔥🔥🔥")
        
        // 서비스 강제 재시작/알람 재예약은 MIUI에서만 사용
        if (SystemUtils.isMiui()) {
            AutoLaunchService.startService(context)
            restartMIUIBatteryCheck(context)
            Handler(Looper.getMainLooper()).postDelayed({
                AutoLaunchService.startService(context)
                restartMIUIBatteryCheck(context)
            }, 1000)
        }
        
        // 설정 확인
        val merged = PreferencesBridge.readValues(context)
        val serviceEnabled = merged.serviceEnabled
        val targetApp = merged.targetApp
        
        Log.d(TAG, "Service enabled: $serviceEnabled, Target app: $targetApp")

        // 서비스가 비활성화면 모든 처리 중단
        if (!serviceEnabled) {
            Log.w(TAG, "Service disabled - skipping power handling")
            return
        }
        
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d(TAG, "🔥🔥🔥 POWER CONNECTED DETECTED! 🔥🔥🔥")
                if (serviceEnabled && targetApp != null) {
                    // 새 연결 시작이면 시퀀스 증가 및 단 1회 처리
                    val seq = ConnectionGuard.beginConnectionIfNew(context)
                    if (seq == null) {
                        Log.w(TAG, "Power already marked as charging. Skip duplicate connected event")
                        return
                    }
                    if (!ConnectionGuard.shouldHandle(context, seq)) {
                        Log.w(TAG, "Sequence already handled: $seq")
                        return
                    }
                    ConnectionGuard.markHandled(context, seq)
                    if (!LaunchGuard.canShow(context)) {
                        Log.w(TAG, "Skip: Launch UI cooldown active or in progress")
                        return
                    }
                    Log.d(TAG, "🔥 Starting power connected handling for: $targetApp")
                    handlePowerConnected(context, targetApp)
                } else {
                    Log.d(TAG, "Service not enabled or target app not set")
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d(TAG, "🔥🔥🔥 POWER DISCONNECTED DETECTED! 🔥🔥🔥")
                Log.d(TAG, "Service enabled: $serviceEnabled, Target app: $targetApp")
                if (serviceEnabled && targetApp != null) {
                    // 연결 종료 마킹
                    ConnectionGuard.endConnection(context)
                    Log.d(TAG, "🔥 Starting power disconnected handling for: $targetApp")
                    // 해제시에도 5초 대기 UI 후 화면 잠금
                    try {
                        LaunchGuard.markStart(context)
                        showShutdownStatus(context, targetApp)
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                ScreenOffActivity.startScreenOff(context)
                            } catch (_: Exception) {}
                        }, 5000)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling disconnect UI/lock", e)
                        handlePowerDisconnected(context, targetApp)
                    }
                } else {
                    Log.w(TAG, "⚠️ Service not enabled or target app not set - skipping power disconnect handling")
                }
            }
            Intent.ACTION_BOOT_COMPLETED, 
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed - setting up AutoLaunch")
                // 부팅 완료 시 백그라운드 서비스 시작
                AutoLaunchService.startService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Package replaced - restarting AutoLaunch")
                // 앱 업데이트 후 서비스 재시작
                AutoLaunchService.startService(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned on")
                // 비-MIUI에서는 화면 켜짐시 추가 체크를 하지 않아 루프 방지
                if (SystemUtils.isMiui()) {
                    checkPowerStatusOnScreenOn(context)
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned off - ensuring service is running")
                // 화면이 꺼질 때 서비스가 실행 중인지 확인
                AutoLaunchService.startService(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present")
                if (SystemUtils.isMiui()) {
                    checkPowerStatusOnScreenOn(context)
                }
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                // 배터리 상태 변화시 충전 연결/해제 감지
                handleBatteryChanged(context, intent)
            }
        }
    }
    
    private fun handlePowerConnected(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "🔥🔥🔥 POWER CONNECTED - Show waiting UI immediately, launch after 5s: $targetApp 🔥🔥🔥")

            // 즉시 대기 UI 표시 (5초 카운트다운)
            LaunchGuard.markStart(context)
            showLaunchStatus(context, targetApp, delaySeconds = 5)

            // 보조: 풀스크린 알림도 함께(디바이스별 정책 대비)
            try {
                AutoLaunchService.showWakeUpNotification(context, targetApp)
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.e(TAG, "Error handling power connected", e)
        }
    }
    
    private fun showLaunchStatus(context: Context, targetApp: String, delaySeconds: Int = 0) {
        try {
            Log.d(TAG, "🔥🔥🔥 즉시 StatusActivity 실행 🔥🔥🔥")
            val statusIntent = Intent(context, StatusActivity::class.java).apply {
                putExtra(StatusActivity.EXTRA_STATUS_TYPE, "launch")
                putExtra(StatusActivity.EXTRA_TARGET_APP, targetApp)
                // 요청된 지연(초) 동안 UI를 보여준 뒤 실행
                putExtra(StatusActivity.EXTRA_DELAY_SECONDS, delaySeconds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(statusIntent)
            Log.d(TAG, "🔥 StatusActivity launched immediately")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing launch status", e)
        }
    }
    
    private fun showShutdownStatus(context: Context, targetApp: String) {
        try {
            val statusIntent = Intent(context, StatusActivity::class.java).apply {
                putExtra(StatusActivity.EXTRA_STATUS_TYPE, "shutdown")
                putExtra(StatusActivity.EXTRA_TARGET_APP, targetApp)
                putExtra(StatusActivity.EXTRA_DELAY_SECONDS, 5)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(statusIntent)
            Log.d(TAG, "🔥 Shutdown status activity started")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing shutdown status", e)
        }
    }
    
    private fun executeWakeUpSequence(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "🔥🔥🔥 EXECUTING WAKE UP SEQUENCE for: $targetApp 🔥🔥🔥")
            
            // StatusActivity에서 3초 딜레이 후 앱 실행하므로 여기서는 추가 실행하지 않음
            Log.d(TAG, "🔥 StatusActivity will handle app launch after 3 seconds")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake up sequence", e)
        }
    }
    
    private fun handlePowerDisconnected(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "🔥 POWER DISCONNECTED - Starting comprehensive app close and screen off sequence")
            LogManager.getInstance().logAndSave(context, TAG, "🔥 POWER DISCONNECTED - Starting comprehensive sequence for: $targetApp")
            
            // StatusActivity로 종료 상태 표시
            showShutdownStatus(context, targetApp)
            
            // 3초 딜레이 후 화면 끄기 및 앱 종료 시작
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔥 3초 딜레이 후 화면 끄기 및 앱 종료 시작")
                
                // 사용자에게 종료 알림
                showUserNotification(context, "🔄 앱 종료 중", "네비게이션 앱을 종료하고 화면을 끕니다.")
                
                // 🔥 방식 1: 강력한 앱 종료 시스템 사용
                Log.d(TAG, "🔥 Step 1: Using AppKiller for comprehensive app termination")
                LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 1: AppKiller start for: $targetApp")
                
                AppKiller.killApp(context, targetApp) { killResult ->
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 AppKiller result: $killResult for: $targetApp")
                }
                
                // 🔥 방식 2: 100ms 후 기존 방식도 병행
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 2: Backup force close attempt")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 2: Backup force close")
                    forceCloseTargetApp(context, targetApp)
                    forceCloseTargetAppAlternative(context, targetApp)
                }, 100)
                
                // 🔥 방식 3: 200ms 후 홈 화면 강제 이동
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 3: Force move to home screen")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 3: Force to home")
                    forceToHomeScreen(context)
                }, 200)
                
                // 🔥 방식 4: 500ms 후 화면 끄기 시도 1차 (강화된 버전)
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 4: Primary screen off attempt")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 4: Primary screen off")
                    ScreenOffActivity.startScreenOff(context)
                    
                    // 추가 화면 끄기 시도
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔥 Step 4.1: Additional screen off attempt")
                        ScreenOffActivity.startScreenOff(context)
                    }, 1000)
                }, 500)
                
                // 🔥 방식 5: 800ms 후 MainAcitivity 종료 신호
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 5: Send close signal to MainActivity")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 5: MainActivity signal")
                    sendCloseSignalToMainActivity(context)
                }, 800)
                
                // 🔥 방식 6: 1.5초 후 대안 화면 끄기 시도
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 6: Alternative screen off methods")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 6: Alternative screen off")
                    alternativeScreenOffMethods(context)
                }, 1500)
                
                // 🔥 방식 7: 2초 후 최종 안전장치
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔥 Step 7: Final safety net - force everything")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Step 7: Final safety net")
                    finalSafetyNet(context, targetApp)
                }, 2000)
                
                // 🔥 방식 8: 3초 후 최종 상태 체크
                Handler(Looper.getMainLooper()).postDelayed({
                    val isStillRunning = AppKiller.isAppRunning(context, targetApp)
                    Log.d(TAG, "🔥 Final check: Target app still running: $isStillRunning")
                    LogManager.getInstance().logAndSave(context, TAG, "🔥 Final check: App running: $isStillRunning")
                    
                    if (isStillRunning) {
                        Log.w(TAG, "🔥 WARNING: Target app still running after all attempts!")
                        LogManager.getInstance().logAndSave(context, TAG, "🔥 WARNING: App still running!", "W")
                        // 최종 긴급 시도
                        AppKiller.killApp(context, targetApp)
                    }
                }, 3000)
                
            }, 2000) // 2초 딜레이
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error handling power disconnected", e)
            LogManager.getInstance().logAndSave(context, TAG, "🔥 Error: ${e.message}", "E")
            // 오류 발생 시 긴급 모드
            emergencyPowerDisconnectMode(context, targetApp)
        }
    }
    
    // launchApp 메서드 제거 - StatusActivity만 사용
    // private fun launchApp(context: Context, packageName: String) { ... }
    // private fun launchAppByUrlScheme(context: Context, packageName: String) { ... }
    
    private fun forceCloseTargetApp(context: Context, packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // 최근 작업에서 해당 앱 제거 시도
            val recentTasks = activityManager.appTasks
            for (task in recentTasks) {
                try {
                    val taskInfo = task.taskInfo
                    if (taskInfo.baseActivity?.packageName == packageName) {
                        task.finishAndRemoveTask()
                        Log.d(TAG, "Removed task for package: $packageName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing task for package: $packageName", e)
                }
            }
            
            // 홈 화면으로 이동하여 앱을 백그라운드로 보냄
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(homeIntent)
            
            Log.d(TAG, "Forced close target app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error force closing target app: $packageName", e)
        }
    }
    
    private fun sendCloseSignalToMainActivity(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("action", "close_app")
            context.startActivity(intent)
            Log.d(TAG, "Close signal sent to MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending close signal to MainActivity", e)
        }
    }
    
    private fun checkPowerStatusOnScreenOn(context: Context) {
        try {
            // 화면이 켜졌을 때 현재 충전 상태를 확인하고 필요시 처리
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra("status", -1)
                val isCharging = status == 2 || status == 5 // CHARGING or FULL
                val level = batteryIntent.getIntExtra("level", -1)
                val scale = batteryIntent.getIntExtra("scale", -1)
                
                Log.d(TAG, "Screen on - Battery: ${level * 100 / scale}%, Charging: $isCharging")
                
                val merged = PreferencesBridge.readValues(context)
                val serviceEnabled = merged.serviceEnabled
                val targetApp = merged.targetApp
                
                if (serviceEnabled && targetApp != null && isCharging) {
                    // 충전 중이면 앱 실행 체크
                    Log.d(TAG, "Device is charging on screen on - checking app launch")
                    handlePowerConnected(context, targetApp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking power status on screen on", e)
        }
    }
    
    private fun handleBatteryChanged(context: Context, intent: Intent) {
        try {
            val status = intent.getIntExtra("status", -1)
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            val isCharging = status == 2 || status == 5 // CHARGING or FULL
            val wasCharging = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("was_charging", false)
            
            Log.d(TAG, "Battery changed - Level: ${level * 100 / scale}%, Charging: $isCharging, Was charging: $wasCharging")
            
            // 충전 상태 변화 감지
            if (isCharging != wasCharging) {
                // 충전 상태가 변경되었음
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("was_charging", isCharging)
                    .apply()
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
                val targetApp = prefs.getString(KEY_TARGET_APP, null)
                
                if (serviceEnabled && targetApp != null) {
                    if (isCharging) {
                        Log.d(TAG, "Charging state changed to connected via battery change")
                        handlePowerConnected(context, targetApp)
                    } else {
                        Log.d(TAG, "Charging state changed to disconnected via battery change")
                        handlePowerDisconnected(context, targetApp)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling battery changed", e)
        }
    }
    
    private fun restartMIUIBatteryCheck(context: Context) {
        try {
            Log.d(TAG, "🔥 MIUI: Restarting AlarmManager battery check")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val alarmIntent = Intent(context, MIUIBatteryCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001, // ALARM_REQUEST_CODE와 같은 값
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 기존 알람 취소 후 새로 예약 (5초 후)
            alarmManager.cancel(pendingIntent)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5000L, // 5초 후
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5000L, // 5초 후
                    pendingIntent
                )
            }
            
            Log.d(TAG, "🔥 MIUI: AlarmManager battery check restarted")
        } catch (e: Exception) {
            Log.e(TAG, "🔥 MIUI: Error restarting battery check", e)
        }
    }
    
    // 🔥 새로운 강화된 앱 종료 메서드들
    private fun forceCloseTargetAppAlternative(context: Context, packageName: String) {
        try {
            Log.d(TAG, "🔥 Alternative force close method for: $packageName")
            
            // 방법 1: 프로세스 킬링 시도
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            Log.d(TAG, "🔥 Killed background processes for: $packageName")
            
            // 방법 2: 모든 런칭 태스크 종료
            try {
                val runningTasks = activityManager.getRunningTasks(50)
                for (task in runningTasks) {
                    if (task.baseActivity?.packageName == packageName) {
                        Log.d(TAG, "🔥 Found running task for $packageName, attempting to remove")
                        // Android 11 이상에서는 제한되지만 시도해볼 수 있음
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "🔥 Cannot get running tasks (Android security)", e)
            }
            
            // 방법 3: 브로드캐스트로 앱에 종료 신호 전송
            try {
                val closeIntent = Intent("com.autolaunch.FORCE_CLOSE_TARGET_APP")
                closeIntent.putExtra("package_name", packageName)
                context.sendBroadcast(closeIntent)
                Log.d(TAG, "🔥 Sent force close broadcast to: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 Failed to send close broadcast", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error in alternative force close", e)
        }
    }
    
    private fun forceToHomeScreen(context: Context) {
        try {
            Log.d(TAG, "🔥 Forcing to home screen")
            
            // 방법 1: 일반 홈 인텐트
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(homeIntent)
            
            // 방법 2: 런처 인텐트
            val launcherIntent = Intent(Intent.ACTION_MAIN)
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launcherIntent)
                Log.d(TAG, "🔥 Launched home screen successfully")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 Launcher intent failed", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error forcing to home screen", e)
        }
    }
    
    private fun alternativeScreenOffMethods(context: Context) {
        try {
            Log.d(TAG, "🔥 Trying alternative screen off methods")
            
            // 방법 1: 시스템 UI 숨기기
            try {
                val systemUIIntent = Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS")
                context.sendBroadcast(systemUIIntent)
                Log.d(TAG, "🔥 Sent close system dialogs broadcast")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 System UI hide failed", e)
            }
            
            // 방법 2: 다시 한 번 ScreenOffActivity 시도
            Handler(Looper.getMainLooper()).postDelayed({
                ScreenOffActivity.startScreenOff(context)
            }, 200)
            
            // 방법 3: DeviceAdminReceiver를 통한 화면 끄기 시도
            try {
                val deviceAdminIntent = Intent(context, DeviceAdminReceiver::class.java)
                deviceAdminIntent.action = "com.autolaunch.LOCK_SCREEN"
                context.sendBroadcast(deviceAdminIntent)
                Log.d(TAG, "🔥 Sent device admin lock broadcast")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 Device admin broadcast failed", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error in alternative screen off methods", e)
        }
    }
    
    private fun finalSafetyNet(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "🔥 FINAL SAFETY NET - Last resort methods")
            
            // 최종 1: 한 번 더 홈 화면으로
            forceToHomeScreen(context)
            
            // 최종 2: 한 번 더 앱 킬링 시도
            Handler(Looper.getMainLooper()).postDelayed({
                forceCloseTargetAppAlternative(context, targetApp)
            }, 100)
            
            // 최종 3: 브로드캐스트로 모든 컴포넌트에 종료 신호
            Handler(Looper.getMainLooper()).postDelayed({
                val emergencyCloseIntent = Intent("com.autolaunch.EMERGENCY_CLOSE")
                emergencyCloseIntent.putExtra("reason", "power_disconnected")
                context.sendBroadcast(emergencyCloseIntent)
                Log.d(TAG, "🔥 Sent emergency close broadcast")
            }, 200)
            
            Log.d(TAG, "🔥 FINAL SAFETY NET completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error in final safety net", e)
        }
    }
    
    private fun emergencyPowerDisconnectMode(context: Context, targetApp: String) {
        try {
            Log.e(TAG, "🔥🔥🔥 EMERGENCY POWER DISCONNECT MODE ACTIVATED! 🔥🔥🔥")
            
            // 긴급 1: 즉시 홈 화면으로
            forceToHomeScreen(context)
            
            // 긴급 2: 500ms 후 화면 끄기 시도
            Handler(Looper.getMainLooper()).postDelayed({
                ScreenOffActivity.startScreenOff(context)
            }, 500)
            
            // 긴급 3: 1초 후 최종 시도
            Handler(Looper.getMainLooper()).postDelayed({
                finalSafetyNet(context, targetApp)
            }, 1000)
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥🔥🔥 EMERGENCY MODE ALSO FAILED! 🔥🔥🔥", e)
        }
    }
    
    private fun showUserNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // 사용자 알림 채널 생성
            val channelId = "user_notifications"
            val channelName = "사용자 알림"
            val channelDescription = "충전 연결/해제 시 사용자에게 표시되는 알림"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = channelDescription
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // 알림 생성
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            
            // 알림 표시 (고유 ID 사용)
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            
            // 3초 후 자동으로 알림 제거
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                notificationManager.cancel(notificationId)
            }, 3000)
            
            Log.d(TAG, "User notification shown: $title - $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing user notification", e)
        }
    }
    
    private fun checkPowerStatusImmediately(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
            val targetApp = prefs.getString(KEY_TARGET_APP, null)
            
            if (serviceEnabled && targetApp != null) {
                // 현재 충전 상태 확인
                val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || 
                                status == android.os.BatteryManager.BATTERY_STATUS_FULL
                
                Log.d(TAG, "🔥 Immediate power status check - Charging: $isCharging, Target app: $targetApp")
                
                if (isCharging) {
                    // 충전 중이면 즉시 앱 실행
                    handlePowerConnected(context, targetApp)
                } else {
                    // 충전 해제 상태면 화면 끄기
                    Log.d(TAG, "🔥 Power disconnected detected in immediate check")
                    handlePowerDisconnected(context, targetApp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in immediate power status check", e)
        }
    }
    

} 