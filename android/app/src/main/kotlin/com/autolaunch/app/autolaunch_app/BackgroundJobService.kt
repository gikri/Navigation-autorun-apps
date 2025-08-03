package com.autolaunch.app.autolaunch_app

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.util.Log
import android.os.Build
import android.content.Context
import android.app.ActivityManager

class BackgroundJobService : JobService() {
    
    companion object {
        private const val TAG = "BackgroundJobService"
    }
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Background job started")
        
        try {
            // AutoLaunchService가 실행 중인지 확인
            if (!isAutoLaunchServiceRunning()) {
                Log.d(TAG, "AutoLaunchService not running, restarting...")
                // 서비스 재시작
                AutoLaunchService.startService(this)
            } else {
                Log.d(TAG, "AutoLaunchService is running normally")
            }
            
            // 배터리 상태도 한번 더 체크
            checkBatteryStatus()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in background job", e)
        }
        
        // 작업 완료
        jobFinished(params, false)
        return false // 비동기 작업 없음
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Background job stopped")
        return false // 재시작 불필요
    }
    
    private fun isAutoLaunchServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            services.any { serviceInfo ->
                serviceInfo.service.className == AutoLaunchService::class.java.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
            false
        }
    }
    
    private fun checkBatteryStatus() {
        try {
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra("status", -1)
                val level = batteryIntent.getIntExtra("level", -1)
                val scale = batteryIntent.getIntExtra("scale", -1)
                val isCharging = status == 2 || status == 5 // CHARGING or FULL
                
                Log.d(TAG, "Battery check from job - Level: ${level * 100 / scale}%, Charging: $isCharging")
                
                // 설정 확인하고 필요시 추가 처리
                val prefs = getSharedPreferences("autolaunch_prefs", Context.MODE_PRIVATE)
                val serviceEnabled = prefs.getBoolean("service_enabled", true)
                val targetApp = prefs.getString("target_app", null)
                
                if (serviceEnabled && targetApp != null) {
                    Log.d(TAG, "Service enabled with target app: $targetApp, Battery charging: $isCharging")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery status", e)
        }
    }
} 