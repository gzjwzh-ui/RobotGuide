package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 实时语音识别（ASR）
 * 支持普通话、粤语、英语
 */
class RobotSpeechRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false

    fun isSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
               PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开始监听
     * @param language 语言代码 zh-CN/yue-HK/en-US
     * @param onResult 识别结果回调
     * @param onError 错误回调
     * @param onVolumeChange 音量变化回调（用于动画）
     */
    fun startListening(
        language: String = "zh-CN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {},
        onVolumeChange: (Float) -> Unit = {}
    ) {
        if (!isSupported()) {
            onError("设备不支持语音识别")
            return
        }
        if (listening) return

        val locale = when {
            language.startsWith("yue") -> Locale("zh", "HK")
            language.startsWith("en") -> Locale.US
            else -> Locale.CHINESE
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { onVolumeChange(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没检测到语音"
                    else -> "识别错误 $error"
                }
                onError(msg)
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0]) // 实时显示部分结果
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        listening = false
    }

    fun isListening() = listening

    fun destroy() {
        stopListening()
    }
}
