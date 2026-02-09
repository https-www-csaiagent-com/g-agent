package com.agent.mobileagent.network

import com.agent.mobileagent.model.*
import com.agent.mobileagent.util.LogUtils
import com.agent.mobileagent.util.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Agent API 客户端
 * 
 * 直接调用vLLM模型服务，类似于Python版本的model_utils.py
 */
class AgentApiClient {
    
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 调用模型获取下一步动作
     * 
     * @param base64Image 截图的Base64编码
     * @param query 用户查询/任务描述
     * @param history 历史操作记录
     * @return 解析后的动作响应
     */
    suspend fun getNextAction(
        base64Image: String,
        query: String,
        history: List<String> = emptyList()
    ): Result<ActionResponse> = withContext(Dispatchers.IO) {
        try {
            val vllmUrl = PreferenceManager.vllmUrl
            val modelName = PreferenceManager.modelName
            
            // 构建历史记录摘要
            val historyInfo = buildHistorySummary(history)
            
            // 构建prompt
            val prompt = buildPrompt(query, historyInfo)
            
            LogUtils.d("调用模型: $modelName")
            LogUtils.d("Prompt长度: ${prompt.length}")
            
            // 构建请求体
            val requestBody = buildVLLMRequest(modelName, base64Image, prompt)
            
            val request = Request.Builder()
                .url("$vllmUrl/chat/completions")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                LogUtils.e("API请求失败: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("API请求失败: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("响应体为空"))
            }
            
            LogUtils.d("收到响应: ${responseBody.take(500)}...")
            
            // 解析响应
            val vllmResponse = gson.fromJson(responseBody, VLLMResponse::class.java)
            val content = vllmResponse.choices?.firstOrNull()?.message?.content
            
            if (content.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("模型响应内容为空"))
            }
            
            LogUtils.model(content.take(300))
            
            // 解析Action JSON
            val actionJson = extractActionJson(content)
            if (actionJson.isEmpty()) {
                LogUtils.e("无法从响应中提取Action")
                return@withContext Result.failure(Exception("无法解析Action"))
            }
            
            val action = gson.fromJson(actionJson, ActionResponse::class.java)
            Result.success(action)
            
        } catch (e: Exception) {
            LogUtils.e("getNextAction异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 构建vLLM请求体
     */
    private fun buildVLLMRequest(
        modelName: String,
        base64Image: String,
        prompt: String
    ): RequestBody {
        val imageContent = VLLMContent(
            type = "image_url",
            imageUrl = VLLMImageUrl("data:image/jpeg;base64,$base64Image")
        )
        
        val textContent = VLLMContent(
            type = "text",
            text = prompt
        )
        
        val message = VLLMMessage(
            role = "user",
            content = listOf(imageContent, textContent)
        )
        
        val requestObj = mapOf(
            "model" to modelName,
            "messages" to listOf(message),
            "max_tokens" to 1024,
            "temperature" to 0.7,
            "reasoning_effort" to "low",
            "extra_body" to mapOf(
                "chat_template_kwargs" to mapOf(
                    "enable_thinking" to false,
                    "thinking_tokens_budget" to 30
                )
            )
        )
        
        val json = gson.toJson(requestObj)
        return json.toRequestBody("application/json".toMediaType())
    }
    
    /**
     * 构建Prompt
     */
    private fun buildPrompt(query: String, historyInfo: String): String {
        return """
You are a GUI Agent, and your primary task is to respond accurately to user requests or questions. In addition to directly answering the user's queries, you can also use tools or perform GUI operations directly until you fulfill the user's request or provide a correct answer. You should carefully read and understand the images and questions provided by the user, and engage in thinking and reflection when appropriate. The coordinates involved are all represented in thousandths (0-999).

# Task:
$query

# Task Platform
Mobile

# Action Space
### status
Calling rule: `{"action_type": "status", "goal_status": "<complete|infeasible>"}`
Description: Finish the task by using the status action with complete or infeasible as goal_status.

### click
Calling rule: `{"action_type": "click", "box_2d": [[xmin,ymin,xmax,ymax]]}`
Description: Click/tap on an element on the screen. Use the box_2d to indicate which element you want to click. The box_2d should be [[xmin,ymin,xmax,ymax]] normalized to 0-999.

### input_text
Calling rule: `{"action_type": "input_text", "text": "<text_input>", "box_2d": [[xmin,ymin,xmax,ymax]]}`
Description: Type text into a text field. Use the box_2d to indicate the target text field.

### navigate_back
Calling rule: `{"action_type": "navigate_back"}`
Description: Navigate back.

### swipe
Calling rule: `{"action_type": "swipe", "start_point": [x, y], "end_point": [x, y]}`
Description: 在屏幕上执行一次滑动操作。

### open_app
Calling rule: `{"action_type": "open_app", "app_name": "<name>"}`
Description: Open an app. Supported apps: 美团，京东，淘宝，拼多多，小红书

### wait
Calling rule: `{"action_type": "wait"}`
Description: Wait for the screen to update.

### call_user
Calling rule: `{"action_type": "call_user", "text": "reason"}`
Description: When the task is unsolvable or you need the user's help like login, input verification code, call the user.

# Historical
$historyInfo

# Output Format
1. Memory: important information you want to remember for the future actions.
2. Reason: the reason for the action and the memory.
3. Action: the action you want to take, in the correct JSON format.

Your answer should look like:
Memory: ...
Reason: ...
Action: {"action_type":...}

# Some Additional Notes
- Memory、Reason、Action三项必须全部输出，而且全部用中文输出
- Reason部分控制在20个字之内
- 如果页面出现登录，验证码，隐私协议同意等字眼需要呼叫用户（call_user）协助完成
- 任务完成时使用status标记complete
""".trimIndent()
    }
    
    /**
     * 构建历史记录摘要
     */
    private fun buildHistorySummary(history: List<String>): String {
        if (history.isEmpty()) return "暂无历史操作"
        
        val memories = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        val actions = mutableListOf<String>()
        
        val invalidKeywords = listOf("无", "暂无", "空", "null", "None", "", "无新信息")
        
        for (h in history) {
            // 提取Memory
            val memoryPattern = Pattern.compile("Memory[：:]\\s*(.*?)(?:\\nReason[：:]|\\nAction[：:]|$)", Pattern.DOTALL)
            val memoryMatcher = memoryPattern.matcher(h)
            if (memoryMatcher.find()) {
                val mem = memoryMatcher.group(1)?.trim() ?: ""
                if (mem.isNotEmpty() && mem !in invalidKeywords && !memories.contains(mem)) {
                    memories.add(mem)
                }
            }
            
            // 提取Reason
            val reasonPattern = Pattern.compile("Reason[：:][ \\t]*(.*?)(?:\\nAction[：:]|$)", Pattern.DOTALL)
            val reasonMatcher = reasonPattern.matcher(h)
            if (reasonMatcher.find()) {
                val rsn = reasonMatcher.group(1)?.trim() ?: ""
                if (rsn.isNotEmpty() && !rsn.startsWith("Action")) {
                    reasons.add(rsn)
                }
            }
            
            // 提取Action
            val actionPattern = Pattern.compile("Action[：:]\\s*(.*?)$", Pattern.DOTALL)
            val actionMatcher = actionPattern.matcher(h)
            if (actionMatcher.find()) {
                actions.add(actionMatcher.group(1)?.trim() ?: "")
            }
        }
        
        val result = StringBuilder()
        
        if (memories.isNotEmpty()) {
            result.append("Memory:\n")
            memories.forEachIndexed { i, m -> result.append("${i + 1}. $m\n") }
            result.append("\n")
        }
        
        if (reasons.isNotEmpty()) {
            result.append("Reason:\n")
            reasons.forEachIndexed { i, r -> result.append("${i + 1}. $r\n") }
            result.append("\n")
        }
        
        if (actions.isNotEmpty()) {
            result.append("Action:\n")
            actions.forEachIndexed { i, a -> result.append("${i + 1}. $a\n") }
        }
        
        return result.toString().ifEmpty { "暂无历史操作" }
    }
    
    /**
     * 从模型响应中提取Action JSON
     */
    private fun extractActionJson(response: String): String {
        // 清理响应
        val cleanResponse = response.replace("<|begin_of_box|>", "").replace("<|end_of_box|>", "")
        
        // 尝试匹配 Action: {...}
        val patterns = listOf(
            Pattern.compile("Action[：:]\\s*(\\{.*?\\})", Pattern.DOTALL),
            Pattern.compile("(\\{\"action_type\".*?\\})", Pattern.DOTALL)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(cleanResponse)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: continue
                // 验证是否为有效JSON
                if (isValidJson(jsonStr)) {
                    return jsonStr
                }
            }
        }
        
        // 尝试找所有JSON对象
        return findAllJsonObjects(cleanResponse)
            .filter { it.contains("action_type") }
            .lastOrNull() ?: ""
    }
    
    /**
     * 验证JSON是否有效
     */
    private fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 查找所有JSON对象
     */
    private fun findAllJsonObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        var braceCount = 0
        var startPos = -1
        
        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (braceCount == 0) startPos = i
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startPos >= 0) {
                        val jsonStr = text.substring(startPos, i + 1)
                        if (isValidJson(jsonStr)) {
                            objects.add(jsonStr)
                        }
                        startPos = -1
                    }
                }
            }
        }
        
        return objects
    }
    
    /**
     * 构建历史记录条目
     */
    fun buildHistoryEntry(memory: String, reason: String, actionJson: String): String {
        return "Memory:$memory\nReason:$reason\nAction:<|begin_of_box|>$actionJson<|end_of_box|>"
    }
}
