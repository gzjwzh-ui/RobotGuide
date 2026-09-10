package com.robot.guide.api

import android.content.Context
import android.util.Log
import com.robot.guide.data.RobotActionInfo
import com.robot.guide.data.RobotStatus
import com.robot.guide.util.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 机器动作控制器 —— 对齐实体机器控制面板
 *
 * 真实指令分组：
 *   底座移动：左转90°/右转90°/前进1米/后退1米/底座停止
 *   头部：    头部左/头部右/头部上/头部下/头部手臂复位/头部复位
 *   灯光：    耳朵灯开/耳朵灯关/眼睛灯开/眼睛灯关
 *   组合动作：活动筋骨
 *
 * 另外两个辅助接口：
 *   GET /api/robot/actions —— 列出所有支持的动作（可用于动态生成按钮）
 *   GET /api/robot/status  —— 实时硬件状态（传感器 / 灯光 / 底座 / 头部）
 */
class RobotActionController(context: Context) {

    private val tag = "RobotAction"
    private val settings = AppSettings(context)

    /**
     * 本地枚举（离线/无后端时使用），code 与后端 ROBOT_ACTIONS 保持一致。
     * group / hardware / durationMs 与后端同步，可直接用于展示。
     */
    enum class Action(val code: String, val display: String, val group: String, val hardware: String, val durationMs: Int) {
        // 底座移动
        BASE_TURN_LEFT_90 ("base_turn_left_90",  "左转 90°   ↺", "底座移动", "base", 2500),
        BASE_TURN_RIGHT_90("base_turn_right_90", "右转 90°   ↻", "底座移动", "base", 2500),
        BASE_FORWARD_1M   ("base_forward_1m",    "前进 1 米  ⬆", "底座移动", "base", 3500),
        BASE_BACKWARD_1M  ("base_backward_1m",   "后退 1 米  ⬇", "底座移动", "base", 3500),
        BASE_STOP         ("base_stop",          "底座停止   ⏹", "底座移动", "base",  500),
        // 头部
        HEAD_LEFT         ("head_left",          "头部左     ←", "头部",    "head",  800),
        HEAD_RIGHT        ("head_right",         "头部右     →", "头部",    "head",  800),
        HEAD_UP           ("head_up",            "头部上     ↑", "头部",    "head",  800),
        HEAD_DOWN         ("head_down",          "头部下     ↓", "头部",    "head",  800),
        HEAD_RESET_ALL    ("head_reset_all",     "头部手臂复位",  "头部",    "head", 1200),
        HEAD_RESET        ("head_reset",         "头部复位",      "头部",    "head", 1000),
        // 灯光
        EAR_LED_ON        ("ear_led_on",         "耳朵灯   开",   "灯光",    "led",   300),
        EAR_LED_OFF       ("ear_led_off",        "耳朵灯   关",   "灯光",    "led",   300),
        EYE_LED_ON        ("eye_led_on",         "眼睛灯   开",   "灯光",    "led",   300),
        EYE_LED_OFF       ("eye_led_off",        "眼睛灯   关",   "灯光",    "led",   300),
        // 组合动作
        COMBO_STRETCH     ("combo_stretch",      "活动筋骨 🧘",  "组合动作", "combo", 5000);

        companion object {
            /** 按 group 分组的 Action 列表 */
            fun byGroup(): Map<String, List<Action>> =
                values().groupBy { it.group }
        }
    }

    // ========= 核心：执行动作 =========

    /**
     * 发送动作指令到后端 `/api/robot/action`。
     * 无后端时本地模拟（onResult 仍回调 true）。
     */
    fun execute(action: Action, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            Log.d(tag, "后端未配置，本地模拟动作: ${action.display}")
            onResult(true, "模拟: ${action.display}")
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
                    put("action",     action.code)
                    put("duration",   action.durationMs)
                    put("group",      action.group)
                    put("hardware",   action.hardware)
                    put("timestamp",  System.currentTimeMillis())
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val code = conn.responseCode
                val body = BufferedReader(InputStreamReader(
                    if (code == 200) conn.inputStream else conn.errorStream
                )).readText()

                if (code == 200) {
                    val ok = JSONObject(body).optBoolean("ok", true)
                    val hw = JSONObject(body).optBoolean("hardware_connected", false)
                    val suffix = if (hw) "硬件已接收" else "本地模拟"
                    onResult(ok, "${action.display} · $suffix")
                } else {
                    onResult(false, "动作失败 HTTP $code: $body")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "动作指令发送失败（可能无后端）: ${e.message}")
                onResult(false, "无法连接后端，动作未执行")
            }
        }.start()
    }

    // ========= 查询：实时硬件状态 =========

    /**
     * 轮询后端 `/api/robot/status`。
     * 无后端时返回 null。
     */
    fun fetchStatus(onResult: (RobotStatus?) -> Unit) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) { onResult(null); return }
        Thread {
            try {
                val url = URL("$baseUrl/api/robot/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 2000
                conn.readTimeout    = 2000
                val code = conn.responseCode
                val body = BufferedReader(InputStreamReader(
                    if (code == 200) conn.inputStream else conn.errorStream
                )).readText()
                conn.disconnect()
                if (code != 200) { onResult(null); return }
                onResult(parseStatus(body))
            } catch (e: Exception) {
                Log.w(tag, "fetchStatus 异常: ${e.message}")
                onResult(null)
            }
        }.start()
    }

    private fun parseStatus(body: String): RobotStatus {
        val j = JSONObject(body)
        val head = j.optJSONObject("head") ?: JSONObject()
        val base = j.optJSONObject("base") ?: JSONObject()
        val led  = j.optJSONObject("led")  ?: JSONObject()
        val sens = j.optJSONObject("sensors") ?: JSONObject()
        val ir   = sens.optJSONObject("infrared")   ?: JSONObject()
        val us   = sens.optJSONObject("ultrasonic") ?: JSONObject()
        val pos  = sens.optJSONObject("position")   ?: JSONObject()
        return RobotStatus(
            head = RobotStatus.HeadState(
                angle = head.optInt("angle"),
                status = head.optString("status", "idle")),
            base = RobotStatus.BaseState(
                moving = base.optBoolean("moving"),
                direction = base.optString("direction", "idle")),
            led = RobotStatus.LedState(
                ear = led.optBoolean("ear", true),
                eye = led.optBoolean("eye", true)),
            sensors = RobotStatus.SensorState(
                infrared = RobotStatus.SensorState.Infrared(
                    ir.optInt("right"), ir.optInt("left"),
                    ir.optInt("fcc"),   ir.optInt("top")),
                ultrasonic = RobotStatus.SensorState.Ultrasonic(
                    us.optInt("rear"),   us.optInt("front"),
                    us.optInt("left_center"), us.optInt("mid_left"), us.optInt("mid_center"),
                    us.optInt("right_center"), us.optInt("right_side")),
                laser = sens.optString("laser", "ok"),
                humanDetected = sens.optBoolean("human_detected"),
                position = RobotStatus.SensorState.Position(
                    pos.optInt("x"), pos.optInt("y"), pos.optInt("theta"))),
            lastAction = j.optString("last_action").ifBlank { null },
            lastActionAt = j.optLong("last_action_at"),
            hardwareConnected = j.optBoolean("hardware_connected"))
    }

    // ========= 查询：支持的动作列表 =========

    fun listActions(onResult: (List<RobotActionInfo>?) -> Unit) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) { onResult(Action.values().map { it.toInfo() }); return }
        Thread {
            try {
                val url = URL("$baseUrl/api/robot/actions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 2000
                conn.readTimeout    = 2000
                val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()
                val arr = JSONObject(body).optJSONArray("actions") ?: JSONArray()
                val list = (0 until arr.length()).map { i ->
                    val a = arr.getJSONObject(i)
                    RobotActionInfo(
                        code = a.optString("code"),
                        group = a.optString("group"),
                        name = a.optString("name"),
                        durationMs = a.optInt("duration_ms"),
                        hardware = a.optString("hardware"))
                }
                onResult(list)
            } catch (_: Exception) {
                onResult(Action.values().map { it.toInfo() })
            }
        }.start()
    }

    private fun Action.toInfo() = RobotActionInfo(
        code = code, group = group, name = display,
        durationMs = durationMs, hardware = hardware)

    // ========= 文本 → 动作自动匹配 =========

    /**
     * 从任意文本中提取硬件控制关键词并触发真实机器动作。
     * 同时匹配：
     *   - 用户提问（"让机器人右转" / "眼睛灯开一下"）
     *   - AI 回答（"我可以帮你前进到展厅入口"）
     *
     * 匹配表按 hardware 分组，越具体的规则放越前面。
     */
    fun autoTriggerFromText(text: String) {
        val t = text
        when {
            // ===== 灯光类（最精准，放前面）=====
            // 耳朵灯
            t.contains("耳朵灯") && (t.contains("关") || t.contains("灭") || t.contains("暗")) ->
                execute(Action.EAR_LED_OFF)
            t.contains("耳朵灯") && (t.contains("开") || t.contains("亮") || t.contains("打")) ->
                execute(Action.EAR_LED_ON)
            t.contains("耳朵灯") -> { /* 只说了耳朵灯没说开/关，不触发 */ }
            // 眼睛灯
            t.contains("眼睛灯") && (t.contains("关") || t.contains("灭") || t.contains("暗")) ->
                execute(Action.EYE_LED_OFF)
            t.contains("眼睛灯") && (t.contains("开") || t.contains("亮") || t.contains("打")) ->
                execute(Action.EYE_LED_ON)

            // ===== 组合动作 =====
            t.contains("活动筋骨") || t.contains("伸展") ->
                execute(Action.COMBO_STRETCH)
            t.contains("打招呼") || t.contains("欢迎你") || t.contains("大家好") -> {
                execute(Action.COMBO_STRETCH)
                execute(Action.HEAD_RESET_ALL)
            }

            // ===== 头部 =====
            t.contains("头部复位") || t.contains("头复位") -> execute(Action.HEAD_RESET)
            t.contains("手臂复位") || t.contains("全部复位") || t.contains("头手复位") ->
                execute(Action.HEAD_RESET_ALL)
            // 头部方向 —— 匹配"看左/看右/向左转/向右转/抬头/低头/向上看/向下看"
            (t.contains("看左") || t.contains("向左") || t.contains("头向左")) ->
                execute(Action.HEAD_LEFT)
            (t.contains("看右") || t.contains("向右") || t.contains("头向右")) ->
                execute(Action.HEAD_RIGHT)
            (t.contains("抬头") || t.contains("向上") || t.contains("头向上")) ->
                execute(Action.HEAD_UP)
            (t.contains("低头") || t.contains("向下") || t.contains("头向下")) ->
                execute(Action.HEAD_DOWN)

            // ===== 底座移动（方向词容易歧义，放后面，避免误匹配头部方向）=====
            // 停止 —— 明确且安全
            (t.contains("停止") || t.contains("停下") || t.contains("别动") ||
                t.contains("底座停止")) -> execute(Action.BASE_STOP)
            // 前进
            (t.contains("前进") || t.contains("往前") || t.contains("向前") ||
                t.contains("往前走") || t.contains("往前开")) -> execute(Action.BASE_FORWARD_1M)
            // 后退
            (t.contains("后退") || t.contains("退后") || t.contains("向后") ||
                t.contains("往回走") || t.contains("倒")) -> execute(Action.BASE_BACKWARD_1M)
            // 左转 —— 必须是"底座左转"/"向左转"/"左前方"，排除"头部左转"（头部已在上面匹配过）
            (t.contains("向左转") || t.contains("左转") || t.contains("左前方")) &&
                !t.contains("头") -> execute(Action.BASE_TURN_LEFT_90)
            // 右转
            (t.contains("向右转") || t.contains("右转")) &&
                !t.contains("头") -> execute(Action.BASE_TURN_RIGHT_90)
        }
    }
}
