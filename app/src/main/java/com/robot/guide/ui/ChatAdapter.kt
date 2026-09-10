package com.robot.guide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.robot.guide.R
import com.robot.guide.data.ChatMessage
import com.robot.guide.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天消息列表适配器
 */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(content = content)
            notifyItemChanged(lastIndex)
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
            } else {
                binding.tvBotMessage.text = msg.content
                binding.tvSource.text = when (msg.source) {
                    ChatMessage.Source.LOCAL -> "📚 固定知识库"
                    ChatMessage.Source.AI -> "🤖 AI生成"
                    ChatMessage.Source.FALLBACK -> "💬 兜底回复"
                }

                // 加载状态
                if (msg.content.isBlank()) {
                    binding.pbLoading.visibility = View.VISIBLE
                    binding.tvBotMessage.text = binding.root.context.getString(R.string.chat_thinking)
                } else {
                    binding.pbLoading.visibility = View.GONE
                }
            }

            // 媒体预览（如果有）
            if (msg.mediaRefs.isNotEmpty()) {
                val previewContainer = if (isUser) binding.userMediaPreview else binding.botMediaPreview
                previewContainer.visibility = View.VISIBLE
                previewContainer.removeAllViews()
                for (ref in msg.mediaRefs.take(3)) {
                    val thumb = ImageView(binding.root.context).apply {
                        // 简化显示，实际应该加载图片/视频缩略图
                        setBackgroundColor(0xFF333333.toInt())
                        setPadding(8, 8, 8, 8)
                    }
                    previewContainer.addView(thumb)
                }
            }
        }
    }
}
