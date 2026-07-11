package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Environment
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var activeRecording: Recording? = null
    var currentRecordingFile: File? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    
    var isRecording = false
        private set
    var isPaused = false
        private set

    companion object {
        private const val TAG = "CameraManager"
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ProcessCameraProvider: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        facing: String,
        resolution: String,
        fps: Int,
        onFrameCaptured: (ByteArray) -> Unit,
        onRecordingEvent: (VideoRecordEvent) -> Unit = {},
        onQrCodeScanned: ((String) -> Unit)? = null
    ) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // 1. Selector for Camera Facing
        val cameraSelector = if (facing == "FRONT") {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // 2. Preview Use Case
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(surfaceProvider)
        }

        // 3. Image Analysis Use Case (for frame grabbing)
        val targetSize = when (resolution) {
            "480p" -> Size(640, 480)
            "1080p" -> Size(1920, 1080)
            "4K" -> Size(3840, 2160)
            else -> Size(1280, 720) // 720p default
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(targetSize)
            .build()

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            if (onQrCodeScanned != null) {
                com.example.ui.QrCodeUtils.decodeQrCode(imageProxy)?.let { text ->
                    onQrCodeScanned(text)
                }
            }
            val jpegBytes = yuvToJpeg(imageProxy)
            if (jpegBytes != null) {
                onFrameCaptured(jpegBytes)
            }
            imageProxy.close()
        }

        // 4. Video Recording Use Case
        val quality = when (resolution) {
            "480p" -> Quality.SD
            "1080p" -> Quality.FHD
            "4K" -> Quality.UHD
            else -> Quality.HD // 720p
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()
        val videoCaptureInstance = VideoCapture.withOutput(recorder)
        this.videoCapture = videoCaptureInstance

        try {
            // Bind everything to Lifecycle
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                videoCaptureInstance
            )
            camera = boundCamera
            cameraControl = boundCamera.cameraControl
            cameraInfo = boundCamera.cameraInfo
            
            // Re-apply zoom or flash properties if we have reference to controllers
            Log.i(TAG, "Camera bound successfully! Resolution: $resolution, Facing: $facing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${e.message}")
        }
    }

    fun setZoom(ratio: Float) {
        val minZoom = cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
        val maxZoom = cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f
        val clampedRatio = ratio.coerceIn(minZoom, maxZoom)
        cameraControl?.setZoomRatio(clampedRatio)
    }

    fun setFlash(enabled: Boolean) {
        cameraControl?.enableTorch(enabled)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setExposure(value: Int) {
        cameraControl?.setExposureCompensationIndex(value)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        outputDirectory: File,
        onEvent: (VideoRecordEvent) -> Unit
    ): File? {
        if (isRecording) return currentRecordingFile
        
        val foundCapture = videoCapture
        if (foundCapture == null) {
            Log.e(TAG, "Cannot start recording: VideoCapture use case is not bound!")
            return null
        }

        // Create file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VID_$timeStamp.mp4"
        val outputFile = File(outputDirectory, fileName)
        currentRecordingFile = outputFile

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        val recording = foundCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        isPaused = false
                        Log.i(TAG, "Recording started: ${outputFile.absolutePath}")
                    }
                    is VideoRecordEvent.Pause -> {
                        isPaused = true
                        Log.i(TAG, "Recording paused")
                    }
                    is VideoRecordEvent.Resume -> {
                        isPaused = false
                        Log.i(TAG, "Recording resumed")
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        isPaused = false
                        if (event.hasError()) {
                            Log.e(TAG, "Recording finalized with error: ${event.error}")
                        } else {
                            Log.i(TAG, "Recording finalized successfully: ${outputFile.absolutePath}")
                        }
                    }
                }
                onEvent(event)
            }

        activeRecording = recording
        return outputFile
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            activeRecording?.pause()
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            activeRecording?.resume()
        }
    }

    fun stopRecording() {
        if (isRecording) {
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
            isPaused = false
        }
    }

    private fun yuvToJpeg(imageProxy: ImageProxy, quality: Int = 60): ByteArray? {
        try {
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                return null
            }
            val planes = imageProxy.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            
            val pixelStride = planes[1].pixelStride
            val rowStride = planes[1].rowStride

            if (pixelStride == 2) {
                vBuffer.get(nv21, ySize, vSize)
            } else {
                var pos = ySize
                val uRemaining = uBuffer.remaining()
                for (i in 0 until uRemaining) {
                    if (pos < nv21.size) {
                        nv21[pos++] = vBuffer.get(i)
                    }
                    if (pos < nv21.size) {
                        nv21[pos++] = uBuffer.get(i)
                    }
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), quality, out)
            return out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "YUV to JPEG compression failed: ${e.message}")
            return null
        }
    }
}
