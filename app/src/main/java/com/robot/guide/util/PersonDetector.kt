package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.util.concurrent.Executors

/**
 * 人脸/人体检测 + 摄像头预览
 * 关键点：
 *   - 绑定前先检测有没有前置，没有就用后置
 *   - bindCamera 必须在 cameraProvider 成功初始化后立即调用
 *   - ImageAnalysis 用 KEEP_ONLY_LATEST 避免堆积，分辨率足够 ML Kit 用就行
 */
class PersonDetector(private val context: Context) {

    private val tag = "PersonDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var running = false
    private var boundCamera: android.hardware.camera2.CameraDevice? = null

    private var lastTriggerTime = 0L
    private val cooldownMs = 10000L
    private var consecutiveFramesWithoutFace = 0
    private val leaveThreshold = 30

    data class Callback(
        val onPersonEnter: () -> Unit,
        val onPersonLeave: () -> Unit,
        val onStatusChange: (Boolean) -> Unit
    )

    private var callback: Callback? = null

    fun isSupported(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 启动摄像头预览 + 人脸检测
     */
    fun start(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cb: Callback) {
        if (running) return
        if (!isSupported()) {
            Log.w(tag, "没有相机权限，跳过人脸检测")
            return
        }
        this.callback = cb

        val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(com.google.mlkit.vision.face.FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        ProcessCameraProvider.getInstance(context).addListener({
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get()
                bindCamera(previewView, lifecycleOwner)
            } catch (e: Exception) {
                Log.e(tag, "无法启动相机: ${e.message}", e)
                // 失败时给个假的状态回调，避免 UI 一直等
                cb.onStatusChange(false)
            }
        }, ContextCompat.getMainExecutor(context))

        running = true
    }

    private fun bindCamera(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val provider = cameraProvider ?: run {
            Log.e(tag, "cameraProvider 为 null，放弃绑定")
            return
        }

        // 选摄像头：优先前置，没有就后置
        val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val cameraSelector = if (hasFront) {
            Log.d(tag, "使用前置摄像头")
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            Log.w(tag, "没有前置摄像头，使用后置")
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
            Log.d(tag, "✅ 相机绑定成功 (Preview + Analysis)")
        } catch (e: Exception) {
            Log.e(tag, "❌ 绑定相机失败: ${e.message}", e)
            // 某些老旧设备 IllegalArgumentException 时，尝试只绑定 Preview（不做人脸检测）
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                Log.w(tag, "降级：仅预览可用，人脸检测已关闭")
            } catch (e2: Exception) {
                Log.e(tag, "降级方案也失败: ${e2.message}")
            }
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
                            lastTriggerTime = 0L
                        }
                    }
                }
                ?.addOnFailureListener { err ->
                    // 单帧失败忽略，但偶尔打一条日志（避免刷屏）
                    if ((System.currentTimeMillis() / 1000) % 5 == 0L) {
                        Log.d(tag, "人脸检测失败: ${err.message}")
                    }
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
            faceDetector = null
        } catch (_: Exception) {}
        running = false
        Log.d(tag, "相机已停止")
    }
}
