package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/**
 * 人脸/人体检测自动唤醒
 * 前摄摄像头持续检测人脸，发现有人进入视野后触发回调
 */
class PersonDetector(private val context: Context) {

    private val tag = "PersonDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var running = false

    // 状态管理：检测到 -> 冷却几秒 -> 再次检测
    private var lastTriggerTime = 0L
    private val cooldownMs = 8000L   // 8秒冷却
    private var consecutiveFramesWithoutFace = 0
    private val leaveThreshold = 20  // 连续20帧没脸才算"人离开了"

    data class Callback(
        val onPersonEnter: () -> Unit,     // 有人进入
        val onPersonLeave: () -> Unit,     // 人离开
        val onStatusChange: (Boolean) -> Unit  // 检测到/未检测到
    )

    private var callback: Callback? = null

    fun isSupported(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(cb: Callback) {
        if (running) return
        if (!isSupported()) {
            Log.w(tag, "没有相机权限，跳过人脸检测")
            return
        }
        this.callback = cb

        // 初始化ML Kit人脸检测器
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        ProcessCameraProvider.getInstance(context).addListener({
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get()
                bindCamera()
            } catch (e: Exception) {
                Log.e(tag, "无法启动相机检测", e)
            }
        }, ContextCompat.getMainExecutor(context))

        running = true
        Log.d(tag, "人脸检测已启动")
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                // 不需要UI预览，用LifecycleOwner包装一下
                LifecycleOwnerWrapper(),
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis, preview
            )
        } catch (e: Exception) {
            Log.e(tag, "绑定相机失败", e)
        }
    }

    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector?.process(input)
                ?.addOnSuccessListener { faces ->
                    val now = System.currentTimeMillis()
                    if (faces.isNotEmpty()) {
                        consecutiveFramesWithoutFace = 0
                        callback?.onStatusChange?.invoke(true)
                        if (now - lastTriggerTime > cooldownMs) {
                            lastTriggerTime = now
                            callback?.onPersonEnter?.invoke()
                        }
                    } else {
                        consecutiveFramesWithoutFace++
                        callback?.onStatusChange?.invoke(false)
                        if (consecutiveFramesWithoutFace >= leaveThreshold) {
                            lastTriggerTime = 0L  // 重置冷却
                        }
                    }
                }
                ?.addOnFailureListener {
                    Log.w(tag, "人脸检测失败: ${it.message}")
                }
                ?.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun stop() {
        if (!running) return
        try {
            cameraProvider?.unbindAll()
            faceDetector?.close()
        } catch (_: Exception) {}
        running = false
        Log.d(tag, "人脸检测已停止")
    }

    /**
     * 占位 LifecycleOwner，让 CameraX 在非Activity场景下工作
     */
    private inner class LifecycleOwnerWrapper : androidx.lifecycle.LifecycleOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this)
        init {
            registry.markState(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        override val lifecycle get() = registry
    }
}
