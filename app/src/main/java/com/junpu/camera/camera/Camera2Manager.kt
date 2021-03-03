package com.junpu.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.Face
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.junpu.camera.utils.*
import com.junpu.log.L
import com.junpu.log.logStackTrace
import java.util.*

/**
 * Camera2 Manager
 * @author junpu
 * @date 2021/3/2
 */
class Camera2Manager(context: Context) : ICamera {

    private var cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private lateinit var previewView: TextureView
    private var displayRotation: Int = 0 // 屏幕方向

    private var isFaceFront = false // 是否是前置摄像头
    private var isRepeatCapture = false // 拍照后是否继续拍照
    private var isFaceDetect = false // 是否人脸识别

    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var mainHandler: Handler = Handler(Looper.getMainLooper())

    private lateinit var cameraId: String
    private lateinit var captureSize: Size // 拍照尺寸
    private lateinit var previewSize: Size // 预览尺寸
    private var sensorOrientation = 0 // 相机角度
    private var flashSupported = false // 是否支出闪光灯
    private var faceDetectModes: IntArray? = null // 人脸检测模式列表

    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var captureBuilder: CaptureRequest.Builder? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private lateinit var previewRequest: CaptureRequest

    private var captureCallback: ((Bitmap, Int) -> Unit)? = null // 拍照回调
    private var faceCallback: ((Array<Face>?) -> Unit)? = null // 人脸识别回调

    override fun init(faceFront: Boolean, repeatCapture: Boolean, faceDetect: Boolean) {
        isFaceFront = faceFront
        isRepeatCapture = repeatCapture
        isFaceDetect = faceDetect
    }

    /**
     * 切换摄像头
     */
    override fun switchCamera() {
        isFaceFront = !isFaceFront
        closeCamera()
        openCamera(previewView, displayRotation)
    }

    /**
     * 拍照回调
     */
    override fun doOnCapture(callback: (Bitmap, Int) -> Unit) {
        this.captureCallback = callback
    }

    /**
     * 人脸检测回调
     */
    override fun doOnFaceDetect(callback: (Array<Face>?) -> Unit) {
        this.faceCallback = callback
    }

    /**
     * 开启摄像头线程
     */
    private fun openBackgroundThread() {
        bgThread = HandlerThread("CameraBackground").apply { start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    /**
     * 关闭摄像头线程
     */
    private fun closeBackgroundThread() {
        bgThread?.quitSafely()
        try {
            bgThread?.join()
            bgThread = null
            bgHandler = null
        } catch (e: InterruptedException) {
            e.logStackTrace()
        }
    }

    /**
     * 开启摄像头
     */
    override fun openCamera(previewView: TextureView, displayRotation: Int) {
        openBackgroundThread()
        this.previewView = previewView
        this.displayRotation = displayRotation

        // 屏幕关闭，重新打开后，textureView已经可用
        if (previewView.isAvailable) {
            startCamera(previewView.width, previewView.height)
        } else {
            previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                    startCamera(w, h)
                }

                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {
                    previewView.configureTransform(w, h, previewSize, displayRotation)
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    }

    /**
     * 开启摄像头
     */
    @SuppressLint("MissingPermission")
    private fun startCamera(width: Int, height: Int) {
        buildCameraConfig(width, height)
        buildImageReader()
        previewView.configureTransform(width, height, previewSize, displayRotation)

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    L.vv(curThreadName)
                    cameraDevice = camera
                    createCameraSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, bgHandler)
        } catch (e: CameraAccessException) {
            e.logStackTrace()
        } catch (e: InterruptedException) {
            e.logStackTrace()
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * 配置输出参数
     */
    private fun buildCameraConfig(width: Int, height: Int): Boolean {
        try {
            val faceId =
                if (isFaceFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics[CameraCharacteristics.LENS_FACING] != faceId) continue
                val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                    ?: continue

                // 相机角度
                sensorOrientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION] ?: 0

                // 拍照尺寸列表
                val captureSizes = map.getOutputSizes(ImageFormat.JPEG)
                // 选择拍照尺寸
                captureSize = getCaptureSize(captureSizes) ?: continue
                L.vv("captureSize: $captureSize")

                // 预览尺寸列表
                val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)
                // 选择预览尺寸
                previewSize = getPreviewSize(
                    previewSizes,
                    captureSize,
                    width,
                    height,
                    displayRotation,
                    sensorOrientation
                )
                L.vv("previewSize: $previewSize")

                // 是否有闪光灯
                flashSupported = characteristics[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true

                // 人脸识别模式列表
                faceDetectModes =
                    characteristics[CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES]
                // 最大识别数量
                val faceMaxCount =
                    characteristics[CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT]
                L.vv("faceDetectModes: ${Arrays.toString(faceDetectModes)}, maxCount: $faceMaxCount")

                this.cameraId = cameraId
                return true
            }
        } catch (e: CameraAccessException) {
            e.logStackTrace()
        } catch (e: NullPointerException) {
            e.logStackTrace()
        }
        return false
    }

    /**
     * 构建ImageReader
     */
    private fun buildImageReader() {
        imageReader = ImageReader.newInstance(
            captureSize.width,
            captureSize.height,
            ImageFormat.JPEG,
            1
        )
        imageReader?.setOnImageAvailableListener({
            L.vv("have a new image: $curThreadName")
            bgHandler?.post {
                val bitmap = it.acquireNextImage().toBitmap()
                L.vv("bitmap: ${bitmap.width}/${bitmap.height}")
                mainHandler.post {
                    captureCallback?.invoke(bitmap, 0)
                }
            }
        }, bgHandler)
    }

    /**
     * 创建CameraSession
     */
    private fun createCameraSession() {
        L.vv("createCameraSession")
        try {
            val texture = previewView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)

            val builder =
                previewBuilder ?: cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    .apply {
                        addTarget(surface)
                        // 3A模式，自动曝光、自动白平衡、自动对焦
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        setFaceDetectMode()
                        previewBuilder = this
                    }

            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        L.vv("createCaptureSession onConfigured: $curThreadName")
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            previewRequest = builder.build()
                            startPreview()
                        } catch (e: CameraAccessException) {
                            e.logStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        L.e("Failed")
                    }
                },
                bgHandler
            )
        } catch (e: CameraAccessException) {
            e.logStackTrace()
        }
    }

    /**
     * 设置人脸模式模式
     */
    private fun CaptureRequest.Builder.setFaceDetectMode(): Boolean {
        val modes = faceDetectModes ?: return false
        if (isFaceDetect) {
            val key = CaptureRequest.STATISTICS_FACE_DETECT_MODE
            if (modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)) {
                set(key, CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)
            } else if (modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
                set(key, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
            }
        }
        return true
    }

    /**
     * 开启预览
     */
    private fun startPreview() {
        captureSession?.setRepeatingRequest(
            previewRequest,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    val faces = result.get(CaptureResult.STATISTICS_FACES)
                    if (!faces.isNullOrEmpty()) {
                        faceCallback?.invoke(faces)
                    }
                }
            },
            bgHandler
        )
    }

    /**
     * 拍照
     */
    override fun takePhoto() {
        L.vv("takePhoto")
        try {
            val device = cameraDevice ?: return

            val builder =
                captureBuilder ?: device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    .apply {
                        L.vv("init captureRequestBuilder")
                        addTarget(imageReader!!.surface)
                        // 3A模式
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(
                            CaptureRequest.JPEG_ORIENTATION,
                            getOrientation(displayRotation, sensorOrientation)
                        )
                        captureBuilder = this
                    }

            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        if (isRepeatCapture) {
                            // 重置自动对焦开关
                            previewBuilder?.set(
                                CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                            )
                            startPreview()
                        }
                    }
                }, bgHandler)
            }
        } catch (e: CameraAccessException) {
            e.logStackTrace()
        }

    }

    /**
     * 关闭摄像头
     */
    override fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        }
        closeBackgroundThread()
        captureBuilder = null
        previewBuilder = null
    }

}