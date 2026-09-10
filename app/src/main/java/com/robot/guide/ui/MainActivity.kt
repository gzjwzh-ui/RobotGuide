package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.robot.guide.R
import com.robot.guide.databinding.ActivityMainBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync
import com.robot.guide.util.PersonDetector
import com.robot.guide.util.RobotTTS

/**
 * 主界面 - 快捷入口 + 人脸检测自动唤醒
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private lateinit var backendSync: BackendSync
    private lateinit var personDetector: PersonDetector
    private var tts: RobotTTS? = null

    private val requestPerms = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)
        backendSync = BackendSync(this)
        personDetector = PersonDetector(this)

        setupQuickActions()
        setupSettingsBtn()
        checkAndRequestPermissions()
    }

    private fun setupQuickActions() {
        val actions = listOf(
            QuickAction(android.R.drawable.ic_btn_speak_now, R.string.quick_chat) {
                goChat(autoGreeting = false)
            },
            QuickAction(android.R.drawable.ic_menu_gallery, R.string.quick_gallery) {
                startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 0) })
            },
            QuickAction(android.R.drawable.ic_media_play, R.string.quick_video) {
                startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 1) })
            },
            QuickAction(android.R.drawable.ic_menu_manage, R.string.quick_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        )
        binding.rvQuickActions.layoutManager = GridLayoutManager(this, 2)
        binding.rvQuickActions.adapter = QuickActionAdapter(actions)
    }

    private fun setupSettingsBtn() {
        binding.btnQuickChat.setOnClickListener { goChat(autoGreeting = false) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun goChat(autoGreeting: Boolean) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("auto_greeting", autoGreeting)
        })
    }

    private fun checkAndRequestPermissions() {
        val needPerms = requestPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPerms.toTypedArray(), 1)
        } else {
            initFeatures()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            // 只要至少相机权限OK就尝试启动检测
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initFeatures()
            }
        }
    }

    private fun initFeatures() {
        // 1. 尝试从后端同步
        if (settings.syncEnabled) {
            backendSync.syncAll { ok, msg, _ ->
                runOnUiThread {
                    if (ok) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. 启动人脸检测自动唤醒
        if (settings.personDetection) {
            personDetector.start(PersonDetector.Callback(
                onPersonEnter = {
                    runOnUiThread {
                        // 检测到人 -> 自动打开对话并播放问候
                        goChat(autoGreeting = true)
                    }
                },
                onPersonLeave = { /* 可以做些清理 */ },
                onStatusChange = { /* 状态变化可选处理 */ }
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        // 返回主界面时重新启动检测
        if (settings.personDetection && personDetector.isSupported()) {
            // 重启检测（简单处理，每次onResume重新start）
            try { personDetector.stop() } catch (_: Exception) {}
            initFeatures()
        }
    }

    override fun onPause() {
        super.onPause()
        personDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        personDetector.stop()
        tts?.shutdown()
    }
}
