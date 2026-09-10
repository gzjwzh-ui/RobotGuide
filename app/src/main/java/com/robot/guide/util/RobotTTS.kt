package com.robot.guide.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/**
 * 多语言语音播报（TTS）
 * 支持普通话、粤语、英语
 */
class RobotTTS(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    fun start(preferLang: String? = null) {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val lang = preferLang ?: AppSettings(appContext).getSpeechLang()
                val locale = parseLocale(lang)
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 降级到中文
                    tts?.setLanguage(Locale.CHINESE)
                }
                // 播报暂存的文本
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

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingText = text
            return
        }
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = "1"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
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
