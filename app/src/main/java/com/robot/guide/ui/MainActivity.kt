package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.robot.guide.R
import com.robot.guide.api.RobotAIService
import com.robot.guide.data.ChatMessage
import com.robot.guide.databinding.ActivityMainBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync
import com.robot.guide.util.PersonDetector
import com.robot.guide.util.RobotTTS

/**
 * 主界面 - 三栏横屏布局（左检测 + 中对话 + 右导航）
 * 合并原 ChatActivity 对话功能，不再跳转
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private lateinit var backendSync: BackendSync
    private lateinit var personDetector: PersonDetector
    private lateinit var aiService: RobotAIService
    private lateinit var chatAdapter: ChatAdapter
    private var tts: RobotTTS? = null

    private val requestPerms = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var currentNav = NAV_HOME

    companion object {
        const val NAV_HOME = 0
        const val NAV_GALLERY = 1
        const val NAV_VIDEO = 2
        const val NAV_SETTINGS = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)
        backendSync = BackendSync(this)
        personDetector = PersonDetector(this)
        aiService = RobotAIService(this)
        tts = RobotTTS(this).also { it.start() }

        setupChat()
        setupNavigation()
        setupTitleBar()
        setupQuickQuestions()
        setupInputBar()
        checkAndRequestPermissions()

        // 更新机器人名字
        binding.tvRobotName.text = settings.robotName
    }

    // ========== 对话功能（从 ChatActivity 合并） ==========

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter
    }

    private fun sendQuestion(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return

        // 1. 添加用户消息
        chatAdapter.addMessage(ChatMessage(role = ChatMessage.Role.USER, content = question))
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)

        // 2. 清空输入框
        binding.etInput.setText("")

        // 3. 添加机器人占位消息
        val placeholdIdx = chatAdapter.itemCount
        chatAdapter.addMessage(ChatMessage(role = ChatMessage.Role.BOT, content = ""))
        binding.rvChat.scrollToPosition(placeholdIdx)

        // 4. 调用 AI 服务（优先固定问答库 → AI → 兜底）
        aiService.answer(question, chatAdapter.getMessages()) { answer, source, mediaRefs ->
            runOnUiThread {
                val sourceText = when (source) {
                    ChatMessage.Source.LOCAL -> "固定题库"
                    ChatMessage.Source.AI -> "AI生成"
                    ChatMessage.Source.FALLBACK -> "兜底回复"
                }
                chatAdapter.updateLastMessageWithSource(answer, sourceText, mediaRefs)
                binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)

                // 5. TTS 播报
                if (settings.useAI) {
                    tts?.speak(answer)
                }
            }
        }
    }

    // ========== 导航栏 ==========

    private fun setupNavigation() {
        binding.navHome.setOnClickListener { switchNav(NAV_HOME) }
        binding.navGallery.setOnClickListener { switchNav(NAV_GALLERY) }
        binding.navVideo.setOnClickListener { switchNav(NAV_VIDEO) }
        binding.navSettings.setOnClickListener { switchNav(NAV_SETTINGS) }
    }

    private fun switchNav(target: Int) {
        currentNav = target

        // 更新选中状态
        binding.navHome.isSelected = target == NAV_HOME
        binding.navGallery.isSelected = target == NAV_GALLERY
        binding.navVideo.isSelected = target == NAV_VIDEO
        binding.navSettings.isSelected = target == NAV_SETTINGS

        // 更新文字颜色
        val navs = listOf(binding.navHome to binding.navHome.getChildAt(1),
                          binding.navGallery to binding.navGallery.getChildAt(1),
                          binding.navVideo to binding.navVideo.getChildAt(1),
                          binding.navSettings to binding.navSettings.getChildAt(1))
        navs.forEachIndexed { idx, pair ->
            val tv = pair.second as android.widget.TextView
            tv.setTextColor(resources.getColor(
                if (idx == target) R.color.nav_active else R.color.nav_inactive, theme))
        }

        // 执行导航动作
        when (target) {
            NAV_HOME -> {
                // 切回对话（默认就是）
            }
            NAV_GALLERY -> {
                startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 0) })
                // 延迟重置选中，返回时会恢复
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
            NAV_VIDEO -> {
                startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 1) })
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
            NAV_SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
        }
    }

    // ========== 顶部标题栏 ==========

    private fun setupTitleBar() {
        val languages = listOf("zh-CN 普通话", "yue-HK 粤语", "en-US English")
        val langCodes = listOf("zh-CN", "yue-HK", "en-US")
        var langIdx = langCodes.indexOf(settings.robotLanguage).coerceAtLeast(0)

        binding.btnLanguage.text = languages[langIdx].substringAfter(" ")

        binding.btnLanguage.setOnClickListener {
            langIdx = (langIdx + 1) % languages.size
            settings.robotLanguage = langCodes[langIdx]
            binding.btnLanguage.text = languages[langIdx].substringAfter(" ")
        }

        binding.btnMenu.setOnClickListener {
            Toast.makeText(this, "菜单", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 快捷问题 ==========

    private fun setupQuickQuestions() {
        val questions = listOf(
            "健有哪些车型?",
            "工厂怎么参观?",
            "新能源技术",
            "展厅怎么走?",
            "价格多少?"
        )

        binding.rvQuickQuestions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvQuickQuestions.adapter = QuickQuestionAdapter(questions) { q ->
            sendQuestion(q)
        }
    }

    // ========== 输入栏 ==========

    private fun setupInputBar() {
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text?.toString() ?: ""
            sendQuestion(text)
        }

        binding.btnVoice.setOnClickListener {
            // 简化：模拟语音输入 → 直接引导对话
            Toast.makeText(this, "语音输入功能开发中…", Toast.LENGTH_SHORT).show()
        }

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendQuestion(binding.etInput.text?.toString() ?: "")
                true
            } else false
        }
    }

    // ========== 权限 & 初始化 ==========

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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initFeatures()
            }
        }
    }

    private fun initFeatures() {
        // 1. 后端同步
        if (settings.syncEnabled) {
            backendSync.syncAll { ok, msg, _ ->
                runOnUiThread {
                    binding.tvBackendStatus.text = if (ok) "● 在线" else "● 离线"
                    binding.tvBackendStatus.setTextColor(
                        resources.getColor(if (ok) R.color.success else R.color.error, theme))
                    if (ok) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.tvBackendStatus.text = "未启用"
            binding.tvBackendStatus.setTextColor(resources.getColor(R.color.text_hint, theme))
        }

        // 2. 人脸检测（更新 UI 状态）
        if (settings.personDetection) {
            personDetector.start(PersonDetector.Callback(
                onPersonEnter = {
                    runOnUiThread {
                        binding.tvHumanStatus.text = "HUMAN DETECTED"
                        binding.tvHumanStatus.setTextColor(resources.getColor(R.color.colorPrimary, theme))
                    }
                },
                onPersonLeave = {
                    runOnUiThread {
                        binding.tvHumanStatus.text = "NO HUMAN"
                        binding.tvHumanStatus.setTextColor(resources.getColor(R.color.text_hint, theme))
                        binding.tvDetectDetail.text = "● 等待检测..."
                    }
                },
                onStatusChange = { detected ->
                    runOnUiThread {
                        if (detected) {
                            binding.tvDetectDetail.text = "● 检测到 1 人 · 置信度 98%"
                            binding.tvDetectDetail.setTextColor(resources.getColor(R.color.success, theme))
                        }
                    }
                }
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        if (settings.personDetection && personDetector.isSupported()) {
            try { personDetector.stop() } catch (_: Exception) {}
            initFeatures()
        }
        binding.tvRobotName.text = settings.robotName
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
