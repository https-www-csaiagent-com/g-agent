package com.agent.mobileagent.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器
 */
object PreferenceManager {
    
    private const val PREF_NAME = "mobile_agent_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_MODEL_NAME = "model_name"
    private const val KEY_VLLM_URL = "vllm_url"
    private const val KEY_ASR_URL = "asr_url"

    // 默认配置 - 根据你的const.py中的配置
    private const val DEFAULT_SERVER_URL = "http://172.23.39.43:8697"
    private const val DEFAULT_VLLM_URL = "http://14.103.148.99:8002/v1"
    private const val DEFAULT_MODEL_NAME = "cs-glm"
    private const val DEFAULT_ASR_URL = "http://14.103.148.99:8787/recognize"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()
    
    var vllmUrl: String
        get() = prefs.getString(KEY_VLLM_URL, DEFAULT_VLLM_URL) ?: DEFAULT_VLLM_URL
        set(value) = prefs.edit().putString(KEY_VLLM_URL, value).apply()
    
    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    var asrUrl: String
        get() = prefs.getString(KEY_ASR_URL, DEFAULT_ASR_URL) ?: DEFAULT_ASR_URL
        set(value) = prefs.edit().putString(KEY_ASR_URL, value).apply()
}
