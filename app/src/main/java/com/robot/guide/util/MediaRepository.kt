package com.robot.guide.util

import android.content.Context
import android.util.Log
import com.robot.guide.R
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
 *   1. mock 图片用本地 drawable（不依赖网络，国内无法访问 picsum.photos）
 *   2. mock 视频用国内可访问的 CDN
 *   3. 后端拉不到数据时快速返回 mock
 */
class MediaRepository(context: Context) {

    private val tag = "MediaRepo"
    private val ctx = context.applicationContext
    private val settings = AppSettings(context)
    private val pkg = ctx.packageName

    private fun baseUrl(): String = settings.backendUrl.trimEnd('/')

    /** 把后端返回的相对路径补成完整 URL */
    private fun absolutePath(path: String): String {
        if (path.isBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = baseUrl()
        return if (base.isBlank()) path else "$base$path"
    }

    /** 本地占位图 resource URL */
    private fun localDrawableUrl(drawableRes: Int): String =
        "android.resource://$pkg/$drawableRes"

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
            val rawPath = io.optString("stored_path")
            val url = if (rawPath.isNotBlank()) absolutePath(rawPath) else io.optString("url")
            images.add(VehicleImage(url = url, caption = io.optString("caption", "")))
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
     * 模拟车辆数据 —— 5 辆，用本地占位图（不依赖网络）
     * 实际车辆图片可通过后端 /api/vehicles 管理上传
     */
    private fun getMockVehicles(): List<Vehicle> {
        val placeholderIds = listOf(R.drawable.placeholder_car1, R.drawable.placeholder_car2,
            R.drawable.placeholder_car3, R.drawable.placeholder_car4, R.drawable.placeholder_car5)

        val defs = listOf(
            Triple("高尔夫 GTI", "大众", "经典两厢性能车，搭载 2.0T EA888 引擎，7 秒破百。"),
            Triple("速腾 L", "大众", "加长轴距 2791mm，后排腿部空间越级，家用舒适首选。"),
            Triple("迈腾 380", "大众", "商务中型轿车，EA390 2.0T 高功，动力充沛稳重大气。"),
            Triple("途观 L", "大众", "中型 SUV 标杆，四驱系统，7 座可选，适合家庭出游。"),
            Triple("ID.4 纯电", "大众", "MEB 纯电平台，续航 600km+，L2 自动驾驶。")
        )

        return defs.mapIndexed { i, (name, brand, desc) ->
            val drawableId = placeholderIds[i]
            Vehicle(
                id = (i + 1).toLong(),
                name = name,
                brand = brand,
                year = "2024",
                category = if (i < 3) "轿车" else if (i == 3) "SUV" else "新能源",
                description = desc,
                thumbnail = localDrawableUrl(drawableId),
                images = listOf(
                    VehicleImage(url = localDrawableUrl(drawableId), caption = "车辆展示"),
                    VehicleImage(url = localDrawableUrl(drawableId), caption = "外观细节"),
                    VehicleImage(url = localDrawableUrl(drawableId), caption = "内饰")
                )
            )
        }
    }

    fun loadVideos(onResult: (List<MediaFile>) -> Unit) {
        val base = baseUrl()
        if (base.isBlank()) {
            onResult(getMockVideos()); return
        }
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
     * Mock 视频 —— 国内可访问的公开 CDN
     * 注意：这些是公共测试资源，正式部署应放自己 NAS
     */
    private fun getMockVideos(): List<MediaFile> = listOf(
        MediaFile(
            id = "v1", type = MediaFile.Type.VIDEO,
            name = "展厅介绍",
            // 国内可用的测试视频
            path = "https://media.w3.org/2010/05/bunny/trailer.mp4"
        ),
        MediaFile(
            id = "v2", type = MediaFile.Type.VIDEO,
            name = "机器人演示",
            path = "https://media.w3.org/2010/05/video/movie_300.mp4"
        ),
        MediaFile(
            id = "v3", type = MediaFile.Type.VIDEO,
            name = "车辆讲解",
            path = "https://media.w3.org/2010/05/sintel/trailer_hd.mp4"
        )
    )
}
