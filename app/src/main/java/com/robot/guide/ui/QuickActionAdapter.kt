package com.robot.guide.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.robot.guide.databinding.ItemQuickActionBinding

/**
 * 主界面快捷功能适配器
 */
data class QuickAction(
    val iconRes: Int,
    val labelRes: Int,
    val action: () -> Unit
)

class QuickActionAdapter(
    private val actions: List<QuickAction>
) : RecyclerView.Adapter<QuickActionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuickActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(actions[position])
    }

    override fun getItemCount() = actions.size

    inner class ViewHolder(private val binding: ItemQuickActionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(action: QuickAction) {
            binding.ivIcon.setImageResource(action.iconRes)
            binding.tvLabel.setText(action.labelRes)
            binding.root.setOnClickListener { action.action() }
        }
    }
}
