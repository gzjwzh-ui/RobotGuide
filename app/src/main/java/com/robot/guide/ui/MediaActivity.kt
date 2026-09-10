package com.robot.guide.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.robot.guide.databinding.ActivityMediaBinding

/**
 * 媒体展示界面 - 简化版防崩溃
 */
class MediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val tabIndex = intent.getIntExtra("tab", 0)
        binding.tvTitle.text = if (tabIndex == 0) "车辆展示" else "车辆视频"

        // 简化：点击导航时不崩溃，显示提示
        binding.root.setOnClickListener {
            Toast.makeText(this, "媒体加载中...（需连接后端服务）", Toast.LENGTH_SHORT).show()
        }
    }
}
