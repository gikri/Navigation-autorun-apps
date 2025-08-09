package com.autolaunch.app.autolaunch_app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
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
        private const val BATTERY_CHECK_INTERVAL = 5000L // 백그라운드 감지를 위해 5초로 더 단축
        private const val ALARM_BATTERY_CHECK_INTERVAL = 3000L // 백그라운드 감지를 위해 3초로 더 단축
        private const val JOB_ID = 1000
        private const val ALARM_REQUEST_CODE = 1001
        
        fun startService(context: Context) {
            try {
                // 이미 실행 중인지 확인
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val isRunning = manager.getRunningServices(Integer.MAX_VALUE).any { 
                    it.service.className == AutoLaunchService::class.java.name 
                }
                
                if (isRunning) {
                    Log.d(TAG, "Service already running, skipping start request")
                    return
                }
                
                val intent = Intent(context, AutoLaunchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Service start requested successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service", e)
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
    private val notificationUpdateHandler = Handler(Looper.getMainLooper())
    private var notificationUpdateRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutoLaunchService created")
        createNotificationChannels()
        
        // 백그라운드 지속 실행을 위한 설정
        setupBackgroundPersistence()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AutoLaunchService started")
        
        // 앱 활성화 상태 확인
        val isServiceEnabled = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", false)
        
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Foreground 서비스 시작 (최소 권한 타입만 사용)
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground: ${e.message}")
        }
        
        // 백그라운드 알림 강화 - 주기적으로 알림 업데이트
        startNotificationUpdateHandler()
        
        // 이미 실행 중인지 확인
        if (isServiceRunning()) {
            Log.d(TAG, "Service already running, skipping duplicate start")
            return START_STICKY
        }
        
        // 서비스가 강제 종료되어도 다시 시작되도록 설정
        return START_STICKY
    }
    
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AutoLaunchService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - checking if service restart is needed")
        
        // 서비스가 이미 실행 중이면 재시작하지 않음
        if (!isServiceRunning()) {
            Log.d(TAG, "Service not running, restarting service")
            val restartServiceIntent = Intent(applicationContext, AutoLaunchService::class.java)
            restartServiceIntent.setPackage(packageName)
            startService(restartServiceIntent)
        } else {
            Log.d(TAG, "Service already running, skipping restart")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 기존 채널 삭제 (중복 방지)
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
            notificationManager.deleteNotificationChannel(WAKE_UP_CHANNEL_ID)
            
            // 백그라운드 서비스 채널 (최소 우선순위로 설정)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AutoLaunch 서비스",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "AutoLaunch 백그라운드 서비스"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                // 잠금화면에서도 표시되도록 설정
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 알림을 항상 표시
                setAllowBubbles(false)
            }
            
            // 화면 깨우기 채널 (최고우선순위)
            val wakeUpChannel = NotificationChannel(
                WAKE_UP_CHANNEL_ID,
                "화면 깨우기",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "충전 연결 시 화면 깨우기"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                // 잠금화면에서도 표시되도록 설정
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 알림을 항상 표시
                setAllowBubbles(true)
            }
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(wakeUpChannel)
            
            Log.d(TAG, "Notification channels created successfully")
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 AutoLaunch 백그라운드 실행 중")
            .setContentText("충전 연결 시 네비게이션 자동 실행 대기 중")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .build()
    }
    
        fun showWakeUpNotification(context: Context, targetApp: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 이미 알림이 표시되고 있는지 확인
            val activeNotifications = notificationManager.activeNotifications
            val hasWakeUpNotification = activeNotifications.any { it.id == WAKE_UP_NOTIFICATION_ID }
            
            if (hasWakeUpNotification) {
                Log.d(TAG, "Wake up notification already showing, skipping duplicate")
                return
            }
            
            // 기존 알림 제거
            notificationManager.cancel(WAKE_UP_NOTIFICATION_ID)
            // StatusActivity를 풀스크린 인텐트 대상으로 사용하여 잠금화면에서도 UI 표시
            val wakeUpIntent = Intent(context, StatusActivity::class.java).apply {
                putExtra(StatusActivity.EXTRA_STATUS_TYPE, "launch")
                // UI 표시 후 즉시 실행되도록 2초 카운트다운 제공
                putExtra(StatusActivity.EXTRA_TARGET_APP, targetApp)
                putExtra(StatusActivity.EXTRA_DELAY_SECONDS, 5)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 0, wakeUpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, WAKE_UP_CHANNEL_ID)
                .setContentTitle("충전 연결됨")
                .setContentText("네비게이션 실행 준비 중…")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            
            notificationManager.notify(WAKE_UP_NOTIFICATION_ID, notification)
            
            // 2초 후 알림 자동 제거 (딜레이 시간과 맞춤)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    notificationManager.cancel(WAKE_UP_NOTIFICATION_ID)
                    Log.d(TAG, "Wake up notification removed after 2 seconds")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing wake up notification", e)
                }
            }, 2000)
            
            Log.d(TAG, "Wake up notification shown and will be removed in 2 seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing wake up notification", e)
        }
    }
    
    private fun setupBackgroundPersistence() {
        try {
            Log.d(TAG, "Setting up enhanced background persistence (MIUI-only)")
            
            // 1. 배터리 상태 모니터링만 기본으로 사용
            startBatteryMonitoring()

            // 2. MIUI 단말에서만 강한 백그라운드 유지 기능 활성화
            if (SystemUtils.isMiui()) {
                acquireWakeLock()
                setupAlarmManager()
                scheduleBackgroundJob()
                Log.d(TAG, "MIUI persistence features enabled")
            } else {
                Log.d(TAG, "Non-MIUI device: persistence features disabled")
            }
            
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
                    // 주기적으로 배터리 상태를 확인하여 이벤트 놓침 방지 (MIUI에서만 자주)
                    val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = registerReceiver(null, intentFilter)
                    
                    if (batteryStatus != null) {
                        val status = batteryStatus.getIntExtra("status", -1)
                        val isCharging = status == 2 || status == 5 // CHARGING or FULL
                        val level = batteryStatus.getIntExtra("level", -1)
                        val scale = batteryStatus.getIntExtra("scale", -1)
                        
                        Log.d(TAG, "Periodic battery check - Level: ${level * 100 / scale}%, Charging: $isCharging")
                    }
                    
                        // 다음 체크 예약 주기: MIUI는 짧게, 그 외는 길게(5분)
                        val next = if (SystemUtils.isMiui()) BATTERY_CHECK_INTERVAL else 5 * 60 * 1000L
                        batteryCheckHandler.postDelayed(this, next)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic battery check", e)
                    // 오류가 발생해도 계속 체크
                    val next = if (SystemUtils.isMiui()) BATTERY_CHECK_INTERVAL else 5 * 60 * 1000L
                    batteryCheckHandler.postDelayed(this, next)
                }
            }
        }
        
        batteryCheckHandler.postDelayed(batteryCheckRunnable, BATTERY_CHECK_INTERVAL)
        Log.d(TAG, "Periodic battery check started")
    }
    
    private fun setupAlarmManager() {
        try {
            Log.d(TAG, "Setting up AlarmManager for MIUI bypass (MIUI-only)")
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // MIUI 백그라운드 제한 우회용 AlarmManager PendingIntent 생성
            val alarmIntent = Intent(this, MIUIBatteryCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 
                ALARM_REQUEST_CODE, 
                alarmIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (SystemUtils.isMiui()) {
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
                    .setPeriodic(1 * 60 * 1000L) // 백그라운드 감지를 위해 1분으로 더 단축
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
            val merged = PreferencesBridge.readValues(context)
            val serviceEnabled = merged.serviceEnabled
            val targetApp = merged.targetApp
            if (!serviceEnabled) return
            
            if (serviceEnabled && targetApp != null) {
                if (isConnected) {
                    Log.d(TAG, "Handling power connection in service → 5초 후 풀스크린 알림으로 UI 표시")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (LaunchGuard.canShow(context)) {
                            LaunchGuard.markStart(context)
                            showWakeUpNotification(context, targetApp)
                        } else {
                            Log.w(TAG, "Skip wakeup notification: cooldown or in progress")
                        }
                    }, 5000)
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
    
    private fun startNotificationUpdateHandler() {
        notificationUpdateRunnable = object : Runnable {
            override fun run() {
                try {
                    // 알림 업데이트
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                    
                    // 30초마다 알림 업데이트
                    notificationUpdateHandler.postDelayed(this, 30000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating notification", e)
                }
            }
        }
        notificationUpdateHandler.post(notificationUpdateRunnable!!)
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
            notificationUpdateHandler.removeCallbacksAndMessages(null)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                jobScheduler?.cancel(JOB_ID)
                Log.d(TAG, "Background job cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
} 