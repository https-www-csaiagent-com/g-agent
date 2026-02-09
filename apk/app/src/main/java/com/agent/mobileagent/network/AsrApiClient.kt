package com.agent.mobileagent.network

import com.agent.mobileagent.util.LogUtils
import com.agent.mobileagent.util.PreferenceManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ASR语音转文字API客户端
 */
object AsrApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * ASR响应数据类
     */
    data class AsrResponse(
        val success: Boolean,
        val text: String?,
        val raw_text: String?,
        val language: String?
    )

    /**
     * 调用ASR接口进行语音转文字
     * @param audioFile 音频文件
     * @param language 语言（默认auto自动检测）
     * @return 转换后的文本，失败返回null
     */
    fun recognize(audioFile: File, language: String = "auto"): String? {
        try {
            LogUtils.i("开始调用ASR接口，文件: ${audioFile.absolutePath}")

            // 构建multipart请求
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("language", language)
                .build()

            // 构建请求
            val asrUrl = PreferenceManager.asrUrl
            val request = Request.Builder()
                .url(asrUrl)
                .post(requestBody)
                .build()

            // 发送请求
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            LogUtils.d("ASR响应: $responseBody")

            if (response.isSuccessful && responseBody != null) {
                val asrResponse = gson.fromJson(responseBody, AsrResponse::class.java)
                if (asrResponse.success && asrResponse.text != null) {
                    LogUtils.i("ASR转换成功: ${asrResponse.text}")
                    return asrResponse.text
                } else {
                    LogUtils.e("ASR转换失败: success=${asrResponse.success}")
                }
            } else {
                LogUtils.e("ASR请求失败: ${response.code}")
            }
        } catch (e: Exception) {
            LogUtils.e("ASR调用异常: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
}
