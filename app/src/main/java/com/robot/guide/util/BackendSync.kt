package com.robot.guide.util

import android.content.Context
import android.util.Log
import com.robot.guide.data.QAItem
import com.robot.guide.data.Vehicle
import com.robot.guide.data.VehicleImage
import com.robot.guide.db.DatabaseHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 后端同步客户端
 * 从 Python Flask 后端拉取全量配置 + 问答库 + 车辆数据
 */
class BackendSync(private val context: Context) {

    private val settings = AppSettings(context)
    private val db = DatabaseHelper.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tag = "BackendSync"

    /**
     * 拉取全量数据（配置+问答库+车辆）
     */
    fun syncAll(callback: (success: Boolean, msg: String, vehicles: List<Vehicle>) -> Unit) {
        Thread {
            try {
                val url = "${settings.backendUrl}/api/sync"
                Log.d(tag, "同步中: $url")

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        callback(false, "HTTP ${response.code}", emptyList())
                        return@use
                    }

                    val body = response.body?.string() ?: run {
                        callback(false, "空响应", emptyList())
                        return@use
                    }
                    val root = JSONObject(body)

                    // 1. 应用配置
                    val cfg = root.optJSONObject("config")
                    if (cfg != null) {
                        val cfgMap = mutableMapOf<String, String>()
                        val keys = cfg.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            cfgMap[k] = cfg.optString(k)
                        }
                        settings.applyBackendConfig(cfgMap)
                        Log.d(tag, "配置已应用: ${cfgMap.keys}")
                    }

                    // 2. 更新问答库
                    val qaArr = root.optJSONArray("qa_items")
                    if (qaArr != null) {
                        db.deleteAll()
                        for (i in 0 until qaArr.length()) {
                            val q = qaArr.getJSONObject(i)
                            val keywords = q.optJSONArray("keywords")?.let { arr ->
                                (0 until arr.length()).map { arr.optString(it) }
                            } ?: emptyList()
                            val mediaRefs = q.optJSONArray("media_refs")?.let { arr ->
                                (0 until arr.length()).map { arr.optString(it) }
                            } ?: emptyList()

                            db.insertQA(
                                QAItem(
                                    question = q.optString("question"),
                                    answer = q.optString("answer"),
                                    keywords = keywords,
                                    mediaRefs = mediaRefs,
                                    createdAt = q.optLong("created_at", System.currentTimeMillis()),
                                    updatedAt = q.optLong("updated_at", System.currentTimeMillis())
                                )
                            )
                        }
                        Log.d(tag, "问答库已更新: ${qaArr.length()} 条")
                    }

                    // 3. 解析车辆数据
                    val vehicles = mutableListOf<Vehicle>()
                    val vehArr = root.optJSONArray("vehicles")
                    if (vehArr != null) {
                        val baseUrl = settings.backendUrl
                        for (i in 0 until vehArr.length()) {
                            val v = vehArr.getJSONObject(i)
                            val imgArr = v.optJSONArray("images")
                            val images = imgArr?.let { arr ->
                                (0 until arr.length()).map { idx ->
                                    val img = arr.getJSONObject(idx)
                                    VehicleImage(
                                        id = img.optLong("id", 0),
                                        url = baseUrl + img.optString("path", ""),
                                        caption = img.optString("caption", ""),
                                        sortOrder = img.optInt("sort_order", idx)
                                    )
                                }
                            } ?: emptyList()

                            val thumbRel = v.optString("thumbnail", "")
                            val thumbUrl = if (thumbRel.isNotBlank()) baseUrl + thumbRel else null

                            vehicles.add(
                                Vehicle(
                                    id = v.optLong("id", 0),
                                    name = v.optString("name"),
                                    brand = v.optString("brand", ""),
                                    year = v.optString("year", ""),
                                    category = v.optString("category", ""),
                                    description = v.optString("description", ""),
                                    descriptionEn = v.optString("description_en", ""),
                                    descriptionYue = v.optString("description_yue", ""),
                                    thumbnail = thumbUrl,
                                    imageCount = images.size,
                                    sortOrder = v.optInt("sort_order", 0),
                                    images = images
                                )
                            )
                        }
                        Log.d(tag, "车辆数据已更新: ${vehicles.size} 款")
                    }

                    callback(true, "同步成功：${qaArr?.length() ?: 0}条问答 + ${vehicles.size}款车辆", vehicles)
                }
            } catch (e: Exception) {
                Log.e(tag, "同步失败", e)
                callback(false, e.message ?: "未知错误", emptyList())
            }
        }.start()
    }

    /**
     * 检测后端是否可达
     */
    fun testConnection(callback: (success: Boolean, msg: String) -> Unit) {
        Thread {
            try {
                val request = Request.Builder().url("${settings.backendUrl}/api/health").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        callback(true, "后端在线 ✓")
                    } else {
                        callback(false, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback(false, e.message ?: "连接失败")
            }
        }.start()
    }
}
