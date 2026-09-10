package com.robot.guide.util

import android.content.Context
import android.util.Log
import com.robot.guide.data.MediaFile
import com.robot.guide.data.Vehicle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 媒体资源仓库
 * 从后端 API 加载车辆图片和视频
 */
class MediaRepository(context: Context) {

    private val tag = "MediaRepo"
    private val settings = AppSettings(context)

    /**
     * 加载车辆列表
     */
    fun loadVehicles(onResult: (List<Vehicle>) -> Unit) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            // 返回内置测试数据
            onResult(getMockVehicles())
            return
        }

        Thread {
            try {
                val url = URL("$baseUrl/api/vehicles")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText()
                    reader.close()
                    val arr = JSONArray(body)
                    val vehicles = mutableListOf<Vehicle>()
                    for (i in 0 until arr.length()) {
                        vehicles.add(parseVehicle(arr.getJSONObject(i)))
                    }
                    onResult(vehicles)
                } else {
                    onResult(getMockVehicles())
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "加载车辆列表失败，使用模拟数据: ${e.message}")
                onResult(getMockVehicles())
            }
        }.start()
    }

    private fun parseVehicle(obj: JSONObject): Vehicle {
        val images = obj.optJSONArray("images")
        val imgList = mutableListOf<com.robot.guide.data.VehicleImage>()
        if (images != null) {
            for (i in 0 until images.length()) {
                val io = images.getJSONObject(i)
                imgList.add(com.robot.guide.data.VehicleImage(
                    url = io.optString("url"),
                    caption = io.optString("caption", "")
                ))
            }
        }
        return Vehicle(
            id = obj.optLong("id"),
            name = obj.optString("name"),
            brand = obj.optString("brand"),
            year = obj.optString("year"),
            category = obj.optString("category"),
            description = obj.optString("description"),
            thumbnail = obj.optString("thumbnail"),
            images = imgList
        )
    }

    /**
     * 获取模拟车辆数据（内置图片URL用于测试）
     */
    private fun getMockVehicles(): List<Vehicle> {
        return listOf(
            Vehicle(
                id = 1,
                name = "GT Concept",
                brand = "健驰",
                year = "2025",
                category = "概念车",
                description = "全新一代概念跑车，搭载双电机全轮驱动系统，零百加速仅2.3秒。",
                thumbnail = "https://images.unsplash.com/photo-1617531653332-bd46c24f2068?w=600",
                images = listOf(
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1617531653332-bd46c24f2068?w=800", "前脸"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1617788138017-80ad40651399?w=800", "侧面"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1619362088658-a6c25ad6cb3d?w=800", "内饰")
                )
            ),
            Vehicle(
                id = 2,
                name = "SUV Pro",
                brand = "健驰",
                year = "2024",
                category = "SUV",
                description = "智能豪华SUV，具备L3自动驾驶能力，续航里程达700公里。",
                thumbnail = "https://images.unsplash.com/photo-1519440515328-c46c8257b28e?w=600",
                images = listOf(
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1519440515328-c46c8257b28e?w=800", "整车"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=800", "内饰")
                )
            ),
            Vehicle(
                id = 3,
                name = "City EV",
                brand = "健驰",
                year = "2025",
                category = "微型车",
                description = "城市通勤电动车，小巧灵活，配备智能泊车和远程控制功能。",
                thumbnail = "https://images.unsplash.com/photo-1593941707882-a5bac8eb9775?w=600",
                images = listOf(
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1593941707882-a5bac8eb9775?w=800", "外观"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1552519507-da3b142c6e3d?w=800", "驾驶舱")
                )
            ),
            Vehicle(
                id = 4,
                name = "Truck X",
                brand = "健驰",
                year = "2024",
                category = "商用车",
                description = "智能物流运输车，支持编队行驶，搭载多传感器融合系统。",
                thumbnail = "https://images.unsplash.com/photo-1586281380349-632531db7ed4?w=600",
                images = listOf(
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1586281380349-632531db7ed4?w=800", "车头"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1519003722824-194d4455a60c?w=800", "货箱")
                )
            ),
            Vehicle(
                id = 5,
                name = "Roadster",
                brand = "健驰",
                year = "2025",
                category = "跑车",
                description = "纯电敞篷跑车，碳纤维车身，百公里加速2.1秒。",
                thumbnail = "https://images.unsplash.com/photo-1544636331-e26879cd4d9b?w=600",
                images = listOf(
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1544636331-e26879cd4d9b?w=800", "外观"),
                    com.robot.guide.data.VehicleImage("https://images.unsplash.com/photo-1583121274602-3e2820c69888?w=800", "内饰")
                )
            )
        )
    }

    /**
     * 获取视频列表
     */
    fun loadVideos(onResult: (List<MediaFile>) -> Unit) {
        val baseUrl = settings.backendUrl.trimEnd('/')
        if (baseUrl.isBlank()) {
            onResult(getMockVideos())
            return
        }

        Thread {
            try {
                val url = URL("$baseUrl/api/videos")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText()
                    reader.close()
                    onResult(parseVideos(body))
                } else {
                    onResult(getMockVideos())
                }
                conn.disconnect()
            } catch (e: Exception) {
                onResult(getMockVideos())
            }
        }.start()
    }

    private fun parseVideos(body: String): List<MediaFile> {
        val arr = JSONArray(body)
        val list = mutableListOf<MediaFile>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(MediaFile(
                id = obj.optString("id"),
                path = obj.optString("url"),
                name = obj.optString("name"),
                type = MediaFile.Type.VIDEO
            ))
        }
        return list
    }

    private fun getMockVideos(): List<MediaFile> {
        return listOf(
            MediaFile("v1", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "展厅宣传片", MediaFile.Type.VIDEO),
            MediaFile("v2", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "工厂参观", MediaFile.Type.VIDEO),
            MediaFile("v3", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "产品介绍", MediaFile.Type.VIDEO)
        )
    }
}
