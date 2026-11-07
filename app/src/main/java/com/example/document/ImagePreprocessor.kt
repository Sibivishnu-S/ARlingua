package com.example.document

import android.graphics.*
import android.media.Image
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /**
     * Analyzes document frame quality (Luminance, Contrast, Blur)
     */
    fun analyzeQuality(bitmap: Bitmap): DocumentQualityReport {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val stepX = max(1, width / 100)
            val stepY = max(1, height / 100)

            var totalLuminance = 0L
            var sampleCount = 0
            var sumVariance = 0L
            var prevPixelLum = 128

            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // Relative luminance formula: 0.299R + 0.587G + 0.114B
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    totalLuminance += lum
                    sumVariance += abs(lum - prevPixelLum)
                    prevPixelLum = lum
                    sampleCount++
                }
            }

            if (sampleCount == 0) return DocumentQualityReport()

            val avgLum = totalLuminance.toFloat() / sampleCount
            val avgVariance = sumVariance.toFloat() / sampleCount

            val isLowLight = avgLum < 45f
            val isBlurry = avgVariance < 6f
            val isLowContrast = avgVariance < 12f

            var warningMsg: String? = null
            if (isLowLight) warningMsg = "Low lighting detected. Move closer to light or use flash."
            else if (isBlurry) warningMsg = "Image is blurry. Hold device steady and re-focus."
            else if (isLowContrast) warningMsg = "Low contrast text. Ensure background is contrasting."

            val qualityScore = (avgLum / 255f * 0.4f) + (min(avgVariance / 30f, 1f) * 0.6f)

            DocumentQualityReport(
                isLowLight = isLowLight,
                isBlurry = isBlurry,
                isLowContrast = isLowContrast,
                averageLuminance = avgLum,
                blurScore = avgVariance,
                contrastScore = avgVariance,
                overallQualityScore = qualityScore,
                warningMessage = warningMsg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing bitmap quality", e)
            DocumentQualityReport()
        }
    }

    /**
     * Applies rotation matrix to align bitmap to screen orientation
     */
    fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Crops bitmap to specified rectangular or percentage region
     */
    fun cropRegion(source: Bitmap, leftPct: Float, topPct: Float, rightPct: Float, bottomPct: Float): Bitmap {
        val w = source.width
        val h = source.height

        val l = (leftPct.coerceIn(0f, 1f) * w).toInt()
        val t = (topPct.coerceIn(0f, 1f) * h).toInt()
        val r = (rightPct.coerceIn(0f, 1f) * w).toInt()
        val b = (bottomPct.coerceIn(0f, 1f) * h).toInt()

        val cropW = max(10, r - l)
        val cropH = max(10, b - t)

        return Bitmap.createBitmap(source, l.coerceIn(0, w - 1), t.coerceIn(0, h - 1), min(cropW, w - l), min(cropH, h - t))
    }

    /**
     * Processes bitmap based on selected PreprocessingMode to maximize OCR legibility
     */
    fun preprocessForOcr(source: Bitmap, mode: PreprocessingMode): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        when (mode) {
            PreprocessingMode.NORMAL -> {
                // Subtle contrast enhancement color matrix
                val cm = ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 5f,
                    0f, 1.1f, 0f, 0f, 5f,
                    0f, 0f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            PreprocessingMode.LOW_LIGHT -> {
                // High brightness gain + contrast boost
                val cm = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, 35f,
                    0f, 1.4f, 0f, 0f, 35f,
                    0f, 0f, 1.4f, 0f, 35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            PreprocessingMode.LOW_CONTRAST, PreprocessingMode.SHADOWED -> {
                // Grayscale + High contrast stretch
                val grayscaleCm = ColorMatrix().apply { setSaturation(0f) }
                val contrastCm = ColorMatrix(floatArrayOf(
                    1.8f, 0f, 0f, 0f, -60f,
                    0f, 1.8f, 0f, 0f, -60f,
                    0f, 0f, 1.8f, 0f, -60f,
                    0f, 0f, 0f, 1f, 0f
                ))
                grayscaleCm.postConcat(contrastCm)
                paint.colorFilter = ColorMatrixColorFilter(grayscaleCm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            PreprocessingMode.HANDWRITTEN -> {
                // Contrast boost with black stroke enhancement
                val cm = ColorMatrix(floatArrayOf(
                    1.6f, 0f, 0f, 0f, -30f,
                    0f, 1.6f, 0f, 0f, -30f,
                    0f, 0f, 1.6f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
        }

        return output
    }
}
