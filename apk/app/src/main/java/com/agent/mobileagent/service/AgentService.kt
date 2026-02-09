package com.agent.mobileagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.agent.mobileagent.MainActivity
import com.agent.mobileagent.R
import com.agent.mobileagent.model.ActionResponse
import com.agent.mobileagent.model.ActionType
import com.agent.mobileagent.model.TaskStatus
import com.agent.mobileagent.network.AgentApiClient
import com.agent.mobileagent.util.LogUtils
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/**
 * Agent后台服务
 * 
 * 负责：
 * 1. 前台服务保活
 * 2. 任务执行循环
 * 3. 协调截图、模型调用、动作执行
 */
class AgentService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "AgentServiceChannel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_TASK = "com.agent.mobileagent.START_TASK"
        const val ACTION_STOP_TASK = "com.agent.mobileagent.STOP_TASK"
        const val EXTRA_QUERY = "query"
        
        // 最大执行步数
        private const val MAX_STEPS = 100
        // 每步之间的延迟（毫秒）
        private const val STEP_DELAY = 1000L
    }
    
    private val binder = AgentBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val apiClient = AgentApiClient()
    private val gson = Gson()
    
    // 任务状态
    private var currentJob: Job? = null
    private var taskStatus = TaskStatus.IDLE
    private var currentQuery = ""
    private val historyModel = mutableListOf<String>()
    
    // 状态回调
    var statusCallback: ((TaskStatus, String) -> Unit)? = null
    
    inner class AgentBinder : Binder() {
        fun getService(): AgentService = this@AgentService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LogUtils.i("AgentService已创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TASK -> {
                val query = intent.getStringExtra(EXTRA_QUERY) ?: ""
                if (query.isNotEmpty()) {
                    startTask(query)
                }
            }
            ACTION_STOP_TASK -> {
                stopTask()
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification("等待任务"))
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        LogUtils.i("AgentService已销毁")
    }
    
    /**
     * 开始执行任务
     */
    fun startTask(query: String) {
        if (taskStatus == TaskStatus.RUNNING) {
            LogUtils.w("任务已在执行中")
            return
        }

        currentQuery = query
        historyModel.clear()

        currentJob = serviceScope.launch {
            executeTask(query)
        }
    }
    
    /**
     * 停止任务
     */
    fun stopTask() {
        currentJob?.cancel()
        currentJob = null
        updateStatus(TaskStatus.IDLE, "任务已停止")
        LogUtils.i("任务已手动停止")
    }
    
    /**
     * 获取当前任务状态
     */
    fun getTaskStatus(): TaskStatus = taskStatus
    
    /**
     * 执行任务主循环
     */
    private suspend fun executeTask(query: String) {
        updateStatus(TaskStatus.RUNNING, "开始执行任务: $query")
        LogUtils.i("========== 开始执行任务 ==========")
        LogUtils.i("Query: $query")

        val accessibilityService = AutoAgentAccessibilityService.getInstance()
        if (accessibilityService == null) {
            updateStatus(TaskStatus.ERROR, "无障碍服务未开启")
            LogUtils.e("无障碍服务未开启，无法执行任务")
            return
        }

        // 首先回到桌面
        LogUtils.i("回到桌面...")
        accessibilityService.performHome()
        delay(1500)

        var stepCount = 0

        while (stepCount < MAX_STEPS && coroutineContext.isActive) {
            stepCount++
            LogUtils.i("---------- 第 $stepCount 步 ----------")
            updateNotification("执行中: 第 $stepCount 步")

            try {
                // 1. 截图
                LogUtils.d("正在截图...")
                val base64Image = withTimeoutOrNull(10000) {
                    suspendCancellableCoroutine<String?> { cont ->
                        accessibilityService.takeScreenshot { result ->
                            cont.resume(result) {}
                        }
                    }
                }

                if (base64Image.isNullOrEmpty()) {
                    LogUtils.e("截图失败，跳过此步")
                    delay(STEP_DELAY)
                    continue
                }

                LogUtils.d("截图成功")

                // 2. 调用模型
                LogUtils.d("调用模型...")
                val result = apiClient.getNextAction(base64Image, query, historyModel)

                if (result.isFailure) {
                    LogUtils.e("模型调用失败: ${result.exceptionOrNull()?.message}")
                    delay(STEP_DELAY)
                    continue
                }

                val action = result.getOrNull()!!
                LogUtils.i("模型返回动作: ${action.actionType}")

                // 3. 执行动作
                val actionResult = executeAction(accessibilityService, action)

                // 4. 更新历史记录
                val historyEntry = apiClient.buildHistoryEntry(
                    memory = "",  // 可以从响应中提取
                    reason = "",
                    actionJson = gson.toJson(action)
                )
                historyModel.add(historyEntry)

                // 5. 检查是否完成
                if (actionResult == ActionResult.COMPLETE) {
                    updateStatus(TaskStatus.COMPLETED, "任务执行完成")
                    LogUtils.i("========== 任务完成 ==========")
                    break
                } else if (actionResult == ActionResult.NEED_USER_HELP) {
                    updateStatus(TaskStatus.NEED_USER_HELP, "需要用户协助")
                    LogUtils.i("========== 需要用户协助 ==========")
                    break
                } else if (actionResult == ActionResult.INFEASIBLE) {
                    updateStatus(TaskStatus.ERROR, "任务无法完成")
                    LogUtils.w("========== 任务无法完成 ==========")
                    break
                }

                // 6. 等待下一步
                delay(STEP_DELAY)

            } catch (e: CancellationException) {
                LogUtils.i("任务被取消")
                throw e
            } catch (e: Exception) {
                LogUtils.e("执行步骤出错: ${e.message}", e)
                delay(STEP_DELAY)
            }
        }

        if (stepCount >= MAX_STEPS) {
            updateStatus(TaskStatus.ERROR, "超过最大步数限制")
            LogUtils.w("超过最大步数限制: $MAX_STEPS")
        }
    }

    /**
     * 执行单个动作
     */
    private suspend fun executeAction(
        service: AutoAgentAccessibilityService,
        action: ActionResponse
    ): ActionResult {
        return when (action.actionType) {
            ActionType.CLICK -> {
                val box = action.box2d?.firstOrNull()
                if (box != null && box.size >= 4) {
                    // 计算中心点
                    val centerX = (box[0] + box[2]) / 2
                    val centerY = (box[1] + box[3]) / 2
                    
                    suspendCancellableCoroutine<ActionResult> { cont ->
                        service.performClick(centerX, centerY) { success ->
                            cont.resume(if (success) ActionResult.CONTINUE else ActionResult.ERROR) {}
                        }
                    }
                } else {
                    LogUtils.e("无效的点击坐标")
                    ActionResult.ERROR
                }
            }
            
            ActionType.SWIPE -> {
                val start = action.startPoint
                val end = action.endPoint
                if (start != null && end != null && start.size >= 2 && end.size >= 2) {
                    suspendCancellableCoroutine<ActionResult> { cont ->
                        service.performSwipe(
                            startX = start[0],
                            startY = start[1],
                            endX = end[0],
                            endY = end[1]
                        ) { success ->
                            cont.resume(if (success) ActionResult.CONTINUE else ActionResult.ERROR) {}
                        }
                    }
                } else {
                    LogUtils.e("无效的滑动坐标")
                    ActionResult.ERROR
                }
            }
            
            ActionType.INPUT_TEXT -> {
                val text = action.text ?: ""
                val box = action.box2d?.firstOrNull()
                
                // 如果有输入框坐标，先点击
                if (box != null && box.size >= 4) {
                    val centerX = (box[0] + box[2]) / 2
                    val centerY = (box[1] + box[3]) / 2
                    service.performClick(centerX, centerY)
                    delay(500)
                }
                
                suspendCancellableCoroutine<ActionResult> { cont ->
                    service.inputText(text) { success ->
                        cont.resume(if (success) ActionResult.CONTINUE else ActionResult.ERROR) {}
                    }
                }
            }
            
            ActionType.NAVIGATE_BACK -> {
                suspendCancellableCoroutine<ActionResult> { cont ->
                    service.performBack { success ->
                        cont.resume(if (success) ActionResult.CONTINUE else ActionResult.ERROR) {}
                    }
                }
            }
            
            ActionType.OPEN_APP -> {
                val appName = action.appName ?: ""
                suspendCancellableCoroutine<ActionResult> { cont ->
                    service.openApp(appName) { success ->
                        cont.resume(if (success) ActionResult.CONTINUE else ActionResult.ERROR) {}
                    }
                }
            }
            
            ActionType.WAIT -> {
                delay(1000)
                ActionResult.CONTINUE
            }
            
            ActionType.STATUS -> {
                when (action.goalStatus) {
                    "complete" -> ActionResult.COMPLETE
                    "infeasible" -> ActionResult.INFEASIBLE
                    else -> ActionResult.CONTINUE
                }
            }
            
            ActionType.CALL_USER -> {
                LogUtils.w("需要用户协助: ${action.text}")
                updateStatus(TaskStatus.NEED_USER_HELP, action.text ?: "需要用户协助")
                ActionResult.NEED_USER_HELP
            }
            
            ActionType.ANSWER, ActionType.SHOW_PRODUCT_INFO, ActionType.SAVE_PRODUCT_INFO -> {
                LogUtils.i("输出结果: ${action.text?.take(200)}")
                ActionResult.CONTINUE
            }
            
            else -> {
                LogUtils.w("未知的动作类型: ${action.actionType}")
                ActionResult.CONTINUE
            }
        }
    }
    
    /**
     * 更新任务状态
     */
    private fun updateStatus(status: TaskStatus, message: String) {
        taskStatus = status
        statusCallback?.invoke(status, message)
        updateNotification(message)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Agent服务运行通知"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }
    
    /**
     * 动作执行结果
     */
    private enum class ActionResult {
        CONTINUE,       // 继续执行
        COMPLETE,       // 任务完成
        ERROR,          // 执行出错
        NEED_USER_HELP, // 需要用户协助
        INFEASIBLE      // 任务无法完成
    }
}
