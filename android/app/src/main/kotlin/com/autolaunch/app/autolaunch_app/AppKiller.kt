package com.autolaunch.app.autolaunch_app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException

class AppKiller {
    
    companion object {
        private const val TAG = "AppKiller"
        
        fun killApp(context: Context, packageName: String, callback: ((Boolean) -> Unit)? = null) {
            Log.d(TAG, "🔥 Starting comprehensive app killing for: $packageName")
            LogManager.getInstance().logAndSave(context, TAG, "🔥 KILLING APP: $packageName")
            
            var killSuccessful = false
            
            // 방법 1: ActivityManager를 통한 강제 종료
            killSuccessful = tryKillBackgroundProcesses(context, packageName) || killSuccessful
            
            // 방법 2: 앱 태스크 제거
            killSuccessful = tryRemoveAppTasks(context, packageName) || killSuccessful
            
            // 방법 3: 앱에 종료 브로드캐스트 전송
            killSuccessful = trySendKillBroadcast(context, packageName) || killSuccessful
            
            // 방법 4: 홈 화면으로 강제 이동
            killSuccessful = tryForceToHome(context) || killSuccessful
            
            // 방법 5: 시스템 명령어를 통한 강제 종료 (루트 권한 불필요)
            Handler(Looper.getMainLooper()).postDelayed({
                val shellKillResult = tryShellKill(context, packageName)
                val finalResult = killSuccessful || shellKillResult
                callback?.invoke(finalResult)
                
                LogManager.getInstance().logAndSave(context, TAG, "🔥 APP KILL RESULT: $finalResult")
            }, 500)
        }
        
        private fun tryKillBackgroundProcesses(context: Context, packageName: String): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.killBackgroundProcesses(packageName)
                Log.d(TAG, "🔥 Killed background processes for: $packageName")
                LogManager.getInstance().logAndSave(context, TAG, "🔥 Background processes killed: $packageName")
                true
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Failed to kill background processes", e)
                LogManager.getInstance().logAndSave(context, TAG, "🔥 Background kill failed: ${e.message}", "E")
                false
            }
        }
        
        private fun tryRemoveAppTasks(context: Context, packageName: String): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = activityManager.appTasks
                var removed = false
                
                for (task in tasks) {
                    try {
                        val taskInfo = task.taskInfo
                        if (taskInfo.baseActivity?.packageName == packageName) {
                            task.finishAndRemoveTask()
                            removed = true
                            Log.d(TAG, "🔥 Removed task for: $packageName")
                            LogManager.getInstance().logAndSave(context, TAG, "🔥 Task removed: $packageName")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "🔥 Error removing task", e)
                    }
                }
                
                removed
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Failed to remove app tasks", e)
                false
            }
        }
        
        private fun trySendKillBroadcast(context: Context, packageName: String): Boolean {
            return try {
                // 표준 종료 브로드캐스트들
                val broadcastsToSend = listOf(
                    "android.intent.action.CLOSE_SYSTEM_DIALOGS",
                    "$packageName.ACTION_FORCE_CLOSE",
                    "android.intent.action.APPLICATION_TERMINATE",
                    "com.autolaunch.KILL_TARGET_APP"
                )
                
                broadcastsToSend.forEach { action ->
                    try {
                        val killIntent = Intent(action)
                        killIntent.putExtra("package_name", packageName)
                        killIntent.`package` = packageName
                        context.sendBroadcast(killIntent)
                        Log.d(TAG, "🔥 Sent kill broadcast: $action to $packageName")
                    } catch (e: Exception) {
                        Log.w(TAG, "🔥 Failed to send broadcast: $action", e)
                    }
                }
                
                LogManager.getInstance().logAndSave(context, TAG, "🔥 Kill broadcasts sent: $packageName")
                true
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Failed to send kill broadcasts", e)
                false
            }
        }
        
        private fun tryForceToHome(context: Context): Boolean {
            return try {
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                
                context.startActivity(homeIntent)
                Log.d(TAG, "🔥 Forced to home screen")
                LogManager.getInstance().logAndSave(context, TAG, "🔥 Forced to home screen")
                true
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Failed to force to home", e)
                false
            }
        }
        
        private fun tryShellKill(context: Context, packageName: String): Boolean {
            return try {
                // am force-stop 명령어 시도 (루트 권한 불필요)
                val commands = listOf(
                    "am force-stop $packageName",
                    "am kill $packageName",
                    "killall $packageName"
                )
                
                var success = false
                commands.forEach { command ->
                    try {
                        val process = Runtime.getRuntime().exec("sh -c '$command'")
                        val exitCode = process.waitFor()
                        if (exitCode == 0) {
                            success = true
                            Log.d(TAG, "🔥 Shell command successful: $command")
                            LogManager.getInstance().logAndSave(context, TAG, "🔥 Shell kill success: $command")
                        } else {
                            Log.w(TAG, "🔥 Shell command failed with exit code $exitCode: $command")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "🔥 Shell command exception: $command", e)
                    }
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Shell kill failed", e)
                false
            }
        }
        
        fun isAppRunning(context: Context, packageName: String): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningApps = activityManager.runningAppProcesses
                
                runningApps?.any { processInfo ->
                    processInfo.processName.contains(packageName) ||
                    processInfo.pkgList.contains(packageName)
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if app is running", e)
                false
            }
        }
    }
} 