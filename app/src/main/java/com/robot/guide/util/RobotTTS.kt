package com.robot.guide.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 多语言语音播报（TTS）
 * 支持普通话、粤语、英语
 *
 * 改进：增加 onDone 回调，用于配合语音识别暂停/恢复
 */
class RobotTTS(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var onDone: (() -> Unit)? = null

    fun start(preferLang: String? = null) {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val lang = preferLang ?: AppSettings(appContext).getSpeechLang()
                val locale = parseLocale(lang)
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.CHINESE)
                }
                pendingText?.let { speak(it) }
                pendingText = null
            }
        }
    }

    private fun parseLocale(lang: String): Locale {
        return when {
            lang.startsWith("yue") -> Locale("zh", "HK")
            lang.startsWith("en") -> Locale.US
            else -> Locale.CHINESE
        }
    }

    /**
     * 说一段话
     * @param text 要播报的文本
     * @param onDone 说完后的回调（用于恢复语音识别）
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        this.onDone = onDone
        if (!ready) {
            pendingText = text
            return
        }
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = "1"
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "robot_tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "robot_tts_${System.currentTimeMillis()}")

        // 尝试用 UtteranceProgressListener 获得准确完成事件
        try {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { this@RobotTTS.onDone?.invoke() }
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { this@RobotTTS.onDone?.invoke() }
                }
            })
        } catch (_: Exception) {}

        // 兜底：如果 UtteranceProgressListener 不工作，按字数估算时长
        val estimatedMs = (text.length * 150L).coerceAtLeast(2000L) // 约 150ms/字
        mainHandler.postDelayed({
            this@RobotTTS.onDone?.invoke()
        }, estimatedMs + 500)
    }

    fun setLanguage(lang: String) {
        tts?.setLanguage(parseLocale(lang))
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
