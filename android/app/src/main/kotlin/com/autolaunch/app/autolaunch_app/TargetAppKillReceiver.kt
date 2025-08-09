package com.autolaunch.app.autolaunch_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class TargetAppKillReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "TargetAppKillReceiver"
        private const val ACTION_FORCE_CLOSE = "com.autolaunch.FORCE_CLOSE_TARGET_APP"
        
        fun registerReceiver(context: Context) {
            try {
                val filter = IntentFilter(ACTION_FORCE_CLOSE)
                context.registerReceiver(TargetAppKillReceiver(), filter)
                Log.d(TAG, "TargetAppKillReceiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering TargetAppKillReceiver", e)
            }
        }
        
        fun unregisterReceiver(context: Context) {
            try {
                context.unregisterReceiver(TargetAppKillReceiver())
                Log.d(TAG, "TargetAppKillReceiver unregistered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering TargetAppKillReceiver", e)
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(TAG, "Received kill broadcast: ${intent.action}")
            
            when (intent.action) {
                ACTION_FORCE_CLOSE -> {
                    val targetPackage = intent.getStringExtra("package_name")
                    val currentPackage = context.packageName
                    
                    Log.d(TAG, "Target package: $targetPackage, Current package: $currentPackage")
                    
                    // 현재 앱이 타겟 앱인 경우에만 종료
                    if (targetPackage == currentPackage) {
                        Log.d(TAG, "🔥 Self-terminating app: $currentPackage")
                        
                        // 앱 종료 로직
                        try {
                            // 모든 Activity 종료
                            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            
                            // 프로세스 종료
                            android.os.Process.killProcess(android.os.Process.myPid())
                            System.exit(0)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error terminating app", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TargetAppKillReceiver", e)
        }
    }
} 