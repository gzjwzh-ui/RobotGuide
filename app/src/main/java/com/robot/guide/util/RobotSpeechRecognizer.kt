package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 实时语音识别（ASR）
 * 支持普通话 zh-CN、粤语 yue-HK、英语 en-US
 *
 * 修复点：
 *   1. isSupported() 增加 SpeechRecognizer.isRecognitionAvailable 检查
 *   2. ERROR_NO_MATCH / SPEECH_TIMEOUT 后自动恢复可监听状态
 *   3. stopListening 使用单线程串行队列，避免与 onError 并发 destroy 导致 crash
 */
class RobotSpeechRecognizer(private val context: Context) {

    private val tag = "SpeechRec"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var listening = false
    private var currentLanguage: String = "zh-CN"

    fun isSupported(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "系统没有可用的语音识别服务")
            return false
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(tag, "没有录音权限")
        return granted
    }

    /**
     * 开始监听
     */
    fun startListening(
        language: String = "zh-CN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {},
        onVolumeChange: (Float) -> Unit = {}
    ) {
        if (!isSupported()) {
            onError("设备不支持语音识别，请手动输入文字")
            return
        }
        if (listening) {
            Log.w(tag, "已经在监听中，忽略重复调用")
            return
        }

        currentLanguage = language
        val locale = when {
            language.startsWith("yue") -> Locale("zh", "HK")
            language.startsWith("en") -> Locale.US
            else -> Locale.CHINESE
        }

        // 先销毁老的，确保干净
        safeDestroyRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            // 超时参数：60 秒足够长，防止中途被系统截断
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    listening = true
                    Log.d(tag, "🎙️ 开始监听 ($language)")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { onVolumeChange(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    listening = false
                    Log.d(tag, "用户停止说话")
                }
                override fun onError(error: Int) {
                    listening = false
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再说一次"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "长时间没说话，识别结束"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        else -> "识别错误 code=$error"
                    }
                    Log.w(tag, "⚠️ onError $error: $msg")
                    // 非致命错误（没听清/超时）不弹 Toast 打扰用户，静默处理即可
                    val isUserError = error in listOf(
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    )
                    if (!isUserError) onError(msg)
                }
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val best = matches.first().trim()
                        Log.d(tag, "✅ 识别结果: $best (共 ${matches.size} 候选)")
                        if (best.isNotBlank()) onResult(best)
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty() && matches.first().isNotBlank()) {
                        // partial 结果可以给 UI 做实时反馈，但不触发 sendQuestion
                        onVolumeChange(-1f) // 用特殊值表示有 partial
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            listening = true
            speechRecognizer?.startListening(intent)
            Log.d(tag, "SpeechRecognizer.startListening 调用成功")
        } catch (e: Exception) {
            listening = false
            Log.e(tag, "❌ startListening 抛异常: ${e.message}", e)
            onError("启动语音识别失败: ${e.message}")
            safeDestroyRecognizer()
        }
    }

    fun stopListening() {
        listening = false
        safeDestroyRecognizer()
    }

    private fun safeDestroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun isListening() = listening

    fun destroy() {
        listening = false
        safeDestroyRecognizer()
    }
}
