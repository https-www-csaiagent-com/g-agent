package com.agent.mobileagent

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.agent.mobileagent.databinding.ActivityMainBinding
import com.agent.mobileagent.model.TaskStatus
import com.agent.mobileagent.network.AsrApiClient
import com.agent.mobileagent.service.AgentService
import com.agent.mobileagent.service.AutoAgentAccessibilityService
import com.agent.mobileagent.service.ScreenCaptureService
import com.agent.mobileagent.util.LogUtils
import com.agent.mobileagent.util.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主界面Activity
 * 
 * 功能：
 * 1. 用户输入任务查询
 * 2. 启动/停止任务执行
 * 3. 显示执行日志
 * 4. 管理无障碍服务和截图权限
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // AgentService绑定
    private var agentService: AgentService? = null
    private var isServiceBound = false

    // 输入模式：true=键盘模式，false=语音模式
    private var isKeyboardMode = true

    // 录音相关
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AgentService.AgentBinder
            agentService = binder.getService()
            isServiceBound = true
            
            // 设置状态回调
            agentService?.statusCallback = { status, message ->
                runOnUiThread {
                    updateUIForStatus(status, message)
                }
            }
            
            LogUtils.d("AgentService已绑定")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            agentService = null
            isServiceBound = false
            LogUtils.d("AgentService已断开")
        }
    }
    
    // 截图权限请求
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 启动截图服务
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            LogUtils.i("截图权限已获取")
            Toast.makeText(this, "截图权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            LogUtils.w("截图权限被拒绝")
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化配置管理器
        PreferenceManager.init(this)
        
        // 设置日志回调
        LogUtils.logCallback = { log ->
            runOnUiThread {
                appendLog(log)
            }
        }
        
        setupUI()
        bindAgentService()
    }
    
    override fun onResume() {
        super.onResume()
        // 检查无障碍服务状态，如果未开启则提示
        if (!AutoAgentAccessibilityService.isServiceEnabled()) {
            // 可以在这里添加提示逻辑
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        LogUtils.logCallback = null
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        // 示例问题点击事件
        binding.tvExample1.setOnClickListener {
            binding.etQuery.setText("打开京东搜索感冒药")
        }
        binding.tvExample2.setOnClickListener {
            binding.etQuery.setText("打开淘宝搜索维生素C")
        }
        binding.tvExample3.setOnClickListener {
            binding.etQuery.setText("打开美团点外卖")
        }

        // 键盘/语音模式切换按钮
        binding.btnInputMode.setOnClickListener {
            toggleInputMode()
        }

        // 语音输入按钮（按住说话）
        binding.btnVoiceInput.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 按下开始录音
                    if (checkRecordPermission()) {
                        startRecording()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // 松开结束录音
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }

        // 发送按钮
        binding.btnSend.setOnClickListener {
            val query = binding.etQuery.text?.toString()?.trim() ?: ""
            if (query.isEmpty()) {
                Toast.makeText(this, "请输入任务描述", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!AutoAgentAccessibilityService.isServiceEnabled()) {
                showAccessibilityDialog()
                return@setOnClickListener
            }

            startTask(query)
        }

        // 浮动停止按钮
        binding.fabStopTask.setOnClickListener {
            stopTask()
        }

        // 输入框回车键监听
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                binding.btnSend.performClick()
                true
            } else {
                false
            }
        }
    }

    /**
     * 切换输入模式（键盘/语音）
     */
    private fun toggleInputMode() {
        isKeyboardMode = !isKeyboardMode

        if (isKeyboardMode) {
            // 切换到键盘模式
            binding.btnInputMode.text = "⌨️"
            binding.etQuery.visibility = View.VISIBLE
            binding.btnVoiceInput.visibility = View.GONE

            // 显示键盘
            binding.etQuery.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            // 切换到语音模式
            binding.btnInputMode.text = "🎤"
            binding.etQuery.visibility = View.GONE
            binding.btnVoiceInput.visibility = View.VISIBLE

            // 重置语音按钮状态
            resetVoiceButton()

            // 隐藏键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etQuery.windowToken, 0)
        }
    }

    /**
     * 重置语音按钮状态
     */
    private fun resetVoiceButton() {
        binding.btnVoiceInput.text = "按住 说话"
        binding.btnVoiceInput.setBackgroundColor(getColor(android.R.color.darker_gray))
        binding.btnVoiceInput.setTextColor(getColor(android.R.color.black))
    }

    /**
     * 检查录音权限
     */
    private fun checkRecordPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            false
        } else {
            true
        }
    }

    /**
     * 开始录音
     */
    private fun startRecording() {
        try {
            // 如果已经在录音，先停止
            if (isRecording) {
                LogUtils.w("已经在录音中，先停止")
                cleanupRecording()
            }

            // 创建音频文件
            audioFile = File(cacheDir, "voice_${System.currentTimeMillis()}.wav")

            // 初始化MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true

            // 更新UI
            binding.btnVoiceInput.text = "松开 结束"
            binding.btnVoiceInput.setBackgroundColor(getColor(R.color.primary))
            binding.btnVoiceInput.setTextColor(getColor(android.R.color.white))

            LogUtils.i("开始录音: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            LogUtils.e("录音失败: ${e.message}")
            Toast.makeText(this, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupRecording()
        }
    }

    /**
     * 停止录音并调用ASR
     */
    private fun stopRecording() {
        // 如果没有在录音，直接返回
        if (!isRecording) {
            LogUtils.w("当前没有在录音")
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // 重置按钮状态
            resetVoiceButton()

            LogUtils.i("录音结束: ${audioFile?.absolutePath}")

            // 调用ASR接口
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    callAsrApi(file)
                } else {
                    Toast.makeText(this, "录音时间太短，请重试", Toast.LENGTH_SHORT).show()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            LogUtils.e("停止录音失败: ${e.message}")
            Toast.makeText(this, "录音时间太短，请重试", Toast.LENGTH_SHORT).show()
            // 确保清理资源和重置状态
            cleanupRecording()
        }
    }

    /**
     * 清理录音资源和状态
     */
    private fun cleanupRecording() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // 忽略stop异常
                }
                release()
            }
        } catch (e: Exception) {
            LogUtils.e("清理MediaRecorder失败: ${e.message}")
        }

        mediaRecorder = null
        isRecording = false

        // 重置按钮状态
        resetVoiceButton()

        // 删除临时文件
        audioFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        audioFile = null
    }

    /**
     * 调用ASR接口进行语音转文字
     */
    private fun callAsrApi(audioFile: File) {
        // 显示加载提示
        Toast.makeText(this, "正在识别语音...", Toast.LENGTH_SHORT).show()

        // 在后台线程调用ASR接口
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = AsrApiClient.recognize(audioFile)

                withContext(Dispatchers.Main) {
                    if (text != null && text.isNotEmpty()) {
                        // 将识别结果填入输入框
                        binding.etQuery.setText(text)

                        // 自动切换回键盘模式
                        isKeyboardMode = false // 先设为false，让toggleInputMode切换到true
                        toggleInputMode()

                        Toast.makeText(this@MainActivity, "识别成功", Toast.LENGTH_SHORT).show()
                        LogUtils.i("ASR识别结果: $text")
                    } else {
                        Toast.makeText(this@MainActivity, "识别失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }

                // 删除临时音频文件
                audioFile.delete()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "识别异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                LogUtils.e("ASR调用异常: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 绑定AgentService
     */
    private fun bindAgentService() {
        val intent = Intent(this, AgentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 开始执行任务
     */
    private fun startTask(query: String) {
        LogUtils.i("========================================")
        LogUtils.i("用户输入任务: $query")

        // 请求截图权限
        requestScreenCapturePermission()

        // 启动任务
        agentService?.startTask(query)

        // 清空输入框
        binding.etQuery.setText("")

        // 显示停止按钮
        binding.fabStopTask.visibility = View.VISIBLE

        // 显示Toast提示
        Toast.makeText(this, "任务已开始执行", Toast.LENGTH_SHORT).show()

        // 最小化应用，回到桌面
        moveTaskToBack(true)
    }
    
    /**
     * 停止任务
     */
    private fun stopTask() {
        agentService?.stopTask()

        // 隐藏停止按钮
        binding.fabStopTask.visibility = View.GONE

        Toast.makeText(this, "任务已停止", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 请求截图权限
     */
    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
    
    /**
     * 根据任务状态更新UI
     */
    private fun updateUIForStatus(status: TaskStatus, message: String) {
        when (status) {
            TaskStatus.RUNNING -> {
                // 任务运行中，显示停止按钮
                binding.fabStopTask.visibility = View.VISIBLE
                LogUtils.i("任务状态: $message")
            }
            TaskStatus.COMPLETED -> {
                // 任务完成，隐藏停止按钮
                binding.fabStopTask.visibility = View.GONE
                Toast.makeText(this, "任务已完成", Toast.LENGTH_SHORT).show()
            }
            TaskStatus.ERROR -> {
                // 任务出错，隐藏停止按钮
                binding.fabStopTask.visibility = View.GONE
                Toast.makeText(this, "任务出错: $message", Toast.LENGTH_LONG).show()
            }
            TaskStatus.NEED_USER_HELP -> {
                // 弹出提示
                showUserHelpDialog(message)
            }
            TaskStatus.IDLE -> {
                // 空闲状态，隐藏停止按钮
                binding.fabStopTask.visibility = View.GONE
                LogUtils.i("任务状态: 空闲")
            }
        }
    }
    
    /**
     * 追加日志
     */
    private fun appendLog(log: String) {
        // 日志功能已移除，不做任何操作
        // 避免调用LogUtils.d造成无限递归
    }
    
    /**
     * 显示无障碍服务提示对话框
     */
    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要开启无障碍服务")
            .setMessage("为了实现自动点击、滑动等操作，请先开启无障碍服务。\n\n请在设置中找到\"智能手机助手\"并开启。")
            .setPositiveButton("去开启") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 打开无障碍设置
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示用户协助对话框
     */
    private fun showUserHelpDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要您的协助")
            .setMessage(message)
            .setPositiveButton("继续执行") { _, _ ->
                // 继续当前任务
                Toast.makeText(this, "继续执行任务", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("停止任务") { _, _ ->
                stopTask()
            }
            .show()
    }
}
