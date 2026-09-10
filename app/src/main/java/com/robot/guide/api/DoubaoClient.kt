package com.robot.guide.api

import com.robot.guide.data.ChatMessage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 豆包API客户端
 * 官方文档: https://www.volcengine.com/docs/82379/1099478
 */
class DoubaoClient(
    private val apiKey: String,
    private val modelId: String,
    private val systemPrompt: String = "你是一个专业的展厅讲解机器人，回答问题要简洁友好。"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

    /**
     * 发送对话请求（非流式）
     */
    fun chat(
        messages: List<ChatMessage>,
        callback: (success: Boolean, reply: String, errorMsg: String?) -> Unit
    ) {
        Thread {
            try {
                val jsonMessages = JSONArray()

                // 系统提示词
                jsonMessages.put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )

                // 历史消息（限制最近10轮）
                val recent = messages.takeLast(20)
                for (msg in recent) {
                    val roleStr = if (msg.role == ChatMessage.Role.USER) "user" else "assistant"
                    jsonMessages.put(
                        JSONObject().apply {
                            put("role", roleStr)
                            put("content", msg.content)
                        }
                    )
                }

                val body = JSONObject().apply {
                    put("model", modelId)
                    put("messages", jsonMessages)
                    put("stream", false)
                    put("max_tokens", 1024)
                    put("temperature", 0.7)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        callback(false, "", "HTTP ${response.code}: ${response.body?.string()}")
                        return@use
                    }

                    val responseBody = response.body?.string() ?: run {
                        callback(false, "", "空响应")
                        return@use
                    }

                    val json = JSONObject(responseBody)
                    val reply = json
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "") ?: ""

                    if (reply.isBlank()) {
                        callback(false, "", "AI返回为空")
                    } else {
                        callback(true, reply, null)
                    }
                }
            } catch (e: Exception) {
                callback(false, "", e.message ?: "未知错误")
            }
        }.start()
    }

    /**
     * 发送单条问题快速回复
     */
    fun quickAsk(question: String, callback: (success: Boolean, reply: String, errorMsg: String?) -> Unit) {
        chat(
            messages = listOf(ChatMessage(role = ChatMessage.Role.USER, content = question)),
            callback = callback
        )
    }

    /**
     * 测试API连接
     */
    fun testConnection(callback: (success: Boolean, msg: String) -> Unit) {
        quickAsk("你好，请用一句话介绍自己") { success, reply, error ->
            if (success) {
                callback(true, "连接成功")
            } else {
                callback(false, error ?: "连接失败")
            }
        }
    }
}
