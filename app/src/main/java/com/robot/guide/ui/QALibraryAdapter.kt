package com.robot.guide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.robot.guide.data.QAItem
import com.robot.guide.databinding.ItemQaBinding

/**
 * 问答库列表适配器
 */
class QALibraryAdapter(
    private val onEdit: (QAItem) -> Unit,
    private val onDelete: (QAItem) -> Unit
) : RecyclerView.Adapter<QALibraryAdapter.ViewHolder>() {

    private var items: List<QAItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setData(newItems: List<QAItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getData() = items

    inner class ViewHolder(private val binding: ItemQaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(qa: QAItem) {
            binding.tvQuestion.text = qa.question
            binding.tvAnswer.text = qa.answer

            val keywordsText = if (qa.keywords.isNotEmpty()) {
                "关键词: ${qa.keywords.joinToString(", ")}"
            } else {
                ""
            }
            val mediaText = if (qa.mediaRefs.isNotEmpty()) {
                "${if (keywordsText.isNotEmpty()) " | " else ""}媒体: ${qa.mediaRefs.joinToString(", ")}"
            } else {
                ""
            }
            binding.tvKeywords.text = keywordsText + mediaText
            binding.tvKeywords.visibility =
                if (keywordsText.isEmpty() && mediaText.isEmpty()) View.GONE else View.VISIBLE

            binding.btnEdit.setOnClickListener { onEdit(qa) }
            binding.btnDelete.setOnClickListener { onDelete(qa) }
        }
    }
}
