package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.robot.guide.R
import com.robot.guide.api.RobotAIService
import com.robot.guide.data.ChatMessage
import com.robot.guide.databinding.ActivityChatBinding
import com.robot.guide.util.AppSettings
import com.robot.guide.util.RobotTTS
import java.util.Locale

/**
 * AI对话界面 - 支持多语言 + TTS语音播报
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var aiService: RobotAIService
    private lateinit var settings: AppSettings
    private lateinit var tts: RobotTTS
    private val history = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)
        aiService = RobotAIService(this)
        tts = RobotTTS(this)
        tts.start()

        setupUI()

        // 如果是从人脸检测自动打开，可能带开场白参数
        val autoGreeting = intent.getBooleanExtra("auto_greeting", false)
        if (autoGreeting) {
            addBotMessage(settings.getGreeting(), speak = true)
        } else {
            addBotMessage(settings.getGreeting(), speak = false)
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            tts.shutdown()
            finish()
        }

        // 标题显示机器人名字
        binding.tvTitle.text = settings.robotName

        binding.rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        adapter = ChatAdapter()
        binding.rvChat.adapter = adapter

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        binding.btnVoice.setOnClickListener { toggleVoiceInput() }
        binding.btnMediaGallery.setOnClickListener {
            startActivity(Intent(this, MediaActivity::class.java))
        }
    }

    private fun addBotMessage(text: String, speak: Boolean = false) {
        val msg = ChatMessage(role = ChatMessage.Role.BOT, content = text)
        history.add(msg)
        adapter.addMessage(msg)
        scrollToBottom()
        if (speak) tts.speak(text)
    }

    private fun sendMessage() {
        val text = binding.etInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.etInput.text.clear()

        val userMsg = ChatMessage(role = ChatMessage.Role.USER, content = text)
        history.add(userMsg)
        adapter.addMessage(userMsg)

        // 思考中占位
        val placeholder = ChatMessage(role = ChatMessage.Role.BOT, content = "")
        history.add(placeholder)
        val placeholderIndex = history.size - 1
        adapter.addMessage(placeholder)
        scrollToBottom()

        aiService.answer(text, history) { answer, source, mediaRefs ->
            runOnUiThread {
                history[placeholderIndex] = ChatMessage(
                    role = ChatMessage.Role.BOT, content = answer,
                    source = source, mediaRefs = mediaRefs
                )
                adapter.updateLastMessage(answer)
                scrollToBottom()
                tts.speak(answer)

                if (mediaRefs.isNotEmpty() && settings.autoShowMedia) {
                    Toast.makeText(
                        this, "已关联 ${mediaRefs.size} 个媒体文件", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.rvChat.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    // ==================== 语音输入（多语言） ====================

    private fun toggleVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                return
            }
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.getSpeechLang())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.chat_voice_tip))

        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "语音输入不可用，请用文字输入", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                binding.etInput.setText(matches[0])
                sendMessage()
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val VOICE_REQUEST_CODE = 200
    }
}
