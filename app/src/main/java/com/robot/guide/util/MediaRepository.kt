package com.robot.guide.util

import android.content.Context
import android.util.Log
import com.robot.guide.data.MediaFile
import com.robot.guide.data.Vehicle
import com.robot.guide.data.VehicleImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 媒体资源仓库
 *
 * 修复点：
 *   1. thumbnail/stored_path 是相对路径（/uploads/xxx.jpg），客户端自动拼 baseUrl
 *   2. mock 图片换成 picsum.photos（稳定 CDN，国内可访）
 *   3. mock 视频换成 Google Sample（已知可用的公开 CDN）
 */
class MediaRepository(context: Context) {

    private val tag = "MediaRepo"
    private val settings = AppSettings(context)

    private fun baseUrl(): String = settings.backendUrl.trimEnd('/')

    /** 把后端返回的相对路径（如 /uploads/xxx.jpg）补成完整 URL */
    private fun absolutePath(path: String): String {
        if (path.isBlank()) return ""
        // 已经是完整 URL 的直接返回
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = baseUrl()
        return if (base.isBlank()) path else "$base$path"
    }

    /** 拼图片 URL —— mock 时 picsum 的参数化图片 */
    private fun mockImage(seed: Int, w: Int = 800, h: Int = 600): String =
        "https://picsum.photos/seed/robot$seed/$w/$h"

    fun loadVehicles(onResult: (List<Vehicle>) -> Unit) {
        val base = baseUrl()
        if (base.isBlank()) {
            onResult(getMockVehicles())
            return
        }

        Thread {
            try {
                val conn = (URL("$base/api/vehicles") as HttpURLConnection).apply {
                    connectTimeout = 3000; readTimeout = 5000
                }
                if (conn.responseCode != 200) {
                    onResult(getMockVehicles()); return@Thread
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()
                val arr = JSONArray(body)
                val vehicles = (0 until arr.length()).map { i -> parseVehicle(arr.getJSONObject(i)) }
                onResult(vehicles)
            } catch (e: Exception) {
                Log.w(tag, "加载车辆列表失败: ${e.message}，使用模拟数据")
                onResult(getMockVehicles())
            }
        }.start()
    }

    private fun parseVehicle(obj: JSONObject): Vehicle {
        val imagesArr = obj.optJSONArray("images") ?: JSONArray()
        val images = mutableListOf<VehicleImage>()
        for (i in 0 until imagesArr.length()) {
            val io = imagesArr.getJSONObject(i)
            // stored_path 是后端字段（/uploads/xxx.jpg），客户端要拼 baseUrl
            val rawPath = io.optString("stored_path")
            val url = if (rawPath.isNotBlank()) absolutePath(rawPath) else io.optString("url")
            images.add(VehicleImage(
                url = url,
                caption = io.optString("caption", ""),
                sortOrder = io.optInt("sort_order")
            ))
        }
        val rawThumb = obj.optString("thumbnail")
        val thumbnail = if (rawThumb.isNotBlank()) absolutePath(rawThumb) else null
        return Vehicle(
            id = obj.optLong("id"),
            name = obj.optString("name"),
            brand = obj.optString("brand"),
            year = obj.optString("year"),
            category = obj.optString("category"),
            description = obj.optString("description"),
            descriptionEn = obj.optString("description_en"),
            descriptionYue = obj.optString("description_yue"),
            thumbnail = thumbnail,
            imageCount = obj.optInt("image_count", images.size),
            images = images
        )
    }

    /**
     * 模拟车辆数据 —— 5 辆，picsum.photos 稳定 CDN，展厅主题
     * seed 带 robot 前缀避免 picsum 返回随机不相干图
     */
    private fun getMockVehicles(): List<Vehicle> = listOf(
        Vehicle(
            id = 1, name = "高尔夫 GTI", brand = "大众", year = "2024", category = "轿车",
            description = "经典两厢性能车，搭载 2.0T EA888 引擎，7 秒破百。",
            thumbnail = mockImage(101, 600, 400),
            images = listOf(
                VehicleImage(mockImage(101, 1200, 800), "前脸"),
                VehicleImage(mockImage(102, 1200, 800), "侧面线条"),
                VehicleImage(mockImage(103, 1200, 800), "内饰驾驶舱"),
                VehicleImage(mockImage(104, 1200, 800), "尾翼")
            )
        ),
        Vehicle(
            id = 2, name = "速腾 L", brand = "大众", year = "2024", category = "轿车",
            description = "加长轴距 2791mm，后排腿部空间越级，家用舒适首选。",
            thumbnail = mockImage(201, 600, 400),
            images = listOf(
                VehicleImage(mockImage(201, 1200, 800), "整车外观"),
                VehicleImage(mockImage(202, 1200, 800), "后排空间"),
                VehicleImage(mockImage(203, 1200, 800), "中控大屏")
            )
        ),
        Vehicle(
            id = 3, name = "迈腾 380", brand = "大众", year = "2024", category = "轿车",
            description = "商务中型轿车，EA390 2.0T 高功，动力充沛稳重大气。",
            thumbnail = mockImage(301, 600, 400),
            images = listOf(
                VehicleImage(mockImage(301, 1200, 800), "外观"),
                VehicleImage(mockImage(302, 1200, 800), "后排老板位"),
                VehicleImage(mockImage(303, 1200, 800), "后备箱")
            )
        ),
        Vehicle(
            id = 4, name = "途观 L", brand = "大众", year = "2024", category = "SUV",
            description = "中型 SUV 标杆，四驱系统，7 座可选，适合家庭出游。",
            thumbnail = mockImage(401, 600, 400),
            images = listOf(
                VehicleImage(mockImage(401, 1200, 800), "外观前脸"),
                VehicleImage(mockImage(402, 1200, 800), "内饰全景"),
                VehicleImage(mockImage(403, 1200, 800), "第三排折叠")
            )
        ),
        Vehicle(
            id = 5, name = "ID.4 纯电", brand = "大众", year = "2024", category = "新能源",
            description = "MEB 纯电平台，续航 600km+，L2 自动驾驶，AR HUD 抬头显示。",
            thumbnail = mockImage(501, 600, 400),
            images = listOf(
                VehicleImage(mockImage(501, 1200, 800), "外观流线"),
                VehicleImage(mockImage(502, 1200, 800), "内饰 AR 抬头显示"),
                VehicleImage(mockImage(503, 1200, 800), "电池底盘")
            )
        )
    )

    fun loadVideos(onResult: (List<MediaFile>) -> Unit) {
        val base = baseUrl()
        if (base.isBlank()) {
            onResult(getMockVideos()); return
        }
        // 后端可能没有 /api/videos，兜底 mock
        Thread {
            try {
                val conn = (URL("$base/api/videos") as HttpURLConnection).apply {
                    connectTimeout = 2000; readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    conn.disconnect()
                    onResult(parseVideos(body))
                } else {
                    onResult(getMockVideos())
                }
            } catch (_: Exception) {
                onResult(getMockVideos())
            }
        }.start()
    }

    private fun parseVideos(body: String): List<MediaFile> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MediaFile(
                id = obj.optString("id"),
                path = absolutePath(obj.optString("url")),
                name = obj.optString("name"),
                type = MediaFile.Type.VIDEO
            )
        }
    }

    /**
     * Mock 视频 —— Google 官方公开样例 CDN，稳定可访问
     */
    private fun getMockVideos(): List<MediaFile> = listOf(
        MediaFile(
            id = "v1", type = MediaFile.Type.VIDEO,
            name = "展厅全景介绍（45秒）",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        MediaFile(
            id = "v2", type = MediaFile.Type.VIDEO,
            name = "工厂参观纪录片（1分钟）",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        MediaFile(
            id = "v3", type = MediaFile.Type.VIDEO,
            name = "大众新能源技术展示（30秒）",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        MediaFile(
            id = "v4", type = MediaFile.Type.VIDEO,
            name = "机器人互动演示（40秒）",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        )
    )
}
