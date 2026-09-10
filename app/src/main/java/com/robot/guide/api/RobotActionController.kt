package com.robot.guide.api

import android.content.Context
import android.util.Log
import com.robot.guide.util.AppSettings
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 机器动作控制器
 * 通过后端 API 发送动作指令给实体机器人
 *
 * 支持动作：
 * - HANDSHAKE   握手
 * - WAVE        挥手
 * - GREET       打招呼
 * - NOD_HEAD    点头
 * - SHAKE_HEAD  摇头
 * - WINK        眨眼
 */
class RobotActionController(context: Context) {

    private val tag = "RobotAction"
    private val settings = AppSettings(context)

    enum class Action(val code: String, val display: String) {
        HANDSHAKE("handshake", "握手 ✋"),
        WAVE("wave", "挥手 👋"),
        GREET("greet", "打招呼 🎉"),
        NOD_HEAD("nod", "点头 ✅"),
        SHAKE_HEAD("shake", "摇头 ❌"),
        WINK("wink", "眨眼 😉"),
        ARROW_RIGHT("arrow_right", "头部右转 →"),
        ARROW_LEFT("arrow_left", "头部左转 ←")
    }

    /**
     * 发送动作指令
     * @param action 要执行的动作
     * @param onResult 结果回调 (success, message)
     */
    fun execute(action: Action, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            // 无后端，本地模拟动作
            Log.d(tag, "后端未配置，本地模拟动作: ${action.display}")
            onResult(true, "模拟动作: ${action.display}")
            return
        }

        Thread {
            try {
                val url = URL("$baseUrl/api/robot/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("action", action.code)
                    put("duration", 3)
                    put("timestamp", System.currentTimeMillis())
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                val reader = BufferedReader(
                    InputStreamReader(if (code == 200) conn.inputStream else conn.errorStream)
                )
                val body = reader.readText()
                reader.close()

                if (code == 200) {
                    onResult(true, "${action.display} 执行成功")
                } else {
                    onResult(false, "动作执行失败: $code")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "动作指令发送失败（可能无后端）: ${e.message}")
                onResult(false, "无法连接后端，动作未执行")
            }
        }.start()
    }

    /**
     * 从 AI 回答中自动触发匹配的动作
     */
    fun autoTriggerFromText(text: String) {
        when {
            text.contains("挥手") || text.contains("欢迎") || text.contains("大家好") -> {
                execute(Action.WAVE)
                execute(Action.GREET)
            }
            text.contains("握手") || text.contains("请") || text.contains("这边请") -> {
                execute(Action.HANDSHAKE)
            }
            text.contains("点头") || text.contains("好的") || text.contains("是的") -> {
                execute(Action.NOD_HEAD)
            }
            text.contains("摇头") || text.contains("不是") || text.contains("不行") -> {
                execute(Action.SHAKE_HEAD)
            }
            text.contains("右转") || text.contains("向右") -> {
                execute(Action.ARROW_RIGHT)
            }
            text.contains("左转") || text.contains("向左") -> {
                execute(Action.ARROW_LEFT)
            }
        }
    }
}
