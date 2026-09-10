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
