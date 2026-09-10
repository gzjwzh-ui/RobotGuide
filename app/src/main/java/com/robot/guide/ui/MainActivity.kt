package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.robot.guide.R
import com.robot.guide.api.RobotActionController
import com.robot.guide.api.RobotAIService
import com.robot.guide.data.ChatMessage
import com.robot.guide.databinding.ActivityMainBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync
import com.robot.guide.util.PersonDetector
import com.robot.guide.util.RobotSpeechRecognizer
import com.robot.guide.util.RobotTTS

/**
 * 主界面 - 三栏横屏布局
 * 功能：摄像头实时预览 + 人脸检测 + 自动欢迎语 + 语音输入 + 机器动作
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private lateinit var backendSync: BackendSync
    private lateinit var personDetector: PersonDetector
    private lateinit var aiService: RobotAIService
    private lateinit var actionController: RobotActionController
    private lateinit var speechRecognizer: RobotSpeechRecognizer
    private lateinit var chatAdapter: ChatAdapter
    private var tts: RobotTTS? = null

    private val requestPerms = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

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
        actionController = RobotActionController(this)
        speechRecognizer = RobotSpeechRecognizer(this)
        tts = RobotTTS(this).also { it.start() }

        setupChat()
        setupNavigation()
        setupTitleBar()
        setupQuickQuestions()
        setupInputBar()
        setupActionButtons()
        checkAndRequestPermissions()

        binding.tvRobotName.text = settings.robotName
    }

    // ========== 对话 ==========

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter
    }

    private fun sendQuestion(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return

        chatAdapter.addMessage(ChatMessage(role = ChatMessage.Role.USER, content = question))
        binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
        binding.etInput.setText("")

        val placeholdIdx = chatAdapter.itemCount
        chatAdapter.addMessage(ChatMessage(role = ChatMessage.Role.BOT, content = ""))
        binding.rvChat.scrollToPosition(placeholdIdx)

        aiService.answer(question, chatAdapter.getMessages()) { answer, source, mediaRefs ->
            runOnUiThread {
                val sourceText = when (source) {
                    ChatMessage.Source.LOCAL -> "固定题库"
                    ChatMessage.Source.AI -> "AI生成"
                    ChatMessage.Source.FALLBACK -> "兜底回复"
                }
                chatAdapter.updateLastMessageWithSource(answer, sourceText, mediaRefs)
                binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                tts?.speak(answer)
                // 自动触发匹配的机器动作
                actionController.autoTriggerFromText(answer)
            }
        }
    }

    // ========== 语音输入 ==========

    private fun startVoiceInput() {
        if (!speechRecognizer.isSupported()) {
            Toast.makeText(this, "设备不支持语音识别，请手动输入文字", Toast.LENGTH_SHORT).show()
            return
        }

        val lang = settings.robotLanguage

        speechRecognizer.startListening(
            language = lang,
            onResult = { text ->
                if (text.isNotBlank()) {
                    sendQuestion(text)
                }
            },
            onError = { msg ->
                if (msg != "没检测到语音") {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ========== 导航栏 ==========

    private fun setupNavigation() {
        binding.navHome.setOnClickListener { switchNav(NAV_HOME) }
        binding.navGallery.setOnClickListener { switchNav(NAV_GALLERY) }
        binding.navVideo.setOnClickListener { switchNav(NAV_VIDEO) }
        binding.navSettings.setOnClickListener { switchNav(NAV_SETTINGS) }
    }

    private fun switchNav(target: Int) {
        binding.navHome.isSelected = target == NAV_HOME
        binding.navGallery.isSelected = target == NAV_GALLERY
        binding.navVideo.isSelected = target == NAV_VIDEO
        binding.navSettings.isSelected = target == NAV_SETTINGS

        val navItems = listOf(
            binding.navHome, binding.navGallery, binding.navVideo, binding.navSettings
        )
        navItems.forEachIndexed { idx, nav ->
            val tv = nav.getChildAt(1) as? android.widget.TextView
            tv?.setTextColor(resources.getColor(
                if (idx == target) R.color.nav_active else R.color.nav_inactive, theme))
        }

        when (target) {
            NAV_HOME -> {}
            NAV_GALLERY -> {
                try { startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 0) }) }
                catch (e: Exception) { Toast.makeText(this, "打开车辆展示失败", Toast.LENGTH_SHORT).show() }
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
            NAV_VIDEO -> {
                try { startActivity(Intent(this, MediaActivity::class.java).apply { putExtra("tab", 1) }) }
                catch (e: Exception) { Toast.makeText(this, "打开车辆视频失败", Toast.LENGTH_SHORT).show() }
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
            NAV_SETTINGS -> {
                try { startActivity(Intent(this, SettingsActivity::class.java)) }
                catch (e: Exception) { Toast.makeText(this, "打开设置失败", Toast.LENGTH_SHORT).show() }
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
            tts?.setLanguage(langCodes[langIdx])
        }

        binding.btnMenu.setOnClickListener {
            Toast.makeText(this, "菜单", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 快捷问题 ==========

    private fun setupQuickQuestions() {
        val questions = listOf(
            "健有哪些车型?", "工厂怎么参观?", "新能源技术", "展厅怎么走?", "价格多少?"
        )
        binding.rvQuickQuestions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvQuickQuestions.adapter = QuickQuestionAdapter(questions) { sendQuestion(it) }
    }

    // ========== 输入栏 ==========

    private fun setupInputBar() {
        binding.btnSend.setOnClickListener { sendQuestion(binding.etInput.text?.toString() ?: "") }

        // 语音按钮：长按开始录音，松开发送
        binding.btnVoice.setOnClickListener {
            if (speechRecognizer.isListening()) {
                speechRecognizer.stopListening()
            } else {
                startVoiceInput()
            }
        }

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendQuestion(binding.etInput.text?.toString() ?: "")
                true
            } else false
        }
    }

    // ========== 机器动作按钮（调试用） ==========

    private fun setupActionButtons() {
        // 从左栏的"头部"状态点击触发动作
        binding.tvHeadStatus.setOnClickListener {
            // 随机触发一个动作演示
            val actions = RobotActionController.Action.values()
            val action = actions.random()
            actionController.execute(action) { success, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
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

        // 2. 人脸检测 + 摄像头预览 + 自动欢迎语
        if (settings.personDetection) {
            personDetector.start(
                binding.cameraPreview,
                this,
                PersonDetector.Callback(
                    onPersonEnter = {
                        runOnUiThread {
                            binding.tvHumanStatus.text = "HUMAN DETECTED"
                            binding.tvHumanStatus.setTextColor(
                                resources.getColor(R.color.colorPrimary, theme))
                            binding.tvDetectDetail.text = "● 检测到 1 人 · 置信度 98%"
                            binding.tvDetectDetail.setTextColor(
                                resources.getColor(R.color.success, theme))

                            // TTS 欢迎语
                            val greeting = settings.greeting.ifBlank {
                                "有什么可以帮到你，我是${settings.robotName}"
                            }
                            tts?.speak(greeting)

                            // 打招呼动作
                            actionController.execute(RobotActionController.Action.WAVE)
                            actionController.execute(RobotActionController.Action.GREET)

                            // 对话区显示欢迎
                            chatAdapter.addMessage(ChatMessage(
                                role = ChatMessage.Role.BOT, content = greeting
                            ))
                            binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                        }
                    },
                    onPersonLeave = {
                        runOnUiThread {
                            binding.tvHumanStatus.text = "NO HUMAN"
                            binding.tvHumanStatus.setTextColor(
                                resources.getColor(R.color.text_hint, theme))
                            binding.tvDetectDetail.text = "● 等待检测..."
                            binding.tvDetectDetail.setTextColor(
                                resources.getColor(R.color.text_hint, theme))
                        }
                    },
                    onStatusChange = { detected ->
                        runOnUiThread {
                            if (detected) {
                                binding.tvDetectDetail.text = "● 检测到 1 人 · 置信度 98%"
                                binding.tvDetectDetail.setTextColor(
                                    resources.getColor(R.color.success, theme))
                            }
                        }
                    }
                )
            )
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
        try { personDetector.stop() } catch (_: Exception) {}
        speechRecognizer.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { personDetector.stop() } catch (_: Exception) {}
        speechRecognizer.destroy()
        tts?.shutdown()
    }
}
