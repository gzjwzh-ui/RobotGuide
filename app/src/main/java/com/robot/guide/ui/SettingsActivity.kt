package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.robot.guide.R
import com.robot.guide.api.DoubaoClient
import com.robot.guide.databinding.ActivitySettingsBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync

/**
 * 系统设置界面 - 后端URL + 机器人身份 + AI配置 + 同步
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

        // ===== 机器人身份 =====
        binding.etRobotName.setText(settings.robotName)
        binding.etGreeting.setText(settings.greeting)
        binding.etAutoWakeWords.setText(settings.autoWakeWords)

        binding.spnLanguage.setSelection(
            listOf("zh-CN", "yue-HK", "en-US").indexOf(settings.robotLanguage).coerceAtLeast(0)
        )

        binding.swPersonDetection.isChecked = settings.personDetection
        binding.etIdleTimeout.setText(settings.idleTimeoutMin.toString())

        // ===== AI =====
        binding.swUseAI.isChecked = settings.useAI
        binding.etApiKey.setText(settings.apiKey)
        binding.etModelId.setText(settings.modelId)
        binding.etSystemPrompt.setText(settings.systemPrompt)
        binding.sbThreshold.progress = settings.matchThreshold
        binding.tvThresholdValue.text = settings.matchThreshold.toString()
    }

    private fun setupEvents() {
        binding.btnBack.setOnClickListener { finish() }

        binding.sbThreshold.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvThresholdValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnManageQA.setOnClickListener {
            startActivity(Intent(this, QALibraryActivity::class.java))
        }

        binding.btnImportQA.setOnClickListener {
            Toast.makeText(this, "请通过网页后台管理问答库", Toast.LENGTH_SHORT).show()
        }

        binding.btnExportQA.setOnClickListener {
            Toast.makeText(this, "请到网页后台导出", Toast.LENGTH_SHORT).show()
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

        // ===== 机器人身份 =====
        binding.etRobotName.text.toString().trim().let { settings.robotName = it }
        binding.etGreeting.text.toString().trim().let { settings.greeting = it }
        binding.etAutoWakeWords.text.toString().trim().let { settings.autoWakeWords = it }
        val langs = listOf("zh-CN", "yue-HK", "en-US")
        settings.robotLanguage = langs[binding.spnLanguage.selectedItemPosition.coerceIn(0, 2)]

        settings.personDetection = binding.swPersonDetection.isChecked
        binding.etIdleTimeout.text.toString().toIntOrNull()?.let { settings.idleTimeoutMin = it }

        // ===== AI =====
        settings.useAI = binding.swUseAI.isChecked
        settings.apiKey = binding.etApiKey.text.toString().trim()
        settings.modelId = binding.etModelId.text.toString().trim()
        settings.systemPrompt = binding.etSystemPrompt.text.toString().trim()
        settings.matchThreshold = binding.sbThreshold.progress

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

        if (settings.useAI && settings.isAIConfigured()) {
            DoubaoClient(settings.apiKey, settings.modelId, settings.systemPrompt)
                .testConnection { success, msg ->
                    runOnUiThread {
                        Toast.makeText(this, "AI测试: ${if (success) "✅" else "❌"} $msg", Toast.LENGTH_LONG).show()
                    }
                }
        }

        finish()
    }
}
