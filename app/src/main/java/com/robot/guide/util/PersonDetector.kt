package com.robot.guide.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.io.IOException
import java.util.concurrent.Executors

/**
 * 人脸/人体检测 + 摄像头预览
 *
 * 参考原 APK CameraHelper + FaceDetectView 实现：
 *   - 使用旧 Camera API（稳定，同硬件上验证通过）
 *   - SurfaceView 预览 + PreviewCallback 获取帧数据
 *   - ML Kit 人脸检测（与原 APK 相同）
 *   - 连续帧确认防抖动（需连续 N 帧有人/无人才触发）
 */
class PersonDetector(private val context: Context) {

    private val tag = "PersonDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var camera: Camera? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var running = false

    // 状态跟踪：只在"人离开→新人到来"时触发欢迎语，不重复发问
    private var consecutiveFramesWithoutFace = 0
    private val leaveThreshold = 10  // 连续 10 帧没人 → 触发 onPersonLeave
    private var wasFaceDetected = false
    private var greetingTriggered = false  // 当前人员已发过欢迎语，离开后重置

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
     * @param previewView SurfaceView 用于预览
     * @param lifecycleOwner 兼容旧接口（实际不用，SurfaceHolder.Callback 代替）
     */
    fun start(previewView: SurfaceView, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cb: Callback) {
        if (running) return
        if (!isSupported()) {
            Log.w(tag, "没有相机权限，跳过人脸检测")
            return
        }
        this.callback = cb

        // 初始化 ML Kit 人脸检测器（Fast 模式，无轮廓/地标）
        val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(com.google.mlkit.vision.face.FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        // SurfaceView 准备好后打开相机
        val holder = previewView.holder
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                surfaceHolder = h
                openCamera()
            }
            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) {
                closeCamera()
            }
        })

        // 如果 Surface 已存在（极短生命周期），直接打开
        if (holder.surface?.isValid == true) {
            surfaceHolder = holder
            openCamera()
        }

        running = true
    }

    private fun openCamera() {
        try {
            // 优先前置摄像头
            val frontId = findFrontCameraId()
            val camId = frontId ?: Camera.CameraInfo.CAMERA_FACING_BACK
            camera = Camera.open(camId) ?: run {
                Log.e(tag, "Camera.open 返回 null")
                return
            }

            // 设置参数
            camera?.let { cam ->
                val params = cam.parameters
                // 找合适的预览尺寸（横屏：width > height）
                val targetSize = findBestPreviewSizeLandscape(params.supportedPreviewSizes)
                params.setPreviewSize(targetSize.width, targetSize.height)
                // 帧率范围
                val range = findBestFpsRange(params.supportedPreviewFpsRange)
                if (range != null) params.setPreviewFpsRange(range[0], range[1])
                cam.parameters = params

                // 设置显示方向
                setCameraDisplayOrientation(camId, cam)

                // 前置摄像头水平镜像（让预览看起来正常）
                val isFront = camId == findFrontCameraId()
                if (isFront) {
                    surfaceHolder?.surface?.let {
                        try {
                            // 用 Matrix 做水平翻转
                            val matrix = android.graphics.Matrix().apply {
                                postScale(-1f, 1f)
                            }
                        } catch (_: Exception) {}
                    }
                }

                // 绑定 SurfaceHolder
                cam.setPreviewDisplay(surfaceHolder)

                // 开始预览 + 设置帧回调
                cam.setPreviewCallback { data, camera ->
                    processFrame(data, camera)
                }
                cam.startPreview()

                Log.d(tag, "✅ 相机启动成功 (id=$camId, ${targetSize.width}x${targetSize.height}, front=$isFront)")
            }
        } catch (e: IOException) {
            Log.e(tag, "❌ 打开相机失败: ${e.message}", e)
            mainHandler.post { callback?.onStatusChange?.invoke(false) }
        } catch (e: Exception) {
            Log.e(tag, "❌ 打开相机异常: ${e.message}", e)
        }
    }

    private fun closeCamera() {
        try {
            camera?.let { cam ->
                cam.setPreviewCallback(null)
                cam.stopPreview()
                cam.release()
            }
            camera = null
            faceDetector?.close()
            faceDetector = null
            running = false
            wasFaceDetected = false
        } catch (_: Exception) {}
    }

    /**
     * 处理每帧数据 —— YUV → ML Kit → 回调
     */
    private fun processFrame(data: ByteArray, camera: Camera) {
        if (faceDetector == null || !running) return

        try {
            val params = camera.parameters
            val width = params.previewSize.width
            val height = params.previewSize.height

            // YUV → NV21 (ML Kit 需要)
            val nv21 = yuv420ToNv21(data, width, height)
            val rotation = getRotationDegrees(camera)

            val image = InputImage.fromByteArray(
                nv21, width, height, rotation, InputImage.IMAGE_FORMAT_NV21
            )

            faceDetector!!.process(image)
                .addOnSuccessListener { faces ->
                    mainHandler.post { handleFaceResult(faces.isNotEmpty()) }
                }
                .addOnFailureListener { err ->
                    if ((System.currentTimeMillis() / 1000) % 10L == 0L) {
                        Log.d(tag, "人脸检测失败: ${err.message}")
                    }
                }
        } catch (e: Exception) {
            // 忽略单帧异常
        }
    }

    private fun handleFaceResult(hasFace: Boolean) {
        callback?.onStatusChange?.invoke(hasFace)

        if (hasFace) {
            consecutiveFramesWithoutFace = 0
            // 只在"人离开后→新人到来"时触发欢迎语（greetingTriggered=false 表示需要发问）
            if (!greetingTriggered) {
                greetingTriggered = true
                wasFaceDetected = true
                callback?.onPersonEnter?.invoke()
            }
        } else {
            consecutiveFramesWithoutFace++
            // 连续多帧没人 → 触发离开，重置欢迎语状态
            if (consecutiveFramesWithoutFace >= leaveThreshold && wasFaceDetected) {
                wasFaceDetected = false
                greetingTriggered = false  // 重置：下一个人来时可以再发欢迎语
                callback?.onPersonLeave?.invoke()
            }
        }
    }

    // ========== 工具方法 ==========

    private fun findFrontCameraId(): Int? {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
        }
        return null
    }

    private fun findBestPreviewSizeLandscape(sizes: List<Camera.Size>): Camera.Size {
        // 选横屏预览（width > height），尺寸 ≤ 640x480，最接近目标
        val landscapeSizes = sizes.filter { it.width >= it.height && it.width <= 800 && it.height <= 600 }
        val pool = if (landscapeSizes.isNotEmpty()) landscapeSizes else sizes
        var best = pool[0]
        var bestDiff = Int.MAX_VALUE
        for (s in pool) {
            val diff = Math.abs(s.width - 640) + Math.abs(s.height - 480)
            if (diff < bestDiff) {
                bestDiff = diff
                best = s
            }
        }
        return best
    }

    private fun findBestFpsRange(ranges: List<IntArray>): IntArray? {
        return ranges.maxByOrNull { it[1] - it[0] }
    }

    private fun setCameraDisplayOrientation(cameraId: Int, camera: Camera) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        // 向左转90度修正方向：原值基础上逆时针90度（即+270等价于-90）
        val rotation = when {
            (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) -> (info.orientation + 180) % 360
            else -> (info.orientation + 0) % 360
        }
        try { camera.setDisplayOrientation(rotation) } catch (_: Exception) {}
    }

    private fun getRotationDegrees(camera: Camera): Int {
        // ML Kit 需要的 rotation 值（从 Camera.CameraInfo 获取）
        return try {
            val info = Camera.CameraInfo()
            // 默认用前置摄像头的 orientation
            val camId = findFrontCameraId() ?: 0
            Camera.getCameraInfo(camId, info)
            info.orientation
        } catch (_: Exception) {
            0
        }
    }

    /**
     * YUV420 → NV21 (ML Kit 需要 NV21 格式)
     */
    private fun yuv420ToNv21(yuv: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        // Y 平面直接拷贝
        System.arraycopy(yuv, 0, nv21, 0, ySize)
        // UV 平面交错（NV21 是 VUVU...，YUV420 是 UU...VV...）
        val uOffset = ySize
        val vOffset = ySize + uvSize
        for (i in 0 until uvSize) {
            nv21[ySize + i * 2] = yuv[vOffset + i]      // V
            nv21[ySize + i * 2 + 1] = yuv[uOffset + i]  // U
        }
        return nv21
    }

    fun stop() {
        closeCamera()
        executor.shutdown()
    }
}
