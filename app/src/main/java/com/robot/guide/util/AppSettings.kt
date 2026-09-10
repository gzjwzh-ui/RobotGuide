package com.robot.guide.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置管理（含后端同步 + 机器人身份配置）
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "robot_guide_settings"

        // ===== 后端同步 =====
        const val KEY_BACKEND_URL = "backend_url"           // 后端API地址
        const val KEY_SYNC_ENABLED = "sync_enabled"        // 是否自动同步
        const val KEY_SYNC_INTERVAL = "sync_interval_min"   // 同步间隔(分钟)

        // ===== 机器人身份 =====
        const val KEY_ROBOT_NAME = "robot_name"
        const val KEY_ROBOT_LANGUAGE = "robot_language"     // zh-CN / yue-HK / en-US
        const val KEY_GREETING = "greeting"                 // 开场白
        const val KEY_AUTO_WAKE_WORDS = "auto_wake_words"

        // ===== 自动唤醒 =====
        const val KEY_PERSON_DETECTION = "person_detection"
        const val KEY_IDLE_TIMEOUT = "idle_timeout"

        // ===== AI 配置 =====
        const val KEY_USE_AI = "use_ai"
        const val KEY_API_KEY = "doubao_api_key"
        const val KEY_MODEL_ID = "doubao_model_id"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_MATCH_THRESHOLD = "match_threshold"
        const val KEY_AUTO_SHOW_MEDIA = "auto_show_media"

        // 默认值
        const val DEFAULT_BACKEND_URL = "http://192.168.0.251:5000"
        const val DEFAULT_MODEL = "doubao-seed-character-260628"
        const val DEFAULT_NAME = "小胖"
        const val DEFAULT_LANGUAGE = "zh-CN"
        const val DEFAULT_GREETING = "您好！我是{robot_name}，有什么可以帮您的吗？"
        const val DEFAULT_PROMPT = "你是展厅讲解机器人，性格活泼友善，回答简洁不超过三句话，主动引导参观者了解展厅内容。"
        const val DEFAULT_THRESHOLD = 60
    }

    // ===== 后端 =====
    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trimEnd('/')).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()

    var syncIntervalMin: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, 5)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL, value.coerceIn(1, 60)).apply()

    // ===== 机器人身份 =====
    var robotName: String
        get() = prefs.getString(KEY_ROBOT_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
        set(value) = prefs.edit().putString(KEY_ROBOT_NAME, value).apply()

    var robotLanguage: String
        get() = prefs.getString(KEY_ROBOT_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_ROBOT_LANGUAGE, value).apply()

    var greeting: String
        get() = prefs.getString(KEY_GREETING, DEFAULT_GREETING) ?: DEFAULT_GREETING
        set(value) = prefs.edit().putString(KEY_GREETING, value).apply()

    var autoWakeWords: String
        get() = prefs.getString(KEY_AUTO_WAKE_WORDS, "你好,您好") ?: "你好,您好"
        set(value) = prefs.edit().putString(KEY_AUTO_WAKE_WORDS, value).apply()

    // ===== 自动唤醒 =====
    var personDetection: Boolean
        get() = prefs.getBoolean(KEY_PERSON_DETECTION, false)
        set(value) = prefs.edit().putBoolean(KEY_PERSON_DETECTION, value).apply()

    var idleTimeoutMin: Int
        get() = prefs.getInt(KEY_IDLE_TIMEOUT, 5)
        set(value) = prefs.edit().putInt(KEY_IDLE_TIMEOUT, value.coerceIn(1, 30)).apply()

    // ===== AI =====
    var useAI: Boolean
        get() = prefs.getBoolean(KEY_USE_AI, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_AI, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL_ID, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var matchThreshold: Int
        get() = prefs.getInt(KEY_MATCH_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_MATCH_THRESHOLD, value.coerceIn(30, 95)).apply()

    var autoShowMedia: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SHOW_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SHOW_MEDIA, value).apply()

    fun isAIConfigured(): Boolean {
        return apiKey.isNotBlank() && modelId.isNotBlank()
    }

    /**
     * 将后端拉取的配置批量应用
     */
    fun applyBackendConfig(cfg: Map<String, String>) {
        cfg["robot_name"]?.let { robotName = it }
        cfg["language"]?.let { robotLanguage = it }
        cfg["greeting"]?.let { greeting = it }
        cfg["auto_wake_words"]?.let { autoWakeWords = it }
        cfg["person_detection"]?.let { personDetection = it == "true" }
        cfg["idle_timeout"]?.let { idleTimeoutMin = it.toIntOrNull() ?: 5 }
        cfg["use_ai"]?.let { useAI = it == "true" }
        cfg["api_key"]?.let { if (it.isNotBlank()) apiKey = it }
        cfg["model_id"]?.let { if (it.isNotBlank()) modelId = it }
        cfg["system_prompt"]?.let { systemPrompt = it }
        cfg["match_threshold"]?.let { matchThreshold = it.toIntOrNull() ?: 60 }
    }

    /**
     * 获取实际生效的问候语（替换占位符）
     */
    fun resolveGreeting(): String {
        return greeting.replace("{robot_name}", robotName)
    }

    /**
     * 语言代码 -> Locale
     */
    fun getLocale(): java.util.Locale {
        return when (robotLanguage) {
            "yue-HK" -> java.util.Locale("zh", "HK")
            "en-US" -> java.util.Locale.US
            else -> java.util.Locale.CHINESE
        }
    }

    /**
     * TTS语言字符串
     */
    fun getSpeechLang(): String {
        return when (robotLanguage) {
            "yue-HK" -> "yue-HK"
            "en-US" -> "en-US"
            else -> "zh-CN"
        }
    }
}
