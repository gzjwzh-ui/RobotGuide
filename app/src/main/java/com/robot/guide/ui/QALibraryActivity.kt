package com.robot.guide.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.robot.guide.R
import com.robot.guide.data.QAItem
import com.robot.guide.db.DatabaseHelper
import com.robot.guide.databinding.ActivityQaLibraryBinding

/**
 * 固定问答库管理
 */
class QALibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQaLibraryBinding
    private lateinit var adapter: QALibraryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQaLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, QAEditActivity::class.java))
        }

        adapter = QALibraryAdapter(
            onEdit = { item ->
                val intent = Intent(this, QAEditActivity::class.java)
                intent.putExtra("qa_id", item.id)
                startActivity(intent)
            },
            onDelete = { item ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_delete))
                    .setMessage("确定删除「${item.question}」吗？")
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        DatabaseHelper.getInstance(this).deleteQA(item.id)
                        loadData()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        )
        binding.rvQA.layoutManager = LinearLayoutManager(this)
        binding.rvQA.adapter = adapter
    }

    private fun loadData() {
        val items = DatabaseHelper.getInstance(this).getAllQA()
        adapter.setData(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
