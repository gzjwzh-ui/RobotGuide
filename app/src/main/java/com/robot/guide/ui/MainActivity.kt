package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.robot.guide.data.RobotStatus
import com.robot.guide.databinding.ActivityMainBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.BackendSync
import com.robot.guide.util.PersonDetector
import com.robot.guide.util.RobotSpeechRecognizer
import com.robot.guide.util.RobotTTS

/**
 * 主界面 - 三栏横屏布局
 * 功能：摄像头实时预览 + 人脸检测 + 自动欢迎语 + 语音输入 + 机器动作 + 硬件状态
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

    // 状态轮询
    private val handler = Handler(Looper.getMainLooper())
    private var statusPollRunning = false
    private val statusPollIntervalMs = 2000L
    private val statusPoll = object : Runnable {
        override fun run() {
            if (!statusPollRunning) return
            actionController.fetchStatus { status ->
                runOnUiThread { updateHardwareStatusUI(status) }
            }
            handler.postDelayed(this, statusPollIntervalMs)
        }
    }

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

        // 🔥 用户提问里如果含硬件关键词，**不等 AI 回答就先触发**（快速响应）
        actionController.autoTriggerFromText(question)

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
                speechRecognizer.pauseForSpeech()
                tts?.speak(answer, onDone = { speechRecognizer.resumeAfterSpeech() })
                // AI 回答里如果又提到硬件词，也触发一次（比如"我可以帮你前进..."）
                actionController.autoTriggerFromText(answer)
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
                try {
                    val intent = Intent(this, SettingsActivity::class.java)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    Toast.makeText(this, "打开设置失败", Toast.LENGTH_SHORT).show()
                }
                binding.navHome.postDelayed({ switchNav(NAV_HOME) }, 200)
            }
        }
    }

    // ========== 顶部标题栏 ==========

    private fun setupTitleBar() {
        // 语言按钮和三点菜单已移除，只保留普通话
        // 语言由后台同步控制，APP 端不再切换
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

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendQuestion(binding.etInput.text?.toString() ?: "")
                true
            } else false
        }
    }

    /**
     * 自动启动语音识别（连续监听模式）
     */
    private fun startAutoListening() {
        if (!speechRecognizer.isSupported()) return

        val lang = settings.robotLanguage
        speechRecognizer.startListening(
            language = lang,
            onResult = { text ->
                if (text.isNotBlank()) {
                    sendQuestion(text)
                }
            },
            onError = { msg ->
                if (msg != "没检测到语音" && msg != "没听清，请再说一次" && msg != "长时间没说话") {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ========== 机器快捷动作 + 硬件状态 ==========

    private var quickActionCursor = 0
    private val quickActions = listOf(
        RobotActionController.Action.COMBO_STRETCH,     // 活动筋骨（打招呼）
        RobotActionController.Action.HEAD_RESET_ALL,   // 头部手臂复位
        RobotActionController.Action.EAR_LED_ON,        // 耳朵灯开
        RobotActionController.Action.EYE_LED_ON,        // 眼睛灯开
        RobotActionController.Action.HEAD_LEFT,         // 头部左
        RobotActionController.Action.HEAD_RIGHT,        // 头部右
        RobotActionController.Action.HEAD_UP,            // 头部上
        RobotActionController.Action.HEAD_DOWN,          // 头部下
        RobotActionController.Action.BASE_TURN_LEFT_90, // 左转90°
        RobotActionController.Action.BASE_STOP,         // 底座停止
    )

    private fun setupActionButtons() {
        // 点击"头部"状态卡片 → 依次触发快捷动作（方便演示/调机）
        binding.tvHeadStatus.setOnClickListener { triggerNextQuickAction() }
    }

    private fun triggerNextQuickAction() {
        val action = quickActions[quickActionCursor % quickActions.size]
        quickActionCursor++
        actionController.execute(action) { ok, msg ->
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStatusPoll() {
        if (statusPollRunning) return
        statusPollRunning = true
        handler.post(statusPoll)
    }
    private fun stopStatusPoll() {
        statusPollRunning = false
        handler.removeCallbacks(statusPoll)
    }

    /** 把后端 /api/robot/status 快照映射到左栏状态行 */
    private fun updateHardwareStatusUI(status: RobotStatus?) {
        if (status == null) {
            binding.tvBackendStatus.text = "● 离线"
            binding.tvBackendStatus.setTextColor(resources.getColor(R.color.error, theme))
            return
        }
        // 后端连接 + 硬件连接状态
        binding.tvBackendStatus.text = "● 在线" + if (status.hardwareConnected) " · 硬件已连" else ""
        binding.tvBackendStatus.setTextColor(resources.getColor(R.color.success, theme))

        // 头部状态
        binding.tvHeadStatus.text = when (status.head.status) {
            "moving"    -> "移动中 · ${status.head.angle}°"
            "resetting" -> "复位中"
            else        -> "${status.head.angle}° · 正常"
        }

        // 把最有意义的传感器汇总显示在 tvVoiceStatus 上（超声波最近距离）
        val us = status.sensors.ultrasonic
        val nearest = minOf(us.front, us.midCenter, us.leftCenter, us.rightCenter)
        val nearestLabel = when (nearest) {
            in 200..255 -> "安全"
            in 100..199 -> "注意"
            in 30..99   -> "接近！"
            else        -> "过近 ⚠"
        }
        binding.tvVoiceStatus.text = "前方 ${nearest} · $nearestLabel"
        binding.tvVoiceStatus.setTextColor(resources.getColor(
            if (nearest < 100) R.color.error
            else if (nearest < 200) R.color.colorPrimary
            else R.color.success, theme))

        // 灯光状态拼在检测详情行
        val earOn = if (status.led.ear) "👂ON" else "👂OFF"
        val eyeOn = if (status.led.eye) "👁ON" else "👁OFF"
        binding.tvDetectDetail.text = "● 耳朵灯: $earOn   眼睛灯: $eyeOn"

        // 顶部标题里反映底座状态 + 硬件连接
        val baseLabel = when {
            status.base.moving -> "移动中(${status.base.direction})"
            else                -> "待机"
        }
        val hwTag = if (status.hardwareConnected) "ONLINE · HW" else "ONLINE · 模拟"
        binding.tvRobotStatus.text = "$hwTag · $baseLabel"
        binding.tvRobotStatus.setTextColor(resources.getColor(
            if (status.hardwareConnected) R.color.success else R.color.colorPrimary, theme))
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // 设置页面返回后，重新加载 TTS 语言和音量
            val lang = data.getStringExtra("language") ?: settings.robotLanguage
            val volume = data.getIntExtra("volume", settings.ttsVolumeInt)
            tts?.reloadLanguage(lang)
            tts?.setVolume(volume / 100f)
            Toast.makeText(this, "语言和音量已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initFeatures() {
        // 0. 启动硬件状态轮询（每 2 秒一次）
        startStatusPoll()

        // 1. 后端同步
        if (settings.syncEnabled) {
            backendSync.syncAll { ok, msg, _ ->
                runOnUiThread {
                    binding.tvBackendStatus.text = if (ok) "● 在线" else "● 离线"
                    binding.tvBackendStatus.setTextColor(
                        resources.getColor(if (ok) R.color.success else R.color.error, theme))
                    if (ok) {
                        // 同步成功后重新加载 TTS 语言（后台可能改了语言设置）
                        val syncedLang = settings.robotLanguage
                        tts?.reloadLanguage(syncedLang)
                        // 更新机器人名字
                        binding.tvRobotName.text = settings.robotName
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
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

                            // TTS 欢迎语（只在人离开后→新人到来时说一次）
                            val greeting = settings.greeting.ifBlank {
                                "您好，我是${settings.robotName}"
                            }
                            speechRecognizer.pauseForSpeech()
                            tts?.speak(greeting, onDone = {
                                speechRecognizer.resumeAfterSpeech()
                                startAutoListening()
                            })

                            // 打招呼动作
                            actionController.execute(RobotActionController.Action.COMBO_STRETCH)

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
        } else {
            // 未启用人脸检测也自动启动语音识别
            startAutoListening()
        }
    }

    override fun onResume() {
        super.onResume()
        if (settings.personDetection && personDetector.isSupported()) {
            try { personDetector.stop() } catch (_: Exception) {}
            initFeatures()
        } else {
            startAutoListening()
        }
        binding.tvRobotName.text = settings.robotName
    }

    override fun onPause() {
        super.onPause()
        stopStatusPoll()
        try { personDetector.stop() } catch (_: Exception) {}
        speechRecognizer.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStatusPoll()
        try { personDetector.stop() } catch (_: Exception) {}
        speechRecognizer.destroy()
        tts?.shutdown()
    }
}
