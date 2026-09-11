package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 实时语音识别（ASR）—— 参考原 APK SpeechHandler 的 Handler 消息机制
 *
 * 关键改进：
 *   1. 连续对话模式：ERROR_NO_MATCH / SPEECH_TIMEOUT 后自动重启监听
 *   2. Handler 串行化：所有状态变更通过 mainHandler.post 执行，避免并发 crash
 *   3. 与 TTS 配合：说话时暂停监听，说完后自动恢复
 *   4. 多次重试保护：连续错误超过 3 次后延迟重启，避免无限循环
 *   5. 状态机：IDLE → LISTENING → PROCESSING → SPEAKING → LISTENING
 */
class RobotSpeechRecognizer(private val context: Context) {

    private val tag = "SpeechRec"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile private var listening = false
    @Volatile private var pausedForSpeech = false

    private var currentLanguage: String = "zh-CN"
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3

    // 回调
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onVolumeChange: ((Float) -> Unit)? = null

    companion object {
        const val MSG_START = 0x1
        const val MSG_STOP = 0x2
        const val MSG_RESTART = 0x3
        const val MSG_ERROR_RECOVER = 0x4
        const val MSG_UNPAUSE = 0x5
    }

    private val messageHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_START -> doStartListening(currentLanguage)
            MSG_STOP -> doStopListening()
            MSG_RESTART -> {
                Log.d(tag, "🔄 自动重启监听 (连续错误 $consecutiveErrors)")
                doStopListening()
                doStartListening(currentLanguage)
            }
            MSG_ERROR_RECOVER -> {
                val delay = if (consecutiveErrors >= maxConsecutiveErrors) 3000L else 800L
                Log.d(tag, "⏱️ 错误恢复延迟 ${delay}ms (连续错误 $consecutiveErrors)")
                consecutiveErrors = 0
                messageHandler.removeMessages(MSG_RESTART)
                messageHandler.sendEmptyMessageDelayed(MSG_RESTART, delay)
            }
            MSG_UNPAUSE -> {
                pausedForSpeech = false
                if (listening) return@Handler true
                Log.d(tag, "🔓 TTS 说完，恢复监听")
                doStartListening(currentLanguage)
            }
        }
        true
    }

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
     * 开始监听（非阻塞，通过 Handler 排队）
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
        this.onResult = onResult
        this.onError = onError
        this.onVolumeChange = onVolumeChange
        this.currentLanguage = language
        this.pausedForSpeech = false

        // 通过 Handler 串行化，避免并发
        messageHandler.removeMessages(MSG_START)
        messageHandler.sendEmptyMessage(MSG_START)
    }

    fun stopListening() {
        messageHandler.removeMessages(MSG_RESTART)
        messageHandler.removeMessages(MSG_ERROR_RECOVER)
        messageHandler.sendEmptyMessage(MSG_STOP)
    }

    /**
     * TTS 开始说话时暂停监听
     */
    fun pauseForSpeech() {
        if (pausedForSpeech) return
        pausedForSpeech = true
        listening = false
        safeDestroyRecognizer()
        Log.d(tag, "🔒 暂停监听（TTS 说话中）")
    }

    /**
     * TTS 说完后恢复监听
     */
    fun resumeAfterSpeech() {
        messageHandler.removeMessages(MSG_UNPAUSE)
        messageHandler.sendEmptyMessageDelayed(MSG_UNPAUSE, 300L)
    }

    fun isListening() = listening

    fun destroy() {
        messageHandler.removeCallbacksAndMessages(null)
        listening = false
        pausedForSpeech = false
        safeDestroyRecognizer()
    }

    // ========== 内部实现 ==========

    private fun doStartListening(language: String) {
        if (listening) {
            Log.d(tag, "已经在监听中，跳过")
            return
        }
        if (pausedForSpeech) {
            Log.d(tag, "暂停状态，跳过启动")
            return
        }

        val locale = when {
            language.startsWith("yue") -> Locale("zh", "HK")
            language.startsWith("en") -> Locale.US
            else -> Locale.CHINESE
        }

        safeDestroyRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            listening = true
            speechRecognizer?.startListening(intent)
            Log.d(tag, "🎙️ startListening ($language)")
        } catch (e: Exception) {
            listening = false
            Log.e(tag, "❌ startListening 异常: ${e.message}", e)
            onError?.invoke("启动语音识别失败: ${e.message}")
            scheduleErrorRecover()
        }
    }

    private fun doStopListening() {
        listening = false
        safeDestroyRecognizer()
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                listening = true
                consecutiveErrors = 0
                Log.d(tag, "✅ onReadyForSpeech")
            }
            override fun onBeginningOfSpeech() {
                Log.d(tag, "💬 onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {
                onVolumeChange?.invoke(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listening = false
                Log.d(tag, "🔇 onEndOfSpeech")
            }
            override fun onError(error: Int) {
                listening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再说一次"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "长时间没说话"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    else -> "识别错误 code=$error"
                }
                Log.w(tag, "⚠️ onError $error: $msg")

                // 分类处理
                when (error) {
                    // 可自动恢复的错误
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // 不打扰用户，静默恢复监听（连续对话模式）
                        scheduleErrorRecover()
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        onError?.invoke(msg)
                    }
                    // 其他错误，给用户提示后尝试恢复
                    else -> {
                        consecutiveErrors++
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            onError?.invoke("语音识别暂时不可用，稍后再试")
                        }
                        scheduleErrorRecover()
                    }
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                consecutiveErrors = 0
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val best = matches.first().trim()
                    Log.d(tag, "✅ 识别结果: $best (共 ${matches.size} 候选)")
                    if (best.isNotBlank()) onResult?.invoke(best)
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches.first().isNotBlank()) {
                    onVolumeChange?.invoke(-1f) // 特殊值表示 partial 有内容
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
    }

    private fun scheduleErrorRecover() {
        if (pausedForSpeech) return
        messageHandler.removeMessages(MSG_RESTART)
        messageHandler.removeMessages(MSG_ERROR_RECOVER)
        messageHandler.sendEmptyMessage(MSG_ERROR_RECOVER)
    }

    private fun safeDestroyRecognizer() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }
}
