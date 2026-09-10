package com.robot.guide.data

/**
 * 固定问答数据模型
 */
data class QAItem(
    val id: Long = 0,
    val question: String,
    val answer: String,
    val keywords: List<String> = emptyList(),
    val mediaRefs: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 问答匹配结果
 */
data class MatchResult(
    val qaItem: QAItem,
    val score: Int,       // 0-100 匹配分数
    val matchedKeyword: String?
)

/**
 * 聊天消息模型
 */
data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val content: String,
    val source: Source = Source.LOCAL,
    val mediaRefs: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, BOT }
    enum class Source { LOCAL, AI, FALLBACK }
}

/**
 * 媒体文件模型
 */
data class MediaFile(
    val id: String,          // 唯一标识
    val path: String,        // 文件路径或URL
    val name: String,
    val type: Type,
    val thumbnailPath: String? = null
) {
    enum class Type { IMAGE, VIDEO }
}

/**
 * 车辆展示数据模型
 */
data class Vehicle(
    val id: Long = 0,
    val name: String,
    val brand: String = "",
    val year: String = "",
    val category: String = "",
    val description: String = "",
    val descriptionEn: String = "",
    val descriptionYue: String = "",
    val thumbnail: String? = null,   // 缩略图URL（完整URL或相对路径）
    val imageCount: Int = 0,
    val sortOrder: Int = 0,
    val images: List<VehicleImage> = emptyList()
)

data class VehicleImage(
    val id: Long = 0,
    val url: String,           // 图片URL
    val caption: String = "",  // 图片说明
    val sortOrder: Int = 0
)

/**
 * 机器动作信息（从后端 /api/robot/actions 返回）
 */
data class RobotActionInfo(
    val code: String,          // 动作码，如 "base_forward_1m"
    val group: String,         // 分组：底座移动 / 头部 / 灯光 / 组合动作
    val name: String,          // 中文名，如 "前进1米"
    val durationMs: Int,       // 预估执行时长
    val hardware: String       // 目标硬件：base / head / led / combo
)

/**
 * 机器硬件状态（从后端 /api/robot/status 返回）
 */
data class RobotStatus(
    val head: HeadState = HeadState(),
    val base: BaseState = BaseState(),
    val led: LedState = LedState(),
    val sensors: SensorState = SensorState(),
    val lastAction: String? = null,
    val lastActionAt: Long = 0,
    val hardwareConnected: Boolean = false
) {
    data class HeadState(val angle: Int = 0, val status: String = "idle")
    data class BaseState(val moving: Boolean = false, val direction: String = "idle")
    data class LedState(val ear: Boolean = true, val eye: Boolean = true)
    data class SensorState(
        val infrared: Infrared = Infrared(),
        val ultrasonic: Ultrasonic = Ultrasonic(),
        val laser: String = "ok",
        val humanDetected: Boolean = false,
        val position: Position = Position()
    ) {
        data class Infrared(val right: Int = 0, val left: Int = 0, val fcc: Int = 0, val top: Int = 0)
        data class Ultrasonic(
            val rear: Int = 255, val front: Int = 255,
            val leftCenter: Int = 255, val midLeft: Int = 255, val midCenter: Int = 255,
            val rightCenter: Int = 255, val rightSide: Int = 255
        )
        data class Position(val x: Int = 0, val y: Int = 0, val theta: Int = 0)
    }
}
