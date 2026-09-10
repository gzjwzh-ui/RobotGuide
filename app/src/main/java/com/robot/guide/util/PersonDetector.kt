package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.SurfaceControl
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
 * 独立 Activity 级别绑定，确保 Preview 正确显示
 */
class PersonDetector(private val context: Context) {

    private val tag = "PersonDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var running = false

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
     * @param previewView UI预览控件
     * @param lifecycleOwner AppCompatActivity（LifecycleOwner）
     */
    fun start(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cb: Callback) {
        if (running) return
        if (!isSupported()) {
            Log.w(tag, "没有相机权限，跳过人脸检测")
            return
        }
        this.callback = cb

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        ProcessCameraProvider.getInstance(context).addListener({
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get()
                bindCamera(previewView, lifecycleOwner)
            } catch (e: Exception) {
                Log.e(tag, "无法启动相机", e)
            }
        }, ContextCompat.getMainExecutor(context))

        running = true
    }

    private fun bindCamera(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val provider = cameraProvider ?: return

        // Preview - 绑定到 PreviewView 的 surface
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // ImageAnalysis - 做人脸检测
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(320, 240))
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
                preview,
                analysis
            )
            Log.d(tag, "相机绑定成功 (Preview + Analysis)")
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
                    // 忽略单帧失败
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
    }
}
