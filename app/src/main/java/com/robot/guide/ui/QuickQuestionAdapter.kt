package com.robot.guide.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.robot.guide.databinding.ItemQuickQuestionBinding

/**
 * 快捷问题按钮适配器
 */
class QuickQuestionAdapter(
    private val questions: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<QuickQuestionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuickQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(questions[position])
    }

    override fun getItemCount() = questions.size

    inner class ViewHolder(private val binding: ItemQuickQuestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(question: String) {
            binding.tvQuickQuestion.text = question
            binding.root.setOnClickListener { onClick(question) }
        }
    }
}
