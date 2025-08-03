package com.autolaunch.app.autolaunch_app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class LogManager private constructor() {
    
    companion object {
        private const val TAG = "LogManager"
        private const val LOG_FILE_NAME = "autolaunch_debug.log"
        private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
        
        @Volatile
        private var INSTANCE: LogManager? = null
        
        fun getInstance(): LogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogManager().also { INSTANCE = it }
            }
        }
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun logAndSave(context: Context, tag: String, message: String, level: String = "D") {
        // 일반 로그 출력
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
        }
        
        // 파일에도 저장
        saveToFile(context, tag, message, level)
    }
    
    private fun saveToFile(context: Context, tag: String, message: String, level: String) {
        try {
            val logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
            
            // 파일 크기 체크 및 회전
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                rotateLogFile(logFile)
            }
            
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [$level] [$tag] $message\n"
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
                writer.flush()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving log to file", e)
        }
    }
    
    private fun rotateLogFile(logFile: File) {
        try {
            val backupFile = File(logFile.parentFile, "${LOG_FILE_NAME}.backup")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating log file", e)
        }
    }
    
    fun getLogFile(context: Context): File {
        return File(context.getExternalFilesDir(null), LOG_FILE_NAME)
    }
    
    fun clearLogs(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (logFile.exists()) {
                logFile.delete()
            }
            Log.d(TAG, "Log file cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing log file", e)
        }
    }
} 