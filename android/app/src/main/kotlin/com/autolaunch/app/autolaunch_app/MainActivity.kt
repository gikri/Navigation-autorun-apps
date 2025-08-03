package com.autolaunch.app.autolaunch_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager

class MainActivity : FlutterActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL = "com.autolaunch.app/permissions"
        private const val APP_DETECTION_CHANNEL = "com.autolaunch.app/app_detection"
    }
    
    private lateinit var methodChannel: MethodChannel
    private lateinit var appDetectionChannel: MethodChannel
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "requestPermissions" -> {
                    requestAllPermissions()
                    result.success(true)
                }
                "requestSystemAlertWindow" -> {
                    requestSystemAlertWindowPermission()
                    result.success(true)
                }
                "checkPermissions" -> {
                    checkPermissions(result)
                }
                "openBatteryOptimizationSettings" -> {
                    openBatteryOptimizationSettings()
                    result.success(true)
                }
                "requestWriteSettings" -> {
                    requestWriteSettingsPermission()
                    result.success(true)
                }
                "requestAccessibilityService" -> {
                    requestAccessibilityServicePermission()
                    result.success(true)
                }
                "requestDeviceAdmin" -> {
                    requestDeviceAdminPermission()
                    result.success(true)
                }
                "closeApp" -> {
                    // 앱 종료 신호 처리
                    handleAppClose()
                    result.success(true)
                }
                "forceBatteryOptimization" -> {
                    // 배터리 최적화 강제 추가
                    forceBatteryOptimizationWhitelist()
                    result.success(true)
                }
                "checkCriticalPermissions" -> {
                    // 중요 권한 체크 및 자동 요청
                    checkAndRequestCriticalPermissions(result)
                }
                "getLogFile" -> {
                    // 로그 파일 내용을 읽어서 반환
                    getLogFileContent(result)
                }
                "clearLogs" -> {
                    // 로그 파일 삭제
                    LogManager.getInstance().clearLogs(this)
                    result.success(true)
                }
                "requestXiaomiAutostart" -> {
                    // 샤오미 자동 실행 권한 요청
                    requestXiaomiAutostartPermission()
                    result.success(true)
                }
                "checkDeviceVendor" -> {
                    // 기기 제조사 정보 확인
                    val deviceInfo = getDeviceVendorInfo()
                    result.success(deviceInfo)
                }
                "openMIUISettings" -> {
                    // MIUI 설정 가이드 열기
                    val settingType = call.argument<String>("type") ?: "autostart"
                    openMIUISpecificSettings(settingType)
                    result.success(true)
                }
                "checkMIUIPermissions" -> {
                    // MIUI 특별 권한 상태 체크
                    val miuiStatus = checkMIUISpecificPermissions()
                    result.success(miuiStatus)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
        
        // 앱 감지 채널 설정
        appDetectionChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, APP_DETECTION_CHANNEL)
        appDetectionChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "isAppInstalled" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val isInstalled = isAppInstalled(packageName)
                        result.success(isInstalled)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "getAppInfo" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        val appInfo = getAppInfo(packageName)
                        result.success(appInfo)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 백그라운드 서비스 시작
        AutoLaunchService.startService(this)
        
        // 서비스 활성화 상태를 기본값으로 설정
        val prefs = getSharedPreferences("autolaunch_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("service_enabled")) {
            prefs.edit().putBoolean("service_enabled", true).apply()
        }
        
        // 충전 해제 시 앱 종료 신호 처리
        handleCloseSignal()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCloseSignal()
    }
    
    private fun handleCloseSignal() {
        val action = intent.getStringExtra("action")
        if (action == "close_app") {
            Log.d(TAG, "Received close signal from PowerConnectionReceiver")
            // Flutter에 closeApp 메서드 호출 신호 전송
            Handler(Looper.getMainLooper()).post {
                try {
                    methodChannel.invokeMethod("closeApp", null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking closeApp method", e)
                }
            }
        }
    }
    
    private fun requestAllPermissions() {
        Log.d(TAG, "Requesting all necessary permissions")
        
        // 배터리 최적화 제외 요청
        openBatteryOptimizationSettings()
        
        // 시스템 오버레이 권한 요청
        requestSystemAlertWindowPermission()
        
        // WRITE_SETTINGS 권한 요청
        requestWriteSettingsPermission()
        
        // 접근성 서비스 권한 요청
        requestAccessibilityServicePermission()
        
        // 디바이스 관리자 권한 요청
        requestDeviceAdminPermission()
    }
    
    private fun checkPermissions(result: MethodChannel.Result) {
        val permissions = mutableMapOf<String, Boolean>()
        
        try {
            // 배터리 최적화 권한 확인
            permissions["battery_optimization"] = isBatteryOptimizationIgnored()
            Log.d(TAG, "Battery optimization status: ${permissions["battery_optimization"]}")
            
            // 시스템 알림 창 권한 확인
            permissions["system_alert_window"] = isSystemAlertWindowPermissionGranted()
            Log.d(TAG, "System alert window status: ${permissions["system_alert_window"]}")
            
            // 시스템 설정 변경 권한 확인
            permissions["write_settings"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(this)
            } else {
                true
            }
            Log.d(TAG, "Write settings status: ${permissions["write_settings"]}")
            
            // 접근성 서비스 권한 확인
            permissions["accessibility_service"] = isAccessibilityServiceEnabled()
            Log.d(TAG, "Accessibility service status: ${permissions["accessibility_service"]}")
            
            // 디바이스 관리자 권한 확인
            permissions["device_admin"] = isDeviceAdminActive()
            Log.d(TAG, "Device admin status: ${permissions["device_admin"]}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            // 오류 발생 시 모든 권한을 false로 설정
            permissions["battery_optimization"] = false
            permissions["system_alert_window"] = false
            permissions["write_settings"] = false
            permissions["accessibility_service"] = false
            permissions["device_admin"] = false
        }
        
        Log.d(TAG, "Final permission check results: $permissions")
        result.success(permissions)
    }
    
    private fun isWriteSettingsPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            true
        }
    }
    
    private fun isDeviceAdminActive(): Boolean {
        return try {
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
            val isActive = devicePolicyManager.isAdminActive(componentName)
            Log.d(TAG, "Device admin status check - Component: $componentName, Active: $isActive")
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin status", e)
            false
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization settings", e)
            Toast.makeText(this, "배터리 최적화 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSystemAlertWindowPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestSystemAlertWindowPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting system alert window permission", e)
            Toast.makeText(this, "시스템 오버레이 권한을 요청할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestWriteSettingsPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting write settings permission", e)
            Toast.makeText(this, "시스템 설정 변경 권한을 요청할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestAccessibilityServicePermission() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "접근성 서비스에서 AutoLaunch를 활성화해주세요.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting accessibility service permission", e)
            Toast.makeText(this, "접근성 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val serviceName = "${packageName}/${AutoLaunchAccessibilityService::class.java.name}"
            val isEnabled = enabledServices?.contains(serviceName) == true
            Log.d(TAG, "Accessibility service status: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            false
        }
    }

    private fun requestDeviceAdminPermission() {
        try {
            Log.d(TAG, "=== Device Admin Permission Request Started ===")
            
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
            
            Log.d(TAG, "Package name: ${packageName}")
            Log.d(TAG, "Component name: $componentName")
            Log.d(TAG, "DeviceAdminReceiver class: ${DeviceAdminReceiver::class.java.name}")
            Log.d(TAG, "DeviceAdminReceiver canonical name: ${DeviceAdminReceiver::class.java.canonicalName}")
            
            // 현재 활성화된 모든 디바이스 관리자 확인
            val activeAdmins = devicePolicyManager.activeAdmins
            Log.d(TAG, "Active device admins: $activeAdmins")
            
            val isCurrentlyActive = devicePolicyManager.isAdminActive(componentName)
            Log.d(TAG, "Current device admin status: $isCurrentlyActive")
            
            // 강제로 권한 요청 (디버깅용)
            Log.d(TAG, "Force requesting device admin permission for debugging")
            
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                "AutoLaunch 앱이 충전 연결 해제 시 화면을 자동으로 끄기 위해 디바이스 관리자 권한이 필요합니다.")
            
            // 플래그 추가
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            
            Log.d(TAG, "Intent action: ${intent.action}")
            Log.d(TAG, "Intent component extra: ${intent.getParcelableExtra<ComponentName>(DevicePolicyManager.EXTRA_DEVICE_ADMIN)}")
            Log.d(TAG, "Intent explanation: ${intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION)}")
            Log.d(TAG, "Intent flags: ${intent.flags}")
            
            // Intent 실행 가능 여부 확인
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            Log.d(TAG, "Intent resolve info: $resolveInfo")
            
            if (resolveInfo != null) {
                Log.d(TAG, "Starting device admin request activity")
                startActivity(intent)
                Toast.makeText(this, "디바이스 관리자 권한 설정 화면을 열었습니다.", Toast.LENGTH_LONG).show()
            } else {
                Log.e(TAG, "No activity found to handle device admin request")
                Toast.makeText(this, "디바이스 관리자 설정을 열 수 없습니다. 기기에서 지원하지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                
                // 대안: 설정 앱의 디바이스 관리자 섹션으로 이동
                try {
                    val settingsIntent = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(settingsIntent)
                    Log.d(TAG, "Opened device admin settings as fallback")
                    Toast.makeText(this, "디바이스 관리자 설정에서 AutoLaunch를 활성화해주세요.", Toast.LENGTH_LONG).show()
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Fallback to device admin settings failed", fallbackError)
                    Toast.makeText(this, "디바이스 관리자 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting device admin permission", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Toast.makeText(this, "디바이스 관리자 권한 요청 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleAppClose() {
        Log.d(TAG, "Handling app close")
        
        // 1. 현재 실행 중인 타겟 앱 확인 및 종료
        val prefs = getSharedPreferences("autolaunch_prefs", Context.MODE_PRIVATE)
        val targetApp = prefs.getString("target_app", null)
        
        if (targetApp != null) {
            forceCloseTargetApp(targetApp)
        }
        
        // 2. 딜레이 후 홈 화면으로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            moveToHomeScreen()
        }, 1000)
        
        // 3. 현재 앱도 백그라운드로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            moveTaskToBack(true)
        }, 2000)
    }
    
    private fun forceCloseTargetApp(packageName: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
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
            
            Log.d(TAG, "Force closed target app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error force closing target app: $packageName", e)
        }
    }
    
    private fun moveToHomeScreen() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Log.d(TAG, "Moved to home screen")
        } catch (e: Exception) {
            Log.e(TAG, "Error moving to home screen", e)
        }
    }

    // 타겟 쿼리 방식으로 앱 설치 여부 확인
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            Log.d(TAG, "App not installed: $packageName")
            false
        }
    }

    // 앱 정보 가져오기
    private fun getAppInfo(packageName: String): Map<String, Any>? {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            
            mapOf(
                "appName" to packageManager.getApplicationLabel(applicationInfo).toString(),
                "versionName" to (packageInfo.versionName ?: "Unknown"),
                "versionCode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app info for $packageName", e)
            null
        }
    }
    
    private fun forceBatteryOptimizationWhitelist() {
        try {
            Log.d(TAG, "Force adding app to battery optimization whitelist")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    // 직접 화이트리스트 추가 시도 (루트 권한 없이는 불가능하지만 시도)
                    try {
                        val intent = Intent()
                        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        intent.data = Uri.parse("package:$packageName")
                        
                        // 추가적으로 시스템 설정으로 이동하는 인텐트도 시도
                        val alternativeIntent = Intent()
                        alternativeIntent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        
                        // 우선 직접 요청 시도
                        startActivity(intent)
                        
                        // 1초 후 시스템 설정 화면도 열기
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                startActivity(alternativeIntent)
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not open battery optimization settings", e)
                            }
                        }, 1000)
                        
                        Toast.makeText(this, "배터리 최적화에서 이 앱을 허용해주세요", Toast.LENGTH_LONG).show()
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting battery optimization whitelist", e)
                        
                        // 대안으로 일반 배터리 설정 열기
                        try {
                            val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(settingsIntent)
                            Toast.makeText(this, "배터리 최적화 설정에서 이 앱을 허용해주세요", Toast.LENGTH_LONG).show()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Could not open any battery settings", e2)
                            Toast.makeText(this, "설정 > 배터리 > 앱 배터리 사용량 최적화에서 이 앱을 허용해주세요", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Log.d(TAG, "App is already in battery optimization whitelist")
                    Toast.makeText(this, "이미 배터리 최적화에서 제외되어 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in force battery optimization whitelist", e)
        }
    }
    
    private fun checkAndRequestCriticalPermissions(result: MethodChannel.Result) {
        try {
            Log.d(TAG, "Checking 4 core permissions for locked-screen operation")
            
            val criticalIssues = mutableListOf<String>()
            val permissionStatus = mutableMapOf<String, Boolean>()
            
            // 레퍼런스 앱 기준 4가지 핵심 권한 체크
            
            // 1. 시스템 설정 수정 허용 (WRITE_SETTINGS)
            val canWriteSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(this)
            } else {
                true
            }
            permissionStatus["write_settings"] = canWriteSettings
            if (!canWriteSettings) {
                criticalIssues.add("write_settings")
                Log.w(TAG, "Write settings permission not granted")
            }
            
            // 2. 배터리 제한 해제
            val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
            permissionStatus["battery_optimization"] = batteryOptimized
            if (!batteryOptimized) {
                criticalIssues.add("battery_optimization")
                Log.w(TAG, "Battery optimization not disabled")
            }
            
            // 3. 다른앱 위에 그리기 권한 (SYSTEM_ALERT_WINDOW)
            val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
            permissionStatus["system_alert_window"] = canDrawOverlays
            if (!canDrawOverlays) {
                criticalIssues.add("system_alert_window")
                Log.w(TAG, "System alert window permission not granted")
            }
            
            // 4. 접근성 권한 (주요 권한으로 승격)
            val hasAccessibilityService = isAccessibilityServiceEnabled()
            permissionStatus["accessibility_service"] = hasAccessibilityService
            if (!hasAccessibilityService) {
                criticalIssues.add("accessibility_service")
                Log.w(TAG, "Accessibility service permission not granted")
            }
            
            val issueCount = criticalIssues.size
            val totalCorePermissions = 4
            val grantedCount = totalCorePermissions - issueCount
            
            Log.d(TAG, "Core permission status: $grantedCount/$totalCorePermissions granted")
            Log.d(TAG, "Missing permissions: $criticalIssues")
            
            if (issueCount > 0) {
                // 순차적으로 중요한 권한들 자동 요청
                var delay = 0L
                
                if (criticalIssues.contains("write_settings")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestWriteSettingsPermission()
                    }, delay)
                    delay += 2000
                }
                
                if (criticalIssues.contains("battery_optimization")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        forceBatteryOptimizationWhitelist()
                    }, delay)
                    delay += 2000
                }
                
                if (criticalIssues.contains("system_alert_window")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestSystemAlertWindowPermission()
                    }, delay)
                    delay += 2000
                }
                
                if (criticalIssues.contains("accessibility_service")) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestAccessibilityServicePermission()
                    }, delay)
                }
                
                result.success(mapOf(
                    "hasIssues" to true,
                    "issues" to criticalIssues,
                    "grantedCount" to grantedCount,
                    "totalCount" to totalCorePermissions,
                    "permissionStatus" to permissionStatus,
                    "message" to "잠금 상태 실행을 위해 $issueCount 개의 핵심 권한이 필요합니다. ($grantedCount/$totalCorePermissions 완료)"
                ))
            } else {
                Log.d(TAG, "All 4 core permissions are granted - ready for locked screen operation!")
                result.success(mapOf(
                    "hasIssues" to false,
                    "grantedCount" to grantedCount,
                    "totalCount" to totalCorePermissions,
                    "permissionStatus" to permissionStatus,
                    "message" to "모든 핵심 권한이 허용되어 있습니다! 잠금 상태에서도 정상 작동합니다. ✅"
                ))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking critical permissions", e)
            result.error("PERMISSION_CHECK_ERROR", "Critical permission check failed", e.message)
        }
    }
    
    private fun getDeviceVendorInfo(): Map<String, Any> {
        return try {
            val manufacturer = Build.MANUFACTURER.uppercase()
            val model = Build.MODEL
            val brand = Build.BRAND.uppercase()
            
            // 제조사별 특별 권한 필요 여부 판단
            val isXiaomi = manufacturer.contains("XIAOMI") || brand.contains("XIAOMI") || brand.contains("REDMI")
            val isHuawei = manufacturer.contains("HUAWEI") || brand.contains("HUAWEI") || brand.contains("HONOR")
            val isOppo = manufacturer.contains("OPPO") || brand.contains("OPPO") || brand.contains("ONEPLUS")
            val isSamsung = manufacturer.contains("SAMSUNG")
            val isVivo = manufacturer.contains("VIVO")
            
            Log.d(TAG, "Device info - Manufacturer: $manufacturer, Brand: $brand, Model: $model")
            
            mapOf(
                "manufacturer" to manufacturer,
                "model" to model,
                "brand" to brand,
                "isXiaomi" to isXiaomi,
                "isHuawei" to isHuawei,
                "isOppo" to isOppo,
                "isSamsung" to isSamsung,
                "isVivo" to isVivo,
                "needsAutostartPermission" to isXiaomi,
                "needsPowerManagerPermission" to (isHuawei || isOppo),
                "vendorName" to when {
                    isXiaomi -> "샤오미/레드미"
                    isHuawei -> "화웨이/Honor"
                    isOppo -> "OPPO/OnePlus"
                    isSamsung -> "삼성"
                    isVivo -> "Vivo"
                    else -> "기타"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device vendor info", e)
            mapOf(
                "manufacturer" to "UNKNOWN",
                "model" to "UNKNOWN",
                "brand" to "UNKNOWN",
                "isXiaomi" to false,
                "isHuawei" to false,
                "isOppo" to false,
                "isSamsung" to false,
                "isVivo" to false,
                "needsAutostartPermission" to false,
                "needsPowerManagerPermission" to false,
                "vendorName" to "알 수 없음"
            )
        }
    }
    
    private fun requestXiaomiAutostartPermission() {
        try {
            Log.d(TAG, "Requesting Xiaomi autostart permission")
            
            val deviceInfo = getDeviceVendorInfo()
            if (!(deviceInfo["isXiaomi"] as Boolean)) {
                Toast.makeText(this, "샤오미 디바이스가 아닙니다.", Toast.LENGTH_SHORT).show()
                return
            }
            
            // MIUI 자동 실행 설정 페이지로 이동하는 여러 가지 방법 시도
            val autostartIntents = listOf(
                // MIUI 12+ 자동 실행 설정
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                },
                // MIUI 구버전 자동 실행 설정
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.powermanager.ui.HiddenAppsConfigActivity")
                },
                // MIUI 보안센터 메인
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.securitycenter.MainActivity")
                },
                // 대안: 앱 정보 화면
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            
            var intentLaunched = false
            
            for (intent in autostartIntents) {
                try {
                    val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolveInfo != null) {
                        startActivity(intent)
                        intentLaunched = true
                        
                        val message = when (autostartIntents.indexOf(intent)) {
                            0 -> "MIUI 자동 실행 설정을 열었습니다. '${getString(R.string.app_name)}'를 찾아 허용해주세요."
                            1 -> "MIUI 전원 관리 설정을 열었습니다. 자동 실행을 허용해주세요."
                            2 -> "MIUI 보안센터를 열었습니다. 자동 실행 설정을 찾아 허용해주세요."
                            else -> "앱 설정을 열었습니다. 배터리 및 권한 설정을 확인해주세요."
                        }
                        
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        Log.d(TAG, "Successfully opened Xiaomi autostart settings with intent: ${intent.component}")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to launch intent: ${intent.component}", e)
                    continue
                }
            }
            
            if (!intentLaunched) {
                // 모든 자동화된 방법이 실패한 경우 수동 안내
                Toast.makeText(this, 
                    "설정 > 앱 관리 > ${getString(R.string.app_name)} > 자동 실행을 허용해주세요", 
                    Toast.LENGTH_LONG).show()
                Log.w(TAG, "All autostart intents failed, showing manual instruction")
                
                // 대안으로 설정 메인 화면 열기
                try {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(settingsIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open settings", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Xiaomi autostart permission", e)
            Toast.makeText(this, "샤오미 자동 실행 설정을 열 수 없습니다. 수동으로 설정해주세요.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun openMIUISpecificSettings(settingType: String) {
        try {
            val deviceInfo = getDeviceVendorInfo()
            if (!(deviceInfo["isXiaomi"] as Boolean)) {
                Toast.makeText(this, "샤오미 디바이스가 아닙니다.", Toast.LENGTH_SHORT).show()
                return
            }
            
            when (settingType) {
                "autostart" -> requestXiaomiAutostartPermission()
                "background" -> openBackgroundActivitySettings()
                "power" -> openPowerManagementSettings()
                "notification" -> openNotificationSettings()
                "battery" -> openBatteryOptimizationSettings()
                else -> {
                    // 전체 MIUI 설정 안내
                    showMIUISettingsGuide()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening MIUI specific settings", e)
        }
    }
    
    private fun openBackgroundActivitySettings() {
        try {
            // MIUI 백그라운드 활동 설정
            val intents = listOf(
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity")
                    putExtra("package_name", packageName)
                },
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.appmanager.BackgroundStartActivity")
                },
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            
            var launched = false
            for (intent in intents) {
                try {
                    startActivity(intent)
                    launched = true
                    Toast.makeText(this, "백그라운드 활동 → 제한 없음으로 설정해주세요", Toast.LENGTH_LONG).show()
                    break
                } catch (e: Exception) {
                    continue
                }
            }
            
            if (!launched) {
                Toast.makeText(this, "설정 > 앱 관리 > ${getString(R.string.app_name)} > 백그라운드 활동 → 제한 없음", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening background activity settings", e)
        }
    }
    
    private fun openPowerManagementSettings() {
        try {
            // MIUI 전원 관리 설정
            val intents = listOf(
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.powermanager.ui.HiddenAppsConfigActivity")
                },
                Intent().apply {
                    component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                },
                Intent().apply {
                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            )
            
            var launched = false
            for (intent in intents) {
                try {
                    startActivity(intent)
                    launched = true
                    Toast.makeText(this, "전원 관리에서 ${getString(R.string.app_name)} → 제한 없음으로 설정해주세요", Toast.LENGTH_LONG).show()
                    break
                } catch (e: Exception) {
                    continue
                }
            }
            
            if (!launched) {
                Toast.makeText(this, "설정 > 배터리 > 전원 관리 > ${getString(R.string.app_name)} → 제한 없음", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening power management settings", e)
        }
    }
    
    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, "autolaunch_wakeup_channel")
            }
            
            try {
                startActivity(intent)
                Toast.makeText(this, "잠금 화면 알림을 허용해주세요", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // 대안으로 일반 설정 열기
                val settingsIntent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(settingsIntent)
                Toast.makeText(this, "알림 설정에서 잠금 화면 알림을 허용해주세요", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings", e)
        }
    }
    
    private fun showMIUISettingsGuide() {
        val message = """
            MIUI 백그라운드 실행을 위해 다음 5가지 설정이 필요합니다:
            
            1️⃣ 자동 실행 허용
            2️⃣ 백그라운드 활동 → 제한 없음
            3️⃣ 전원 관리 → 제한 없음
            4️⃣ 잠금 화면 알림 허용
            5️⃣ 배터리 최적화 제외
            
            각 설정을 차례로 완료해주세요.
        """.trimIndent()
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "MIUI settings guide displayed")
    }
    
    private fun checkMIUISpecificPermissions(): Map<String, Any> {
        return try {
            val deviceInfo = getDeviceVendorInfo()
            val isXiaomi = deviceInfo["isXiaomi"] as Boolean
            
            if (!isXiaomi) {
                return mapOf(
                    "isXiaomi" to false,
                    "message" to "샤오미 디바이스가 아닙니다"
                )
            }
            
            // MIUI 권한 상태 체크 (정확한 체크는 어려우므로 가이드만 제공)
            val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
            
            val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
            
            val hasAccessibility = isAccessibilityServiceEnabled()
            val hasDeviceAdmin = isDeviceAdminActive()
            
            mapOf(
                "isXiaomi" to true,
                "batteryOptimized" to batteryOptimized,
                "systemOverlay" to canDrawOverlays,
                "accessibility" to hasAccessibility,
                "deviceAdmin" to hasDeviceAdmin,
                "autostart" to false, // MIUI 자동 실행은 직접 확인 불가
                "backgroundActivity" to false, // MIUI 백그라운드 활동은 직접 확인 불가
                "powerManagement" to false, // MIUI 전원 관리는 직접 확인 불가
                "lockscreenNotification" to false, // 잠금 화면 알림은 직접 확인 불가
                "message" to "MIUI 특별 권한들은 수동으로 설정 후 확인해주세요",
                "needsManualSetup" to true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MIUI specific permissions", e)
            mapOf(
                "error" to true,
                "message" to "MIUI 권한 확인 중 오류가 발생했습니다"
            )
        }
    }
    
    private fun getLogFileContent(result: MethodChannel.Result) {
        try {
            val logFile = LogManager.getInstance().getLogFile(this)
            if (logFile.exists()) {
                val content = logFile.readText()
                result.success(content)
                Log.d(TAG, "Log file content retrieved, size: ${content.length} characters")
            } else {
                result.success("로그 파일이 존재하지 않습니다.")
                Log.d(TAG, "Log file does not exist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file", e)
            result.error("LOG_ERROR", "로그 파일 읽기 오류: ${e.message}", null)
        }
    }
}
