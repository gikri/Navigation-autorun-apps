package com.autolaunch.app.autolaunch_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper

class MIUIBatteryCheckReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MIUIBatteryCheckReceiver"
        private const val ALARM_BATTERY_CHECK_INTERVAL = 5000L // MIUI 대응: 5초로 더 단축
        private const val ALARM_REQUEST_CODE = 1001
        private const val PREFS_NAME = "autolaunch_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_TARGET_APP = "target_app"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MIUI battery check receiver triggered")
        
        try {
            // 1. AutoLaunchService가 실행 중인지 확인하고 필요시 재시작
            AutoLaunchService.startService(context)
            
            // 2. 배터리 상태 체크 및 충전 상태 변화 감지
            checkBatteryStatusAndHandleChanges(context)
            
            // 3. 다음 알람 예약 (지속적 실행 보장)
            scheduleNextAlarm(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in MIUI battery check receiver", e)
            // 오류가 발생해도 다음 알람은 예약
            scheduleNextAlarm(context)
        }
    }
    
    private fun checkBatteryStatusAndHandleChanges(context: Context) {
        try {
            // 현재 배터리 상태 확인
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra("status", -1)
                val level = batteryIntent.getIntExtra("level", -1)
                val scale = batteryIntent.getIntExtra("scale", -1)
                val isCharging = status == 2 || status == 5 // CHARGING or FULL
                
                Log.d(TAG, "MIUI Battery Check - Level: ${level * 100 / scale}%, Charging: $isCharging")
                
                // 이전 충전 상태와 비교
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val wasCharging = prefs.getBoolean("was_charging_miui", false)
                val serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
                val targetApp = prefs.getString(KEY_TARGET_APP, null)
                
                if (serviceEnabled && targetApp != null && isCharging != wasCharging) {
                    // 충전 상태가 변경되었음
                    prefs.edit().putBoolean("was_charging_miui", isCharging).apply()
                    
                    if (isCharging) {
                        Log.d(TAG, "MIUI: Charging connected detected - launching app")
                        handlePowerConnected(context, targetApp)
                    } else {
                        Log.d(TAG, "🔥🔥🔥 MIUI: CHARGING DISCONNECTED DETECTED - AGGRESSIVE CLOSING! 🔥🔥🔥")
                        handlePowerDisconnected(context, targetApp)
                    }
                } else if (serviceEnabled && targetApp != null) {
                    // 충전 상태 변화가 없어도 현재 상태에 따라 처리
                    if (isCharging) {
                        // 🔥 MIUI 대응: 충전 중이면 매번 앱 실행 확인 및 시도
                        Log.d(TAG, "🔥 MIUI: Aggressively ensuring app is running while charging")
                        handlePowerConnected(context, targetApp)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery status in MIUI receiver", e)
        }
    }
    
    private fun handlePowerConnected(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "MIUI: Handling power connection")
            
            // WakeUpActivity로 화면 깨우기 및 앱 실행
            val intent = Intent(context, WakeUpActivity::class.java)
            intent.putExtra(WakeUpActivity.EXTRA_TARGET_APP, targetApp)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            
            // 고우선순위 알림도 표시
            AutoLaunchService.showWakeUpNotification(context, targetApp)
            
            Log.d(TAG, "MIUI: Power connection handled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "MIUI: Error handling power connection", e)
        }
    }
    
    private fun handlePowerDisconnected(context: Context, targetApp: String) {
        try {
            Log.d(TAG, "🔥 MIUI: AGGRESSIVE POWER DISCONNECTION HANDLING")
            
            // 🔥 즉시 타겟 앱 강제 종료
            forceCloseTargetApp(context, targetApp)
            
            // 🔥 200ms 후 추가 종료 시도
            Handler(Looper.getMainLooper()).postDelayed({
                forceCloseTargetAppSecondary(context, targetApp)
            }, 200)
            
            // 🔥 500ms 후 홈 화면으로 이동
            Handler(Looper.getMainLooper()).postDelayed({
                forceToHomeScreenMIUI(context)
            }, 500)
            
            // 🔥 800ms 후 화면 끄기 실행
            Handler(Looper.getMainLooper()).postDelayed({
                ScreenOffActivity.startScreenOff(context)
            }, 800)
            
            Log.d(TAG, "🔥 MIUI: Power disconnection handled aggressively")
        } catch (e: Exception) {
            Log.e(TAG, "🔥 MIUI: Error handling power disconnection", e)
            // 긴급 모드
            emergencyMIUIDisconnect(context, targetApp)
        }
    }
    
    private fun forceCloseTargetApp(context: Context, packageName: String) {
        try {
            // 홈 화면으로 이동하여 앱을 백그라운드로 보냄
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(homeIntent)
            
            Log.d(TAG, "MIUI: Forced close target app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "MIUI: Error force closing target app: $packageName", e)
        }
    }
    
    private fun scheduleNextAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val alarmIntent = Intent(context, MIUIBatteryCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 다음 10초 후 알람 예약
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_BATTERY_CHECK_INTERVAL,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_BATTERY_CHECK_INTERVAL,
                    pendingIntent
                )
            }
            
            Log.d(TAG, "MIUI: Next alarm scheduled in ${ALARM_BATTERY_CHECK_INTERVAL/1000} seconds")
        } catch (e: Exception) {
            Log.e(TAG, "MIUI: Error scheduling next alarm", e)
        }
    }
    
    // 🔥 MIUI 전용 강화 메서드들
    private fun forceCloseTargetAppSecondary(context: Context, packageName: String) {
        try {
            Log.d(TAG, "🔥 MIUI: Secondary force close for $packageName")
            
            // MIUI 특화 앱 종료 방법
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 백그라운드 프로세스 킬링
            activityManager.killBackgroundProcesses(packageName)
            Log.d(TAG, "🔥 MIUI: Killed background processes")
            
            // 브로드캐스트로 앱 종료 신호 전송
            val closeIntent = Intent("$packageName.FORCE_CLOSE")
            context.sendBroadcast(closeIntent)
            Log.d(TAG, "🔥 MIUI: Sent force close broadcast")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 MIUI: Secondary force close failed", e)
        }
    }
    
    private fun forceToHomeScreenMIUI(context: Context) {
        try {
            Log.d(TAG, "🔥 MIUI: Forcing to home screen")
            
            // MIUI 런처로 이동 시도
            val miuiLauncherIntent = Intent(Intent.ACTION_MAIN)
            miuiLauncherIntent.addCategory(Intent.CATEGORY_HOME)
            miuiLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            miuiLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            miuiLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // MIUI 런처 패키지 지정 (가능한 경우)
            try {
                miuiLauncherIntent.setPackage("com.miui.home")
            } catch (e: Exception) {
                Log.w(TAG, "🔥 MIUI: Cannot set MIUI launcher package", e)
            }
            
            context.startActivity(miuiLauncherIntent)
            Log.d(TAG, "🔥 MIUI: Home screen activated")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 MIUI: Force to home failed", e)
        }
    }
    
    private fun emergencyMIUIDisconnect(context: Context, targetApp: String) {
        try {
            Log.e(TAG, "🔥🔥🔥 MIUI EMERGENCY DISCONNECT MODE! 🔥🔥🔥")
            
            // 긴급 1: 즉시 홈으로
            forceToHomeScreenMIUI(context)
            
            // 긴급 2: 500ms 후 화면 끄기
            Handler(Looper.getMainLooper()).postDelayed({
                ScreenOffActivity.startScreenOff(context)
            }, 500)
            
            // 긴급 3: 1초 후 앱 킬링
            Handler(Looper.getMainLooper()).postDelayed({
                forceCloseTargetAppSecondary(context, targetApp)
            }, 1000)
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥🔥🔥 MIUI EMERGENCY MODE FAILED! 🔥🔥🔥", e)
        }
    }
} 