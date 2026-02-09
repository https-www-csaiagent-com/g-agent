package com.agent.mobileagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.mobileagent.util.LogUtils
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * 无障碍服务 - 核心执行引擎
 * 
 * 提供以下能力：
 * 1. 执行点击操作
 * 2. 执行滑动操作
 * 3. 输入文本
 * 4. 返回操作
 * 5. 截图（通过MediaProjection）
 */
class AutoAgentAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: AutoAgentAccessibilityService? = null
        
        fun getInstance(): AutoAgentAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
        
        // MediaProjection相关
        private var mediaProjection: MediaProjection? = null
        private var mediaProjectionIntent: Intent? = null
        private var mediaProjectionResultCode: Int = 0
        
        fun setMediaProjectionData(resultCode: Int, data: Intent) {
            mediaProjectionResultCode = resultCode
            mediaProjectionIntent = data
            LogUtils.i("MediaProjection数据已设置")
        }
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var screenWidth = 1080
    private var screenHeight = 2340
    private var screenDensity = 0
    
    // 截图相关
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 获取屏幕尺寸
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        LogUtils.i("无障碍服务已创建 - 屏幕尺寸: ${screenWidth}x${screenHeight}")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        LogUtils.i("无障碍服务已连接")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里监听UI事件
    }
    
    override fun onInterrupt() {
        LogUtils.w("无障碍服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        releaseMediaProjection()
        LogUtils.i("无障碍服务已销毁")
    }
    
    /**
     * 获取屏幕尺寸
     */
    fun getScreenSize(): Pair<Int, Int> = Pair(screenWidth, screenHeight)
    
    /**
     * 执行点击操作
     * @param x 归一化x坐标 (0-999)
     * @param y 归一化y坐标 (0-999)
     */
    fun performClick(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        // 坐标转换：归一化坐标 -> 实际像素坐标
        val actualX = (x.toFloat() / 999f * screenWidth).toInt()
        val actualY = (y.toFloat() / 999f * screenHeight).toInt()
        
        LogUtils.action("click", "归一化($x, $y) -> 实际($actualX, $actualY)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(actualX.toFloat(), actualY.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            gestureBuilder.addStroke(stroke)
            
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    LogUtils.d("点击执行完成")
                    callback?.invoke(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    LogUtils.w("点击被取消")
                    callback?.invoke(false)
                }
            }, mainHandler)
        } else {
            LogUtils.e("系统版本过低，不支持手势操作")
            callback?.invoke(false)
        }
    }
    
    /**
     * 执行滑动操作
     * @param startX 起始点归一化x坐标 (0-999)
     * @param startY 起始点归一化y坐标 (0-999)
     * @param endX 终点归一化x坐标 (0-999)
     * @param endY 终点归一化y坐标 (0-999)
     * @param duration 持续时间（毫秒）
     */
    fun performSwipe(
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int,
        duration: Long = 500,
        callback: ((Boolean) -> Unit)? = null
    ) {
        // 坐标转换
        val actualStartX = (startX.toFloat() / 999f * screenWidth).toInt()
        val actualStartY = (startY.toFloat() / 999f * screenHeight).toInt()
        val actualEndX = (endX.toFloat() / 999f * screenWidth).toInt()
        val actualEndY = (endY.toFloat() / 999f * screenHeight).toInt()
        
        LogUtils.action("swipe", "($actualStartX,$actualStartY) -> ($actualEndX,$actualEndY)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(actualStartX.toFloat(), actualStartY.toFloat())
                lineTo(actualEndX.toFloat(), actualEndY.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(stroke)
            
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    LogUtils.d("滑动执行完成")
                    callback?.invoke(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    LogUtils.w("滑动被取消")
                    callback?.invoke(false)
                }
            }, mainHandler)
        } else {
            LogUtils.e("系统版本过低，不支持手势操作")
            callback?.invoke(false)
        }
    }
    
    /**
     * 执行返回操作
     */
    fun performBack(callback: ((Boolean) -> Unit)? = null) {
        LogUtils.action("navigate_back")
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        callback?.invoke(result)
    }
    
    /**
     * 回到主屏幕
     */
    fun performHome(callback: ((Boolean) -> Unit)? = null) {
        LogUtils.action("home")
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        callback?.invoke(result)
    }
    
    /**
     * 打开最近任务
     */
    fun performRecents(callback: ((Boolean) -> Unit)? = null) {
        LogUtils.action("recents")
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        callback?.invoke(result)
    }
    
    /**
     * 输入文本
     * 需要先聚焦到输入框
     */
    fun inputText(text: String, callback: ((Boolean) -> Unit)? = null) {
        LogUtils.action("input_text", text.take(50))
        
        try {
            // 查找当前聚焦的输入框
            val rootNode = rootInActiveWindow
            val editText = findFocusedEditText(rootNode)
            
            if (editText != null) {
                // 使用剪贴板方式输入
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
                
                // 粘贴
                editText.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                LogUtils.d("文本输入完成")
                callback?.invoke(true)
            } else {
                // 尝试使用广播方式输入
                val intent = Intent("ADB_INPUT_TEXT").apply {
                    putExtra("text", text)
                }
                sendBroadcast(intent)
                LogUtils.w("未找到聚焦的输入框，尝试广播方式")
                callback?.invoke(true)
            }
        } catch (e: Exception) {
            LogUtils.e("输入文本失败: ${e.message}", e)
            callback?.invoke(false)
        }
    }
    
    /**
     * 查找当前聚焦的输入框
     */
    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        
        if (node.isFocused && node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findFocusedEditText(child)
            if (result != null) return result
        }
        
        return null
    }
    
    /**
     * 截取屏幕截图
     * 使用Android 11+的截图API
     */
    fun takeScreenshot(callback: (String?) -> Unit) {
        LogUtils.d("开始截图...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用无障碍服务的截图能力
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            if (bitmap != null) {
                                val base64 = bitmapToBase64(bitmap)
                                LogUtils.d("截图成功，大小: ${base64.length}")
                                callback(base64)
                            } else {
                                LogUtils.e("截图转换失败")
                                callback(null)
                            }
                            screenshot.hardwareBuffer.close()
                        } catch (e: Exception) {
                            LogUtils.e("截图处理失败: ${e.message}", e)
                            callback(null)
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        LogUtils.e("截图失败，错误码: $errorCode")
                        // 尝试使用MediaProjection方式
                        takeScreenshotViaMediaProjection(callback)
                    }
                }
            )
        } else {
            // 使用MediaProjection方式截图
            takeScreenshotViaMediaProjection(callback)
        }
    }
    
    /**
     * 通过MediaProjection截图
     */
    private fun takeScreenshotViaMediaProjection(callback: (String?) -> Unit) {
        if (mediaProjectionIntent == null) {
            LogUtils.e("MediaProjection未初始化")
            callback(null)
            return
        }
        
        try {
            if (mediaProjection == null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(
                    mediaProjectionResultCode,
                    mediaProjectionIntent!!
                )
            }
            
            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth, 
                screenHeight, 
                PixelFormat.RGBA_8888, 
                2
            )
            
            // 创建VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )
            
            // 等待并获取图像
            mainHandler.postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * screenWidth
                        
                        val bitmap = Bitmap.createBitmap(
                            screenWidth + rowPadding / pixelStride,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                        val base64 = bitmapToBase64(croppedBitmap)
                        
                        bitmap.recycle()
                        croppedBitmap.recycle()
                        
                        LogUtils.d("MediaProjection截图成功")
                        callback(base64)
                    } else {
                        LogUtils.e("获取图像失败")
                        callback(null)
                    }
                } catch (e: Exception) {
                    LogUtils.e("处理截图失败: ${e.message}", e)
                    callback(null)
                } finally {
                    virtualDisplay?.release()
                    imageReader?.close()
                }
            }, 100)
            
        } catch (e: Exception) {
            LogUtils.e("MediaProjection截图失败: ${e.message}", e)
            callback(null)
        }
    }
    
    /**
     * Bitmap转Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * 释放MediaProjection资源
     */
    private fun releaseMediaProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
    }

    /**
     * 打开指定APP
     * 使用显式ComponentName创建Intent
     */
    @SuppressLint("ServiceCast")
    fun openApp(appName: String, callback: ((Boolean) -> Unit)? = null) {
        LogUtils.action("open_app", appName)

        try {
            val targetPackage = resolveAppPackage(appName)
                ?: if (packageManager.getLaunchIntentForPackage(appName) != null) appName else null

            if (targetPackage == null) {
                LogUtils.e("无法解析应用包名: $appName")
                callback?.invoke(false)
                return
            }

            LogUtils.i("目标包名: $targetPackage")

            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent == null) {
                LogUtils.e("无法获取启动Intent，应用可能未安装: $targetPackage")
                callback?.invoke(false)
                return
            }

            val componentName = launchIntent.component
            if (componentName == null) {
                LogUtils.e("无法获取Component: $targetPackage")
                callback?.invoke(false)
                return
            }

            LogUtils.i("Component: ${componentName.packageName}/${componentName.className}")

            // 修复1: 简化Intent创建，使用系统返回的launchIntent
            val intent = Intent(launchIntent).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            LogUtils.i("Intent flags: ${intent.flags}")

            // 修复3: 在主线程中完成整个启动和回调流程
            mainHandler.post {
                try {
                    startActivity(intent)
                    LogUtils.i("startActivity调用成功: ${componentName.className}")

                    // 延迟回调，确保应用有时间启动（缩短至1.5秒以加快执行速度）
                    mainHandler.postDelayed({
                        LogUtils.i("应用启动流程完成: $targetPackage")
                        callback?.invoke(true)
                    }, 1500)

                } catch (e: Exception) {
                    LogUtils.e("startActivity异常: ${e.message}", e)
                    callback?.invoke(false)
                }
            }

        } catch (e: Exception) {
            LogUtils.e("打开应用失败: ${e.message}", e)
            callback?.invoke(false)
        }
    }

    /**
     * 根据应用名称解析包名
     * 自动生成的应用映射表 - 包含手机上所有已安装的第三方应用
     */
    private fun resolveAppPackage(appName: String): String? {
        val appPackageMap = mapOf(
            // 英文/包名映射
            "agent" to "com.zhipu.agent",
            "aimbzg" to "com.linewell.citizencloud.aimbzg",
            "alldocuments" to "com.vivo.alldocuments",
            "appwidget" to "com.vivo.wallet.appwidget",
            "atom" to "com.kaixinkan.ugc.video.atom",
            "demo" to "com.bytedance.androidcloud.demo",
            "fm" to "com.xs.fm",
            "healthcode" to "com.vivo.healthcode",
            "iot" to "com.vivo.widget.iot",
            "minigamecenter" to "com.vivo.minigamecenter",
            "mobileagent" to "com.agent.mobileagent",
            "originwidget" to "com.xtc.originwidget",
            "scrcpy" to "org.las2mile.scrcpy",
            "sosappwidget" to "com.vivo.sosappwidget",
            "vhome" to "com.vivo.vhome",
            "video" to "com.kaixinkan.ugc.video",
            "vivo" to "cn.com.omronhealthcare.omronplus.vivo",
            "widget" to "com.vivo.ese.widget",

            // 中文应用名映射
            "东方财富" to "com.eastmoney.android.berlin",
            "云闪付" to "com.unionpay.tsmservice",
            "京东" to "com.jingdong.app.mall",
            "哔哩哔哩" to "tv.danmaku.bili",
            "小红书" to "com.xingin.xhs",
            "微信" to "com.tencent.mm",
            "微博" to "com.sina.weibo",
            "快手" to "com.smile.gifmaker",
            "抖音" to "com.ss.android.ugc.aweme",
            "掌阅" to "com.chaozh.iReader",
            "支付宝" to "com.eg.android.AlipayGphone",
            "淘宝" to "com.taobao.taobao",
            "爱奇艺" to "com.qiyi.video",
            "番茄小说" to "com.dragon.read",
            "百度" to "com.baidu.searchbox",
            "美团" to "com.sankuai.meituan",
            "腾讯视频" to "com.tencent.qqlive",
            "钉钉" to "com.alibaba.android.rimet",
            "闲鱼" to "com.taobao.idlefish",
            "饿了么" to "me.ele",
            "高德地图" to "com.autonavi.minimap",

            // 常见的其他应用（可能未安装但常用）
            "拼多多" to "com.xunmeng.pinduoduo",
            "QQ" to "com.tencent.mobileqq",
            "百度地图" to "com.baidu.BaiduMap",
            "网易云音乐" to "com.netease.cloudmusic",
            "知乎" to "com.zhihu.android"
        )
        return appPackageMap[appName]
    }
}
