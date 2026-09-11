package com.robot.guide.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.robot.guide.R
import com.robot.guide.databinding.ActivitySettingsBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync

/**
 * 系统设置界面 - 精简版
 * 仅保留 APP 专属设置：后端地址、自动同步、音量调节
 * 其余配置（机器人身份、语言、AI 等）请通过网页后台管理
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = AppSettings(this)
        loadSettings()
        setupEvents()
    }

    private fun loadSettings() {
        // ===== 后端 =====
        binding.etBackendUrl.setText(settings.backendUrl)
        binding.swSyncEnabled.isChecked = settings.syncEnabled

        // ===== TTS 音量 =====
        binding.sbTtsVolume.progress = settings.ttsVolumeInt
        binding.tvTtsVolumeValue.text = "${settings.ttsVolumeInt}%"
    }

    private fun setupEvents() {
        binding.btnBack.setOnClickListener { finish() }

        binding.sbTtsVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTtsVolumeValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seek: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seek: android.widget.SeekBar?) {}
        })

        binding.btnManageQA.setOnClickListener {
            startActivity(Intent(this, QALibraryActivity::class.java))
        }

        binding.btnClearQA.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_clear))
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    com.robot.guide.db.DatabaseHelper.getInstance(this).deleteAll()
                    Toast.makeText(this, "已清空问答库", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.btnTestBackend.setOnClickListener {
            val url = binding.etBackendUrl.text?.toString()?.trim() ?: ""
            if (url.isBlank()) {
                Toast.makeText(this, "请输入后端地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val testSettings = AppSettings(this).apply { backendUrl = url }
            BackendSync(this).testConnection { ok, msg ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "✅ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnSyncNow.setOnClickListener {
            val url = binding.etBackendUrl.text?.toString()?.trim() ?: ""
            if (url.isBlank()) {
                Toast.makeText(this, "请输入后端地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSettings()
            BackendSync(this).syncAll { ok, msg, _ ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "✅ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
                    if (ok) finish()
                }
            }
        }

        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        // ===== 后端 =====
        binding.etBackendUrl.text.toString().trim().let { settings.backendUrl = it }
        settings.syncEnabled = binding.swSyncEnabled.isChecked

        // ===== TTS 音量 =====
        settings.ttsVolumeInt = binding.sbTtsVolume.progress

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

        // 通知主界面重新加载 TTS 音量
        val resultIntent = Intent().apply {
            putExtra("settings_changed", true)
            putExtra("language", settings.robotLanguage)
            putExtra("volume", settings.ttsVolumeInt)
        }
        setResult(RESULT_OK, resultIntent)

        finish()
    }
}
