package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class ImageQualityReport(
    val isLowLight: Boolean = false,
    val isBlurry: Boolean = false,
    val avgLuminance: Float = 128f,
    val blurScore: Float = 100f
)

class MlKitManager(private val context: Context) {

    private val TAG = "MlKitManager"

    // Real-time object detector client
    private val objectDetector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification() // Return categories and confidence scores
            .build()
        ObjectDetection.getClient(options)
    }

    // Real-time text recognition (OCR) client
    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Processes an Android media Image frame from the camera feed for Object Detection.
     */
    fun detectObjects(
        image: Image,
        rotationDegrees: Int,
        onSuccess: (List<DetectedObject>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            objectDetector.process(inputImage)
                .addOnSuccessListener { detectedObjects ->
                    onSuccess(detectedObjects)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Object detection failed", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating object detection", e)
            onFailure(e)
        }
    }

    /**
     * Processes an Android media Image frame for Text Recognition (OCR).
     */
    fun recognizeText(
        image: Image,
        rotationDegrees: Int,
        onSuccess: (Text) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    onSuccess(text)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Text recognition failed", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating text recognition", e)
            onFailure(e)
        }
    }

    /**
     * Processes a Bitmap for Text Recognition (OCR).
     */
    fun recognizeTextFromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        onSuccess: (Text) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    onSuccess(text)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Text recognition from bitmap failed", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing bitmap OCR", e)
            onFailure(e)
        }
    }

    /**
     * Analyzes image quality (brightness & sharpness/blur) from a YUV image buffer.
     */
    fun checkImageQuality(image: Image): ImageQualityReport {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return ImageQualityReport()

            val buffer = planes[0].buffer // Y (Luminance) plane
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            var totalLum = 0L
            val step = maxOf(1, bytes.size / 1000) // Sample 1000 pixels for fast performance
            var count = 0
            var sumDiff = 0L

            var prevLum = (bytes[0].toInt() and 0xFF)
            for (i in 0 until bytes.size step step) {
                val lum = bytes[i].toInt() and 0xFF
                totalLum += lum
                sumDiff += kotlin.math.abs(lum - prevLum)
                prevLum = lum
                count++
            }

            val avgLum = if (count > 0) totalLum.toFloat() / count else 128f
            val avgDiff = if (count > 0) sumDiff.toFloat() / count else 50f

            val isLowLight = avgLum < 35f
            val isBlurry = avgDiff < 5f // Low gradient variation indicates motion blur

            ImageQualityReport(
                isLowLight = isLowLight,
                isBlurry = isBlurry,
                avgLuminance = avgLum,
                blurScore = avgDiff
            )
        } catch (e: Exception) {
            ImageQualityReport()
        }
    }

    /**
     * Closes ML Kit resources to prevent memory leaks in compliance with lifecycle requirements.
     */
    fun close() {
        try {
            objectDetector.close()
            textRecognizer.close()
            Log.i(TAG, "ML Kit detector resources released successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ML Kit resources", e)
        }
    }
}

