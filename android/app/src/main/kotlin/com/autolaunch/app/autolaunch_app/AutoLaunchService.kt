package com.autolaunch.app.autolaunch_app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.content.Context
import android.os.Build
import android.util.Log
import android.os.PowerManager
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.app.job.JobInfo
import android.content.ComponentName
import android.os.SystemClock

class AutoLaunchService : Service() {
    
    companion object {
        private const val TAG = "AutoLaunchService"
        private const val CHANNEL_ID = "autolaunch_service_channel"
        private const val WAKE_UP_CHANNEL_ID = "autolaunch_wakeup_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_UP_NOTIFICATION_ID = 1002
        private const val BATTERY_CHECK_INTERVAL = 15000L // MIUI 대응: 15초로 단축
        private const val ALARM_BATTERY_CHECK_INTERVAL = 5000L // MIUI 대응: AlarmManager 5초
        private const val JOB_ID = 1000
        private const val ALARM_REQUEST_CODE = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, AutoLaunchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AutoLaunchService::class.java)
            context.stopService(intent)
        }
        
        fun showWakeUpNotification(context: Context, targetApp: String) {
            val service = AutoLaunchService()
            service.showWakeUpNotification(context, targetApp)
        }
    }
    
    // 백그라운드 지속 실행을 위한 컴포넌트들
    private var wakeLock: PowerManager.WakeLock? = null
    private val batteryCheckHandler = Handler(Looper.getMainLooper())
    private var batteryReceiver: BroadcastReceiver? = null
    private var jobScheduler: JobScheduler? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutoLaunchService created")
        createNotificationChannels()
        
        // 백그라운드 지속 실행을 위한 설정
        setupBackgroundPersistence()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AutoLaunchService started")
        
        // Foreground 서비스 시작
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 서비스가 강제 종료되어도 다시 시작되도록 설정
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - restarting service")
        
        // 태스크가 제거되어도 서비스 재시작
        val restartServiceIntent = Intent(applicationContext, AutoLaunchService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 백그라운드 서비스 채널 (중간 우선순위로 변경)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AutoLaunch 서비스",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AutoLaunch 백그라운드 서비스"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false)
            }
            
            // 화면 깨우기 채널 (고우선순위)
            val wakeUpChannel = NotificationChannel(
                WAKE_UP_CHANNEL_ID,
                "화면 깨우기",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "충전 연결 시 화면 깨우기"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(wakeUpChannel)
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 AutoLaunch 실행 중")
            .setContentText("충전 연결 시 네비게이션 자동 실행 대기 중")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    fun showWakeUpNotification(context: Context, targetApp: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // WakeUpActivity 실행 인텐트
            val wakeUpIntent = Intent(context, WakeUpActivity::class.java)
            wakeUpIntent.putExtra(WakeUpActivity.EXTRA_TARGET_APP, targetApp)
            wakeUpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val pendingIntent = PendingIntent.getActivity(
                context, 0, wakeUpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, WAKE_UP_CHANNEL_ID)
                .setContentTitle("충전 연결됨")
                .setContentText("네비게이션 앱을 실행합니다")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            
            notificationManager.notify(WAKE_UP_NOTIFICATION_ID, notification)
            
            // 3초 후 알림 자동 제거
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                notificationManager.cancel(WAKE_UP_NOTIFICATION_ID)
            }, 3000)
            
            Log.d(TAG, "Wake up notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing wake up notification", e)
        }
    }
    
    private fun setupBackgroundPersistence() {
        try {
            Log.d(TAG, "Setting up enhanced background persistence for MIUI")
            
            // 1. PARTIAL_WAKE_LOCK 획득 (Doze Mode 우회)
            acquireWakeLock()
            
            // 2. 배터리 상태 모니터링 시작
            startBatteryMonitoring()
            
            // 3. MIUI 대응: AlarmManager로 강력한 백그라운드 체크
            setupAlarmManager()
            
            // 4. JobScheduler로 백그라운드 작업 예약
            scheduleBackgroundJob()
            
            Log.d(TAG, "Enhanced background persistence setup completed for MIUI")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up background persistence", e)
        }
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // MIUI 대응: 더 강력한 WakeLock 설정
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "AutoLaunch::MIUIBackgroundLock"
            )
            
            // 무제한 시간으로 WakeLock 획득 (MIUI 강제 종료 방지)
            wakeLock?.acquire()
            Log.d(TAG, "Enhanced PARTIAL_WAKE_LOCK acquired for MIUI")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }
    
    private fun startBatteryMonitoring() {
        try {
            // 배터리 상태 변화 감지를 위한 브로드캐스트 수신기 등록
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_POWER_CONNECTED -> {
                            Log.d(TAG, "Power connected detected in service")
                            handlePowerEvent(context, true)
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            Log.d(TAG, "Power disconnected detected in service")
                            handlePowerEvent(context, false)
                        }
                        Intent.ACTION_BATTERY_CHANGED -> {
                            // 배터리 상태 변화 로그
                            val level = intent.getIntExtra("level", -1)
                            val scale = intent.getIntExtra("scale", -1)
                            val isCharging = intent.getIntExtra("status", -1) == 2
                            Log.d(TAG, "Battery: ${level * 100 / scale}%, Charging: $isCharging")
                        }
                    }
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            
            registerReceiver(batteryReceiver, filter)
            Log.d(TAG, "Battery monitoring started")
            
            // 주기적 배터리 상태 체크 시작
            startPeriodicBatteryCheck()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting battery monitoring", e)
        }
    }
    
    private fun startPeriodicBatteryCheck() {
        val batteryCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    // 주기적으로 배터리 상태를 확인하여 이벤트 놓침 방지
                    val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = registerReceiver(null, intentFilter)
                    
                    if (batteryStatus != null) {
                        val status = batteryStatus.getIntExtra("status", -1)
                        val isCharging = status == 2 || status == 5 // CHARGING or FULL
                        val level = batteryStatus.getIntExtra("level", -1)
                        val scale = batteryStatus.getIntExtra("scale", -1)
                        
                        Log.d(TAG, "Periodic battery check - Level: ${level * 100 / scale}%, Charging: $isCharging")
                    }
                    
                    // 다음 체크 예약
                    batteryCheckHandler.postDelayed(this, BATTERY_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic battery check", e)
                    // 오류가 발생해도 계속 체크
                    batteryCheckHandler.postDelayed(this, BATTERY_CHECK_INTERVAL)
                }
            }
        }
        
        batteryCheckHandler.postDelayed(batteryCheckRunnable, BATTERY_CHECK_INTERVAL)
        Log.d(TAG, "Periodic battery check started")
    }
    
    private fun setupAlarmManager() {
        try {
            Log.d(TAG, "Setting up AlarmManager for MIUI bypass")
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // MIUI 백그라운드 제한 우회용 AlarmManager PendingIntent 생성
            val alarmIntent = Intent(this, MIUIBatteryCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 
                ALARM_REQUEST_CODE, 
                alarmIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 정확한 알람으로 MIUI 제한 우회 (10초마다)
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
            
            Log.d(TAG, "MIUI bypass AlarmManager set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up AlarmManager", e)
        }
    }
    
    private fun scheduleBackgroundJob() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                
                val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(this, BackgroundJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true)
                    .setPeriodic(5 * 60 * 1000L) // MIUI 대응: 5분으로 단축
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .build()
                
                val result = jobScheduler?.schedule(jobInfo)
                Log.d(TAG, "Enhanced background job scheduled for MIUI: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling background job", e)
        }
    }
    
    private fun handlePowerEvent(context: Context, isConnected: Boolean) {
        try {
            val prefs = context.getSharedPreferences("autolaunch_prefs", Context.MODE_PRIVATE)
            val serviceEnabled = prefs.getBoolean("service_enabled", true)
            val targetApp = prefs.getString("target_app", null)
            
            if (serviceEnabled && targetApp != null) {
                if (isConnected) {
                    Log.d(TAG, "Handling power connection in service")
                    // WakeUpActivity로 위임
                    val intent = Intent(context, WakeUpActivity::class.java)
                    intent.putExtra(WakeUpActivity.EXTRA_TARGET_APP, targetApp)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                } else {
                    Log.d(TAG, "Handling power disconnection in service")
                    // ScreenOffActivity로 위임
                    Handler(Looper.getMainLooper()).postDelayed({
                        ScreenOffActivity.startScreenOff(context)
                    }, 1000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling power event", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutoLaunchService destroyed")
        
        // 리소스 해제
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
            
            batteryReceiver?.let {
                unregisterReceiver(it)
                Log.d(TAG, "Battery receiver unregistered")
            }
            
            batteryCheckHandler.removeCallbacksAndMessages(null)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                jobScheduler?.cancel(JOB_ID)
                Log.d(TAG, "Background job cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
} 