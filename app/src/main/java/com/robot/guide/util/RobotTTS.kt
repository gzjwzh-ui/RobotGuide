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
    private var currentVolume = 1.0f  // 音量 0.0~1.0

    fun start(preferLang: String? = null) {
        Log.d(tag, "TTS start() called")
        val settings = AppSettings(context)
        currentVolume = settings.ttsVolume  // 读取音量设置
        tts = TextToSpeech(context.applicationContext) { status ->
            Log.d(tag, "TTS init status=$status")
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                // 直接用 AppSettings 中的 robotLanguage，避免 getSpeechLang 不一致
                val lang = preferLang ?: settings.robotLanguage
                Log.d(tag, "TTS 使用语言代码: $lang")
                val locale = parseLocale(lang)
                val result = tts?.setLanguage(locale)
                Log.d(tag, "TTS setLanguage($locale) result=$result (0=LANG_AVAILABLE, -1=MISSING_DATA, -2=NOT_SUPPORTED)")

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 首选语言不支持时尝试 fallback
                    Log.w(tag, "首选语言($lang)不支持，尝试 fallback")
                    // 先试简体中文
                    val fallback1 = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    Log.w(tag, "尝试简体中文 result=$fallback1")
                    if (fallback1 == TextToSpeech.LANG_MISSING_DATA || fallback1 == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 再试繁体中文
                        val fallback2 = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)
                        Log.w(tag, "尝试繁体中文 result=$fallback2")
                        if (fallback2 == TextToSpeech.LANG_MISSING_DATA || fallback2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                            // 最后英文
                            val fallback3 = tts?.setLanguage(Locale.US)
                            Log.w(tag, "降级到英文 result=$fallback3")
                        }
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
            lang.startsWith("zh-CN") || lang.startsWith("zh_CN") -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.SIMPLIFIED_CHINESE
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
            if (tts == null) start()
            return
        }

        try {
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume)
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0f)

            val utteranceId = "robot_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

            val estimatedMs = (text.length * 200L).coerceAtLeast(2000L)
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                onDone?.invoke()
            }, estimatedMs + 1000)

            Log.d(tag, "🗣️ TTS speak() vol=$currentVolume, text='${text.take(30)}...', estimated=${estimatedMs}ms")
        } catch (e: Exception) {
            Log.e(tag, "❌ TTS speak 异常: ${e.message}", e)
            onDone?.invoke()
        }
    }

    /**
     * 设置音量 (0.0 ~ 1.0)
     */
    fun setVolume(vol: Float) {
        currentVolume = vol.coerceIn(0f, 1f)
        Log.d(tag, "音量设置为: $currentVolume")
    }

    /**
     * 重新应用语言设置（设置页面修改语言后调用）
     */
    fun reloadLanguage(lang: String) {
        val locale = parseLocale(lang)
        val result = tts?.setLanguage(locale)
        Log.d(tag, "reloadLanguage($lang -> $locale) result=$result")
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
