package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * States of the AR Detection Session state machine.
 */
enum class DetectionSessionState {
    IDLE,
    USER_TAP,
    CAPTURING,
    ANALYZING,
    VERIFYING,
    CONFIRMED,
    REJECTED
}

/**
 * Record holding quality, detection, and spatial overlap metrics for a single frame in a burst session.
 */
data class FrameAnalysisRecord(
    val frameIndex: Int,
    val frameId: Long,
    val timestampMs: Long,
    val isQualityGood: Boolean,
    val qualityIssue: String? = null,
    val rawCandidate: RawDetectedObject? = null,
    val label: String? = null,
    val confidence: Float = 0f,
    val isTapOverlapped: Boolean = false,
    val isOutlier: Boolean = false,
    val boundingBox: RectF? = null
)

/**
 * Detailed image quality assessment result.
 */
data class FrameQualityResult(
    val isGoodQuality: Boolean,
    val issueDescription: String? = null,
    val averageLuminance: Float = 128f,
    val contrastVariance: Float = 50f
)

/**
 * Result of the multi-frame temporal object verification pipeline.
 */
data class SessionVerificationResult(
    val sessionId: String,
    val state: DetectionSessionState,
    val tapPointScreen: Offset,
    val frameRecords: List<FrameAnalysisRecord>,
    val confirmedLabel: String? = null,
    val confirmedConfidence: Float = 0f,
    val confirmedBoundsInScreen: RectF? = null,
    val compositeReliabilityScore: Float = 0f,
    val confidenceScore: Float = 0f,
    val temporalScore: Float = 0f,
    val spatialScore: Float = 0f,
    val targetScore: Float = 0f,
    val imageQualityScore: Float = 0f,
    val statusMessage: String = "",
    val detectionEngine: String = "YOLO Multi-Frame Consensus",
    val croppedBitmapForFallback: Bitmap? = null
)

/**
 * Explicit Screen-to-Camera Coordinate Mapper.
 * Maps screen tap coordinates (UI space) to the camera image pixel space and back,
 * ensuring strict spatial relevance checks for consensus verification.
 */
data class ScreenToImageCoordinateMapper(
    val cameraBoxWidthPx: Float,
    val cameraBoxHeightPx: Float,
    val imageWidthPx: Float,
    val imageHeightPx: Float
) {
    val scale: Float = max(
        cameraBoxWidthPx / imageWidthPx.coerceAtLeast(1f),
        cameraBoxHeightPx / imageHeightPx.coerceAtLeast(1f)
    )
    val offsetX: Float = (cameraBoxWidthPx - imageWidthPx * scale) / 2f
    val offsetY: Float = (cameraBoxHeightPx - imageHeightPx * scale) / 2f

    /** Maps screen space coordinates (tapX, tapY) to rotated camera image pixel space. */
    fun mapScreenToImagePixel(screenX: Float, screenY: Float): Offset {
        val imgX = ((screenX - offsetX) / scale).coerceIn(0f, (imageWidthPx - 1f).coerceAtLeast(0f))
        val imgY = ((screenY - offsetY) / scale).coerceIn(0f, (imageHeightPx - 1f).coerceAtLeast(0f))
        return Offset(imgX, imgY)
    }

    /** Maps bounding box from camera image pixel space to screen view space. */
    fun mapImageToScreenRect(imageRect: RectF): RectF {
        val viewLeft = imageRect.left * scale + offsetX
        val viewTop = imageRect.top * scale + offsetY
        val viewRight = imageRect.right * scale + offsetX
        val viewBottom = imageRect.bottom * scale + offsetY
        return RectF(
            viewLeft.coerceIn(0f, cameraBoxWidthPx),
            viewTop.coerceIn(0f, cameraBoxHeightPx),
            viewRight.coerceIn(0f, cameraBoxWidthPx),
            viewBottom.coerceIn(0f, cameraBoxHeightPx)
        )
    }

    /** Evaluates spatial relevance in camera image pixel space. */
    fun isSpatiallyRelevantInImageSpace(imageRect: RectF, imageTapX: Float, imageTapY: Float): Boolean {
        if (imageRect.contains(imageTapX, imageTapY)) return true
        val distPx = sqrt(
            (imageRect.centerX() - imageTapX) * (imageRect.centerX() - imageTapX) +
            (imageRect.centerY() - imageTapY) * (imageRect.centerY() - imageTapY)
        )
        val maxAllowedDistPx = min(imageWidthPx, imageHeightPx) * 0.15f
        return distPx <= maxAllowedDistPx
    }

    /** Evaluates spatial relevance in screen view space. */
    fun isSpatiallyRelevantInScreenSpace(screenRect: RectF, screenTapX: Float, screenTapY: Float): Boolean {
        if (screenRect.contains(screenTapX, screenTapY)) return true
        val distPx = sqrt(
            (screenRect.centerX() - screenTapX) * (screenRect.centerX() - screenTapX) +
            (screenRect.centerY() - screenTapY) * (screenRect.centerY() - screenTapY)
        )
        return distPx <= 110f
    }
}

/**
 * Multi-Frame Object Verification Engine for ARLinguaLearn.
 * 
 * Prioritizes CORRECTNESS over SPEED:
 * - Rejects single-shot immediate predictions.
 * - Captures 5-8 frame sequences over ~500-800ms.
 * - Enforces tap spatial containment & proximity filtering via explicit ScreenToImageCoordinateMapper.
 * - Rejects temporary single-frame outliers (e.g. 1 frame Laptop among 5 frames Phone).
 * - Checks bounding box spatial stability across frames.
 * - Rejects low-quality, dark, or blurry frames.
 * - Calculates composite reliability score (Confidence + Temporal + Spatial + Target + Quality).
 * - Enforces MIN_RELIABILITY_SCORE threshold before reaching UI.
 */
class MultiFrameObjectVerifier(
    private val objectDetectionService: ObjectDetectionService
) {
    companion object {
        private const val TAG = "MultiFrameObjectVerifier"
        const val ANALYSIS_FRAME_COUNT = 6
        const val FRAME_CAPTURE_INTERVAL_MS = 80L
        const val MIN_RELIABILITY_SCORE = 0.65f
    }

    /**
     * Requirement 7: Evaluates whether an input bitmap frame is suitable for recognition.
     * Checks luminance (dark/overexposed) and contrast variance (blur/corruption).
     */
    fun evaluateFrameQuality(bitmap: Bitmap): FrameQualityResult {
        if (bitmap.isRecycled || bitmap.width < 50 || bitmap.height < 50) {
            return FrameQualityResult(false, "Invalid or recycled bitmap frame")
        }

        val stepX = max(1, bitmap.width / 20)
        val stepY = max(1, bitmap.height / 20)
        var totalLuminance = 0f
        var sampleCount = 0

        val luminances = mutableListOf<Float>()

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                luminances.add(lum)
                totalLuminance += lum
                sampleCount++
            }
        }

        if (sampleCount == 0) return FrameQualityResult(false, "Empty frame samples")

        val avgLum = totalLuminance / sampleCount

        // Check dark / overexposed
        if (avgLum < 12f) {
            return FrameQualityResult(false, "Frame too dark (luminance: ${avgLum.toInt()})", avgLum, 0f)
        }
        if (avgLum > 248f) {
            return FrameQualityResult(false, "Frame overexposed (luminance: ${avgLum.toInt()})", avgLum, 0f)
        }

        // Calculate variance (sharpness indicator)
        var varianceSum = 0f
        for (l in luminances) {
            val diff = l - avgLum
            varianceSum += diff * diff
        }
        val variance = varianceSum / sampleCount

        if (variance < 15f) {
            return FrameQualityResult(false, "Frame too blurry or low contrast (variance: ${variance.toInt()})", avgLum, variance)
        }

        return FrameQualityResult(true, null, avgLum, variance)
    }

    /**
     * Main verification pipeline executing multi-frame capture, spatial target matching, outlier rejection,
     * spatial consistency checks, and composite reliability scoring.
     */
    suspend fun analyzeTapSession(
        sessionId: String,
        tapXScreen: Float,
        tapYScreen: Float,
        cameraBoxWidthPx: Float,
        cameraBoxHeightPx: Float,
        captureLatestFrame: () -> Pair<Bitmap?, Int>?,
        onProgressUpdate: (DetectionSessionState, String, Int) -> Unit
    ): SessionVerificationResult {
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "[Session $sessionId] Starting Multi-Frame Tap Verification at screen tap ($tapXScreen, $tapYScreen)")

        onProgressUpdate(DetectionSessionState.CAPTURING, "Capturing multi-frame sequence (1/$ANALYSIS_FRAME_COUNT)...", 1)

        val records = mutableListOf<FrameAnalysisRecord>()
        var bestCropBitmapForFallback: Bitmap? = null

        // 1. Multi-Frame Burst Capture (6 frames over ~500ms)
        for (frameIdx in 1..ANALYSIS_FRAME_COUNT) {
            if (!coroutineContext.isActive) {
                Log.w(TAG, "[Session $sessionId] Session cancelled during frame capture.")
                return SessionVerificationResult(
                    sessionId = sessionId,
                    state = DetectionSessionState.REJECTED,
                    tapPointScreen = Offset(tapXScreen, tapYScreen),
                    frameRecords = emptyList(),
                    statusMessage = "Session cancelled."
                )
            }

            onProgressUpdate(
                DetectionSessionState.CAPTURING,
                "Capturing frame $frameIdx/$ANALYSIS_FRAME_COUNT...",
                ((frameIdx.toFloat() / ANALYSIS_FRAME_COUNT) * 40).toInt()
            )

            val framePair = captureLatestFrame()
            val rawBitmap = framePair?.first
            val rotationDeg = framePair?.second ?: 90

            if (rawBitmap == null || rawBitmap.isRecycled) {
                Log.w(TAG, "[Session $sessionId] Frame #$frameIdx bitmap null or recycled. Skipping.")
                delay(FRAME_CAPTURE_INTERVAL_MS)
                continue
            }

            // Quality assessment
            val qualityResult = evaluateFrameQuality(rawBitmap)
            if (!qualityResult.isGoodQuality) {
                Log.w(TAG, "[Session $sessionId] Frame #$frameIdx quality check failed: ${qualityResult.issueDescription}")
                records.add(
                    FrameAnalysisRecord(
                        frameIndex = frameIdx,
                        frameId = frameIdx.toLong(),
                        timestampMs = System.currentTimeMillis(),
                        isQualityGood = false,
                        qualityIssue = qualityResult.issueDescription
                    )
                )
                delay(FRAME_CAPTURE_INTERVAL_MS)
                continue
            }

            // Rotate bitmap to match display orientation
            val rotatedBitmap = if (rotationDeg != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDeg.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // Instantiate explicit Screen-to-Camera Coordinate Mapper
            val mapper = ScreenToImageCoordinateMapper(
                cameraBoxWidthPx = cameraBoxWidthPx,
                cameraBoxHeightPx = cameraBoxHeightPx,
                imageWidthPx = rotatedBitmap.width.toFloat(),
                imageHeightPx = rotatedBitmap.height.toFloat()
            )

            // Explicitly map screen tap coordinates (tapXScreen, tapYScreen) to camera image pixel coordinates
            val imageTapPoint = mapper.mapScreenToImagePixel(tapXScreen, tapYScreen)
            val bitmapTapX = imageTapPoint.x
            val bitmapTapY = imageTapPoint.y

            Log.d(
                TAG,
                "[Coordinate Mapping] Screen Tap ($tapXScreen, $tapYScreen) -> Rotated Image Pixel ($bitmapTapX, $bitmapTapY) | Scale: ${mapper.scale}, Offset: (${mapper.offsetX}, ${mapper.offsetY})"
            )

            // Create ~350x350 crop region for second stage fallback centered around mapped image tap point
            val targetCropSizePx = 350
            val cropSizePx = min(targetCropSizePx, min(rotatedBitmap.width, rotatedBitmap.height))
            val cropLeft = (bitmapTapX - cropSizePx / 2f).toInt().coerceIn(0, max(0, rotatedBitmap.width - cropSizePx))
            val cropTop = (bitmapTapY - cropSizePx / 2f).toInt().coerceIn(0, max(0, rotatedBitmap.height - cropSizePx))
            val actualCropW = min(cropSizePx, rotatedBitmap.width - cropLeft)
            val actualCropH = min(cropSizePx, rotatedBitmap.height - cropTop)

            val cropped = Bitmap.createBitmap(rotatedBitmap, cropLeft, cropTop, actualCropW, actualCropH)
            if (bestCropBitmapForFallback == null) {
                bestCropBitmapForFallback = cropped
            }

            // Run YOLO / ML Kit detection on this frame passing mapped image tap point
            val detectedObj = objectDetectionService.detectObjectAtTap(
                fullBitmap = rotatedBitmap,
                tapX = bitmapTapX,
                tapY = bitmapTapY,
                cropBitmap = cropped,
                minConfidenceThreshold = 0.40f // Lower initial threshold for temporal aggregator
            )

            if (detectedObj != null) {
                // Map bounding box from camera image space back to screen space using coordinate mapper
                val screenRect = mapper.mapImageToScreenRect(detectedObj.boundingBox)

                // Verify spatial relevance in BOTH camera image space and screen view space
                val isRelevantInImage = mapper.isSpatiallyRelevantInImageSpace(detectedObj.boundingBox, bitmapTapX, bitmapTapY)
                val isRelevantInScreen = mapper.isSpatiallyRelevantInScreenSpace(screenRect, tapXScreen, tapYScreen)
                val isTapOverlap = isRelevantInImage || isRelevantInScreen

                val distToCenterScreen = sqrt(
                    (screenRect.centerX() - tapXScreen) * (screenRect.centerX() - tapXScreen) +
                    (screenRect.centerY() - tapYScreen) * (screenRect.centerY() - tapYScreen)
                )

                records.add(
                    FrameAnalysisRecord(
                        frameIndex = frameIdx,
                        frameId = frameIdx.toLong(),
                        timestampMs = System.currentTimeMillis(),
                        isQualityGood = true,
                        rawCandidate = detectedObj,
                        label = detectedObj.label,
                        confidence = detectedObj.confidence,
                        isTapOverlapped = isTapOverlap,
                        boundingBox = screenRect
                    )
                )

                Log.i(
                    TAG,
                    "[Session $sessionId] Frame #$frameIdx: Detected '${detectedObj.label}' (${(detectedObj.confidence * 100).toInt()}%) " +
                    "| Spatially Relevant: $isTapOverlap (ImageSpace: $isRelevantInImage, ScreenSpace: $isRelevantInScreen) | Screen CenterDist: ${distToCenterScreen.toInt()}px"
                )
            } else {
                records.add(
                    FrameAnalysisRecord(
                        frameIndex = frameIdx,
                        frameId = frameIdx.toLong(),
                        timestampMs = System.currentTimeMillis(),
                        isQualityGood = true,
                        qualityIssue = "No detection at tap point"
                    )
                )
                Log.w(TAG, "[Session $sessionId] Frame #$frameIdx: No candidate object detected at tap point.")
            }

            delay(FRAME_CAPTURE_INTERVAL_MS)
        }

        // 2. Multi-Frame Analysis & Outlier Rejection
        onProgressUpdate(DetectionSessionState.ANALYZING, "Analyzing frames & filtering outliers...", 50)

        val validTargetRecords = records.filter { it.isQualityGood && it.label != null && it.isTapOverlapped }

        if (validTargetRecords.isEmpty()) {
            Log.w(TAG, "[Session $sessionId] REJECTED: No target-overlapped detections across captured frames.")
            return SessionVerificationResult(
                sessionId = sessionId,
                state = DetectionSessionState.REJECTED,
                tapPointScreen = Offset(tapXScreen, tapYScreen),
                frameRecords = records,
                statusMessage = "No recognizable object found near tap location.",
                croppedBitmapForFallback = bestCropBitmapForFallback
            )
        }

        // Group predictions by label name (case-insensitive)
        val labelGroups = validTargetRecords.groupBy { it.label!!.lowercase() }
        val dominantGroup = labelGroups.maxByOrNull { entry ->
            // Count + average confidence weighting
            entry.value.size * 10f + entry.value.sumOf { it.confidence.toDouble() }.toFloat()
        }

        val dominantLabelNorm = dominantGroup?.key ?: ""
        val dominantLabel = validTargetRecords.firstOrNull { it.label!!.lowercase() == dominantLabelNorm }?.label ?: ""
        val dominantCount = dominantGroup?.value?.size ?: 0
        val totalValidCount = validTargetRecords.size

        // Flag outlier records
        val processedRecords = records.map { rec ->
            if (rec.label != null && rec.label.lowercase() != dominantLabelNorm) {
                Log.w(TAG, "[Outlier Rejection] Frame #${rec.frameIndex}: Tagged '${rec.label}' (${(rec.confidence * 100).toInt()}%) as OUTLIER vs dominant '$dominantLabel' ($dominantCount/$totalValidCount frames)")
                rec.copy(isOutlier = true)
            } else {
                rec
            }
        }

        val dominantRecords = processedRecords.filter { !it.isOutlier && it.label != null && it.isTapOverlapped }

        // 3. Calculate Component Scores for Composite Reliability
        onProgressUpdate(DetectionSessionState.VERIFYING, "Verifying target stability & evidence score...", 75)

        // A. Confidence Score: Average model confidence of dominant class
        val confidenceScore = if (dominantRecords.isNotEmpty()) dominantRecords.map { it.confidence }.average().toFloat() else 0f

        // B. Temporal Score: Ratio of dominant frames to total valid target frames
        val temporalScore = (dominantCount.toFloat() / ANALYSIS_FRAME_COUNT.toFloat()).coerceIn(0f, 1f)

        // C. Spatial Consistency Score: Measure bounding box center shift variance across frames
        val spatialScore: Float
        if (dominantRecords.size >= 2) {
            val centerXs = dominantRecords.map { it.boundingBox?.centerX() ?: tapXScreen }
            val centerYs = dominantRecords.map { it.boundingBox?.centerY() ?: tapYScreen }
            val avgX = centerXs.average().toFloat()
            val avgY = centerYs.average().toFloat()

            var varianceSum = 0f
            for (i in dominantRecords.indices) {
                val dx = centerXs[i] - avgX
                val dy = centerYs[i] - avgY
                varianceSum += sqrt(dx * dx + dy * dy)
            }
            val avgDriftPx = varianceSum / dominantRecords.size
            // Drift <= 30px is 1.0, Drift >= 150px is 0.0
            spatialScore = (1.0f - ((avgDriftPx - 30f) / 120f)).coerceIn(0f, 1f)
        } else if (dominantRecords.size == 1) {
            spatialScore = 0.70f
        } else {
            spatialScore = 0f
        }

        // D. Target Overlap Score: Fraction of captured frames directly containing tap point
        val directTapContainmentCount = dominantRecords.count { it.boundingBox?.contains(tapXScreen, tapYScreen) == true }
        val targetScore = (directTapContainmentCount.toFloat() / dominantCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

        // E. Image Quality Score: Ratio of good-quality frames
        val goodQualityCount = records.count { it.isQualityGood }
        val imageQualityScore = (goodQualityCount.toFloat() / ANALYSIS_FRAME_COUNT.toFloat()).coerceIn(0f, 1f)

        // Composite Reliability Score Calculation
        val compositeReliabilityScore = (
            0.30f * confidenceScore +
            0.25f * temporalScore +
            0.20f * spatialScore +
            0.15f * targetScore +
            0.10f * imageQualityScore
        ).coerceIn(0f, 1f)

        // Average bounding box for confirmed object
        val avgLeft = dominantRecords.map { it.boundingBox?.left ?: tapXScreen }.average().toFloat()
        val avgTop = dominantRecords.map { it.boundingBox?.top ?: tapYScreen }.average().toFloat()
        val avgRight = dominantRecords.map { it.boundingBox?.right ?: tapXScreen }.average().toFloat()
        val avgBottom = dominantRecords.map { it.boundingBox?.bottom ?: tapYScreen }.average().toFloat()
        val confirmedBounds = RectF(avgLeft, avgTop, avgRight, avgBottom)

        Log.i(TAG, "[Session $sessionId] METRICS BREAKDOWN:")
        Log.i(TAG, " - Dominant Object: '$dominantLabel' ($dominantCount/$ANALYSIS_FRAME_COUNT frames)")
        Log.i(TAG, " - Confidence Score: String.format('%.2f', $confidenceScore)")
        Log.i(TAG, " - Temporal Score: String.format('%.2f', $temporalScore)")
        Log.i(TAG, " - Spatial Score: String.format('%.2f', $spatialScore)")
        Log.i(TAG, " - Target Overlap Score: String.format('%.2f', $targetScore)")
        Log.i(TAG, " - Quality Score: String.format('%.2f', $imageQualityScore)")
        Log.i(TAG, " => COMPOSITE RELIABILITY SCORE: String.format('%.2f', $compositeReliabilityScore) (Threshold: $MIN_RELIABILITY_SCORE)")

        return if (compositeReliabilityScore >= MIN_RELIABILITY_SCORE) {
            onProgressUpdate(DetectionSessionState.CONFIRMED, "Verified $dominantLabel", 100)
            Log.i(TAG, "[Session $sessionId] CONFIRMED: '$dominantLabel' verified with score ${String.format("%.2f", compositeReliabilityScore)}")

            SessionVerificationResult(
                sessionId = sessionId,
                state = DetectionSessionState.CONFIRMED,
                tapPointScreen = Offset(tapXScreen, tapYScreen),
                frameRecords = processedRecords,
                confirmedLabel = dominantLabel,
                confirmedConfidence = confidenceScore,
                confirmedBoundsInScreen = confirmedBounds,
                compositeReliabilityScore = compositeReliabilityScore,
                confidenceScore = confidenceScore,
                temporalScore = temporalScore,
                spatialScore = spatialScore,
                targetScore = targetScore,
                imageQualityScore = imageQualityScore,
                statusMessage = "Confirmed: $dominantLabel",
                detectionEngine = "YOLO Multi-Frame Consensus",
                croppedBitmapForFallback = bestCropBitmapForFallback
            )
        } else {
            onProgressUpdate(DetectionSessionState.REJECTED, "Low reliability score (${String.format("%.2f", compositeReliabilityScore)})", 100)
            Log.w(TAG, "[Session $sessionId] REJECTED: Composite score ${String.format("%.2f", compositeReliabilityScore)} below minimum threshold $MIN_RELIABILITY_SCORE")

            SessionVerificationResult(
                sessionId = sessionId,
                state = DetectionSessionState.REJECTED,
                tapPointScreen = Offset(tapXScreen, tapYScreen),
                frameRecords = processedRecords,
                confirmedLabel = dominantLabel,
                confirmedConfidence = confidenceScore,
                confirmedBoundsInScreen = confirmedBounds,
                compositeReliabilityScore = compositeReliabilityScore,
                confidenceScore = confidenceScore,
                temporalScore = temporalScore,
                spatialScore = spatialScore,
                targetScore = targetScore,
                imageQualityScore = imageQualityScore,
                statusMessage = "Insufficient evidence to verify object.",
                detectionEngine = "Unconfirmed local detection",
                croppedBitmapForFallback = bestCropBitmapForFallback
            )
        }
    }
}
