package com.agent.mobileagent.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志工具类
 */
object LogUtils {
    
    private const val TAG = "MobileAgent"
    
    // 日志回调，用于更新UI
    var logCallback: ((String) -> Unit)? = null
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun d(message: String) {
        Log.d(TAG, message)
        notifyLog("D", message)
    }
    
    fun i(message: String) {
        Log.i(TAG, message)
        notifyLog("I", message)
    }
    
    fun w(message: String) {
        Log.w(TAG, message)
        notifyLog("W", message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        notifyLog("E", message)
        throwable?.let {
            notifyLog("E", it.stackTraceToString().take(500))
        }
    }
    
    private fun notifyLog(level: String, message: String) {
        val time = dateFormat.format(Date())
        val formattedLog = "[$time][$level] $message"
        logCallback?.invoke(formattedLog)
    }
    
    fun action(actionType: String, detail: String = "") {
        val msg = if (detail.isNotEmpty()) "执行动作: $actionType - $detail" else "执行动作: $actionType"
        i(msg)
    }
    
    fun model(message: String) {
        i("模型响应: $message")
    }
}
