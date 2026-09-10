package com.robot.guide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.robot.guide.data.ChatMessage
import com.robot.guide.databinding.ItemChatMessageBinding

/**
 * 聊天消息列表适配器 - 深蓝科技风
 */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessageWithSource(content: String, sourceText: String, mediaRefs: List<String>) {
        if (messages.isNotEmpty()) {
            val lastIdx = messages.size - 1
            messages[lastIdx] = messages[lastIdx].copy(
                content = content,
                mediaRefs = mediaRefs
            )
            notifyItemChanged(lastIdx)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun getMessages() = messages.toList()

    inner class ViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: ChatMessage) {
            val isUser = msg.role == ChatMessage.Role.USER

            binding.userMessageContainer.visibility = if (isUser) View.VISIBLE else View.GONE
            binding.botMessageContainer.visibility = if (isUser) View.GONE else View.VISIBLE

            if (isUser) {
                binding.tvUserMessage.text = msg.content
                binding.userTagRow.visibility = View.VISIBLE
                binding.tvUserLang.text = "普通话"
                binding.tvUserConfidence.text = "置信度 95%"
            } else {
                // 机器人消息
                if (msg.content.isBlank()) {
                    binding.pbLoading.visibility = View.VISIBLE
                    binding.tvBotMessage.text = "思考中..."
                    binding.tagRow.visibility = View.GONE
                    binding.audioPlayBar.visibility = View.GONE
                    binding.tvSource.text = ""
                } else {
                    binding.pbLoading.visibility = View.GONE
                    binding.tvBotMessage.text = msg.content

                    // 来源标签
                    val sourceText = when (msg.source) {
                        ChatMessage.Source.LOCAL -> "固定题库"
                        ChatMessage.Source.AI -> "AI生成"
                        ChatMessage.Source.FALLBACK -> "兜底回复"
                    }
                    binding.tvSource.text = sourceText

                    // 动作/LED 标签（根据内容简化判断）
                    if (msg.content.contains("挥手") || msg.content.contains("握手")) {
                        binding.tagRow.visibility = View.VISIBLE
                        binding.tvTagAction.visibility = View.VISIBLE
                        binding.tvTagAction.text = "✋ 挥手"
                    } else if (msg.content.contains("转向")) {
                        binding.tagRow.visibility = View.VISIBLE
                        binding.tvTagAction.visibility = View.VISIBLE
                        binding.tvTagAction.text = "👤 头部转向"
                    } else {
                        binding.tagRow.visibility = View.GONE
                    }

                    // 语音播放条（模拟每条机器人消息都有语音）
                    binding.audioPlayBar.visibility = View.VISIBLE
                }
            }
        }
    }
}
