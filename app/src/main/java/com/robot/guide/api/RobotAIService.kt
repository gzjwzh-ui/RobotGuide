package com.robot.guide.api

import android.content.Context
import com.robot.guide.data.ChatMessage
import com.robot.guide.db.DatabaseHelper
import com.robot.guide.matcher.QAMatcher
import com.robot.guide.util.AppSettings

/**
 * AI服务统一入口
 * 优先匹配固定问答库，匹配不到再调用豆包API
 */
class RobotAIService(context: Context) {

    private val db = DatabaseHelper.getInstance(context)
    private val settings = AppSettings(context)
    private val matcher by lazy { QAMatcher(settings.matchThreshold) }

    /**
     * 智能回答
     * @param userQuestion 用户问题
     * @param history 历史对话
     * @param onResult 结果回调 (answer, source, mediaRefs)
     */
    fun answer(
        userQuestion: String,
        history: List<ChatMessage> = emptyList(),
        onResult: (answer: String, source: ChatMessage.Source, mediaRefs: List<String>) -> Unit
    ) {
        // 1. 先查固定问答库
        val qaLibrary = db.getAllQA()
        val matchResult = matcher.match(userQuestion, qaLibrary)

        if (matchResult != null) {
            // 匹配到固定答案
            onResult(
                matchResult.qaItem.answer,
                ChatMessage.Source.LOCAL,
                matchResult.qaItem.mediaRefs
            )
            return
        }

        // 2. 固定库没匹配到，尝试AI
        if (settings.useAI && settings.isAIConfigured()) {
            val doubao = DoubaoClient(
                apiKey = settings.apiKey,
                modelId = settings.modelId,
                systemPrompt = settings.systemPrompt
            )
            doubao.chat(history + ChatMessage(role = ChatMessage.Role.USER, content = userQuestion)) { success, reply, _ ->
                if (success) {
                    onResult(reply, ChatMessage.Source.AI, emptyList())
                } else {
                    onResult(getFallbackAnswer(userQuestion), ChatMessage.Source.FALLBACK, emptyList())
                }
            }
        } else {
            // AI未配置，使用兜底回答
            onResult(getFallbackAnswer(userQuestion), ChatMessage.Source.FALLBACK, emptyList())
        }
    }

    /**
     * 获取兜底回答
     */
    private fun getFallbackAnswer(question: String): String {
        val fallbacks = listOf(
            "抱歉，我暂时没有找到相关答案，您可以尝试换个方式提问，或者直接询问我关于展厅的内容。",
            "这个问题我还在学习中~ 您可以试试问我「展厅有什么展品」或者「怎么参观」哦。",
            "我目前主要了解展厅的内容，您想了解什么展品呢？"
        )
        return fallbacks.random()
    }

    /**
     * 获取问答库统计
     */
    fun getQAStats(): Triple<Int, Boolean, String> {
        return Triple(db.getCount(), settings.isAIConfigured(), settings.modelId)
    }
}
