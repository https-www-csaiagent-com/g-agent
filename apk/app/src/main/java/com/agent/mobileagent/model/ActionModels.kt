package com.agent.mobileagent.model

import com.google.gson.annotations.SerializedName

/**
 * 模型返回的动作类型
 */
data class ActionResponse(
    @SerializedName("action_type")
    val actionType: String,
    
    @SerializedName("box_2d")
    val box2d: List<List<Int>>? = null,
    
    @SerializedName("text")
    val text: String? = null,
    
    @SerializedName("app_name")
    val appName: String? = null,
    
    @SerializedName("start_point")
    val startPoint: List<Int>? = null,
    
    @SerializedName("end_point")
    val endPoint: List<Int>? = null,
    
    @SerializedName("direction")
    val direction: String? = null,
    
    @SerializedName("goal_status")
    val goalStatus: String? = null,
    
    @SerializedName("key")
    val key: Int? = null,
    
    @SerializedName("override")
    val override: Boolean? = null
)

/**
 * 发送给后端的请求
 */
data class AgentRequest(
    @SerializedName("image")
    val image: String,  // base64编码的图片
    
    @SerializedName("query")
    val query: String,  // 用户查询
    
    @SerializedName("history")
    val history: List<String> = emptyList(),  // 历史操作记录
    
    @SerializedName("screen_width")
    val screenWidth: Int,
    
    @SerializedName("screen_height")
    val screenHeight: Int
)

/**
 * 后端返回的响应
 */
data class AgentResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("action")
    val action: ActionResponse?,
    
    @SerializedName("memory")
    val memory: String? = null,
    
    @SerializedName("reason")
    val reason: String? = null,
    
    @SerializedName("raw_response")
    val rawResponse: String? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * 简化的模型调用请求（直接调用vLLM）
 */
data class VLLMRequest(
    @SerializedName("model")
    val model: String,
    
    @SerializedName("messages")
    val messages: List<VLLMMessage>,
    
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    
    @SerializedName("temperature")
    val temperature: Float = 0.7f
)

data class VLLMMessage(
    @SerializedName("role")
    val role: String,
    
    @SerializedName("content")
    val content: List<VLLMContent>
)

data class VLLMContent(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("text")
    val text: String? = null,
    
    @SerializedName("image_url")
    val imageUrl: VLLMImageUrl? = null
)

data class VLLMImageUrl(
    @SerializedName("url")
    val url: String
)

data class VLLMResponse(
    @SerializedName("choices")
    val choices: List<VLLMChoice>?
)

data class VLLMChoice(
    @SerializedName("message")
    val message: VLLMMessageResponse?
)

data class VLLMMessageResponse(
    @SerializedName("content")
    val content: String?
)

/**
 * 动作类型枚举
 */
object ActionType {
    const val CLICK = "click"
    const val SWIPE = "swipe"
    const val INPUT_TEXT = "input_text"
    const val NAVIGATE_BACK = "navigate_back"
    const val OPEN_APP = "open_app"
    const val WAIT = "wait"
    const val STATUS = "status"
    const val CALL_USER = "call_user"
    const val ANSWER = "answer"
    const val SHOW_PRODUCT_INFO = "show_product_info"
    const val SAVE_PRODUCT_INFO = "save_product_info"
}

/**
 * 任务状态
 */
enum class TaskStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    ERROR,
    NEED_USER_HELP
}
