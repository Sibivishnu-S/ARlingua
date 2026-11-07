package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * State seal representing camera hardware & streaming status.
 */
sealed class CameraStatus {
    object Idle : CameraStatus()
    object Running : CameraStatus()
    data class Error(val message: String) : CameraStatus()
    object PermissionDenied : CameraStatus()
}

/**
 * Clean Architecture Service managing live CameraX video feed, frame analysis pipeline,
 * frame rate (FPS) calculations, and error callbacks.
 */
class CameraService(private val context: Context) {

    private val TAG = "CameraService"

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()

    // FPS calculation state
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    var currentFps: Int = 0
        private set

    var status: CameraStatus = CameraStatus.Idle
        private set

    private var totalCapturedFrames = 0L

    /**
     * Binds CameraX Preview and ImageAnalysis pipeline to the provided lifecycle and PreviewView.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameAvailable: (Bitmap, Int, Int, Int) -> Unit, // bitmap, rotationDegrees, width, height
        onError: (CameraStatus.Error) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status = CameraStatus.PermissionDenied
            Log.e(TAG, "AR_CAMERA: ERROR - Permission denied for camera access.")
            onError(CameraStatus.Error("Camera permission was denied."))
            return
        }

        Log.i(TAG, "AR_CAMERA: permission granted")
        Log.i(TAG, "AR_CAMERA: initializing ProcessCameraProvider and binding CameraX preview & analysis...")
        status = CameraStatus.Running

        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val provider = cameraProvider ?: throw IllegalStateException("Camera provider is null")
                Log.i(TAG, "AR_CAMERA: provider received")

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                Log.i(TAG, "AR_CAMERA: surface provider attached")

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysisUseCase = imageAnalysis

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    totalCapturedFrames++
                    val currentFrameNum = totalCapturedFrames

                    calculateFps()
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val w = imageProxy.width
                        val h = imageProxy.height
                        val frameBitmap = try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            Log.e(TAG, "AR_CAMERA: ERROR converting ImageProxy to Bitmap for Frame #$currentFrameNum", e)
                            null
                        }

                        if (frameBitmap != null) {
                            if (currentFrameNum % 15L == 0L || currentFrameNum <= 5L) {
                                Log.d(TAG, "AR_CAMERA: READY - Frame #$currentFrameNum active (${w}x${h}, rot=${rotation}°, FPS=$currentFps)")
                            }

                            onFrameAvailable(
                                frameBitmap,
                                rotation,
                                w,
                                h
                            )
                        } else {
                            Log.w(TAG, "AR_CAMERA: ERROR - FrameBitmap conversion returned null for Frame #$currentFrameNum")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AR_CAMERA: ERROR analyzing Frame #$currentFrameNum", e)
                    } finally {
                        try {
                            imageProxy.close()
                        } catch (closeEx: Exception) {
                            Log.e(TAG, "AR_CAMERA: ERROR closing imageProxy for Frame #$currentFrameNum", closeEx)
                        }
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                status = CameraStatus.Running
                Log.i(TAG, "AR_CAMERA: bound to lifecycle and live preview active.")

            } catch (e: Exception) {
                Log.e(TAG, "AR_CAMERA: ERROR ${e.message}", e)
                val errStatus = CameraStatus.Error("Camera launch failed: ${e.localizedMessage}")
                status = errStatus
                onError(errStatus)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun calculateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val delta = now - lastFpsTimestamp
        if (delta >= 1000) {
            currentFps = ((frameCount * 1000f) / delta).toInt()
            frameCount = 0
            lastFpsTimestamp = now
        }
    }

    fun stopCamera() {
        try {
            status = CameraStatus.Idle
            imageAnalysisUseCase?.clearAnalyzer()
            imageAnalysisUseCase = null
            cameraProvider?.unbindAll()
            Log.i(TAG, "[CAMERA_STOPPED] Camera unbound and stopped safely.")
        } catch (e: Exception) {
            Log.e(TAG, "[CAMERA_STOPPED] Error unbinding camera: ${e.message}", e)
        }
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bitmap to Base64 JPEG", e)
            ""
        }
    }

    fun release() {
        stopCamera()
        try {
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down camera executor", e)
        }
    }
}
