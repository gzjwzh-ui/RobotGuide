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
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/**
 * 人脸/人体检测 + 摄像头预览
 * - 前摄摄像头持续预览到 PreviewView
 * - 同时做人脸检测，发现有人进入视野后触发回调
 */
class PersonDetector(private val context: Context) {

    private val tag = "PersonDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var running = false

    // 状态管理
    private var lastTriggerTime = 0L
    private val cooldownMs = 10000L   // 10秒冷却
    private var consecutiveFramesWithoutFace = 0
    private val leaveThreshold = 30  // 连续30帧没脸才算"人离开了"

    data class Callback(
        val onPersonEnter: () -> Unit,
        val onPersonLeave: () -> Unit,
        val onStatusChange: (Boolean) -> Unit
    )

    private var callback: Callback? = null
    private var previewView: PreviewView? = null

    fun isSupported(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 启动摄像头预览 + 人脸检测
     * @param previewView UI预览控件
     * @param lifecycleOwner 必须是真正的 LifecycleOwner（如 AppCompatActivity）
     */
    fun start(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cb: Callback) {
        if (running) return
        if (!isSupported()) {
            Log.w(tag, "没有相机权限，跳过人脸检测")
            return
        }
        this.previewView = previewView
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
                bindCamera(lifecycleOwner)
            } catch (e: Exception) {
                Log.e(tag, "无法启动相机检测", e)
            }
        }, ContextCompat.getMainExecutor(context))

        running = true
        Log.d(tag, "人脸检测+预览已启动")
    }

    private fun bindCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val provider = cameraProvider ?: return

        // 1. 创建 Preview 并绑定到 PreviewView
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        // 2. 创建 ImageAnalysis 做人脸检测
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
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview, analysis
            )
            Log.d(tag, "相机已绑定到生命周期 + PreviewView")
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
                            lastTriggerTime = 0L
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
}
