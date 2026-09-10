package com.robot.guide.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.robot.guide.R
import com.robot.guide.data.QAItem
import com.robot.guide.db.DatabaseHelper
import com.robot.guide.databinding.ActivityQaEditBinding

/**
 * 问答编辑/新建
 */
class QAEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQaEditBinding
    private var editingId: Long = -1
    private var existingQA: QAItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQaEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveQA() }
    }

    private fun loadData() {
        editingId = intent.getLongExtra("qa_id", -1)

        if (editingId > 0) {
            binding.tvTitle.setText(R.string.qa_edit_title)
            existingQA = DatabaseHelper.getInstance(this).getQAById(editingId)
            existingQA?.let { qa ->
                binding.etQuestion.setText(qa.question)
                binding.etAnswer.setText(qa.answer)
                binding.etKeywords.setText(qa.keywords.joinToString(","))
                binding.etMediaRef.setText(qa.mediaRefs.joinToString(","))
            }
        } else {
            binding.tvTitle.setText(R.string.qa_new_title)
        }
    }

    private fun saveQA() {
        val question = binding.etQuestion.text.toString().trim()
        val answer = binding.etAnswer.text.toString().trim()

        if (question.isEmpty()) {
            binding.etQuestion.error = getString(R.string.qa_question_required)
            return
        }
        if (answer.isEmpty()) {
            binding.etAnswer.error = getString(R.string.qa_answer_required)
            return
        }

        val keywords = binding.etKeywords.text.toString()
            .split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val mediaRefs = binding.etMediaRef.text.toString()
            .split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val qa = (existingQA ?: QAItem(question = question, answer = answer)).copy(
            question = question,
            answer = answer,
            keywords = keywords,
            mediaRefs = mediaRefs,
            updatedAt = System.currentTimeMillis()
        )

        val db = DatabaseHelper.getInstance(this)
        if (editingId > 0 && existingQA != null) {
            db.updateQA(qa.copy(id = editingId))
            Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
        } else {
            db.insertQA(qa)
            Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
