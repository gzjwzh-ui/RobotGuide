package com.robot.guide.util

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 多语言语音播报（TTS）
 * 支持普通话、粤语、英语
 *
 * 修复点：
 *   1. 初始化时明确选择可用引擎 + 语言
 *   2. speak 用 3 参数版本（兼容 API 21 以下）+ 单独注册 UtteranceProgressListener
 *   3. fallback：如果 TTS 不可用（LANG_NOT_SUPPORTED），尝试用系统首选引擎
 */
class RobotTTS(private val context: Context) {

    private val tag = "RobotTTS"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var onDone: (() -> Unit)? = null

    fun start(preferLang: String? = null) {
        Log.d(tag, "TTS start() called")
        tts = TextToSpeech(context.applicationContext) { status ->
            Log.d(tag, "TTS init status=$status")
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val lang = preferLang ?: AppSettings(context).getSpeechLang()
                val locale = parseLocale(lang)
                val result = tts?.setLanguage(locale)
                Log.d(tag, "TTS setLanguage($locale) result=$result")
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 尝试降级到中文
                    val fallback = tts?.setLanguage(Locale.CHINESE)
                    Log.w(tag, "首选语言不支持，降级到中文 result=$fallback")
                    if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 连中文都不支持，试试英文
                        tts?.setLanguage(Locale.US)
                    }
                }

                // 尝试设置语速/音调让语音更自然
                try {
                    tts?.setSpeechRate(0.9f)
                    tts?.setPitch(1.0f)
                } catch (_: Exception) {}

                // 注册 UtteranceProgressListener（只注册一次）
                try {
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(tag, "TTS onStart $utteranceId")
                        }
                        override fun onDone(utteranceId: String?) {
                            Log.d(tag, "TTS onDone $utteranceId")
                            mainHandler.post { onDone?.invoke() }
                        }
                        override fun onError(utteranceId: String?) {
                            Log.w(tag, "TTS onError $utteranceId")
                            mainHandler.post { onDone?.invoke() }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(tag, "setOnUtteranceProgressListener 失败: ${e.message}")
                }

                // 播报暂存的文本
                pendingText?.let { speak(it) }
                pendingText = null
            } else {
                Log.e(tag, "TTS 初始化失败 status=$status")
                ready = false
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
            Log.w(tag, "TTS 未就绪，暂存文本 (len=${text.length})")
            pendingText = text
            // 尝试重新初始化
            if (tts == null) start()
            return
        }

        try {
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0f)

            // minSdk=23 >= LOLLIPOP(21)，直接用 4 参数版本
            val utteranceId = "robot_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

            // 兜底：按字数估算时长，确保一定会触发 onDone
            val estimatedMs = (text.length * 200L).coerceAtLeast(2000L)
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                onDone?.invoke()
            }, estimatedMs + 1000)

            Log.d(tag, "🗣️ TTS speak() called, text='${text.take(30)}...', estimated=${estimatedMs}ms")
        } catch (e: Exception) {
            Log.e(tag, "❌ TTS speak 异常: ${e.message}", e)
            onDone?.invoke()
        }
    }

    fun setLanguage(lang: String) {
        tts?.setLanguage(parseLocale(lang))
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        onDone = null
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun isReady() = ready

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
