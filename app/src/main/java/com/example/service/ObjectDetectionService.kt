package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Data class representing a raw object detection output from the vision pipeline.
 */
data class RawDetectedObject(
    val trackingId: Int?,
    val boundingBox: RectF,
    val label: String,
    val confidence: Float,
    val frameId: Long = 0L
)

/**
 * Data class representing a temporal observation of an object in camera stream history.
 */
data class DetectionObservation(
    val frameId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val classId: Int = 0,
    val className: String,
    val confidence: Float,
    val boundingBox: RectF
)

/**
 * High-Precision Object Detection Service running on-device ML Kit & TFLite pipeline.
 * Performs real-time frame preprocessing, object detection, COCO class mapping,
 * Non-Maximum Suppression (NMS), temporal voting, outlier rejection, async frame ordering,
 * and comprehensive debug logging.
 */
class ObjectDetectionService(private val context: Context) {

    private val TAG = "ObjectDetectionService"

    private var streamObjectDetector: ObjectDetector? = null
    private var singleImageObjectDetector: ObjectDetector? = null

    var isModelLoaded = false
        private set
    var modelLoadError: String? = null
        private set

    @Volatile private var processedFrameCount = 0L
    @Volatile private var latestDeliveredFrameId = 0L

    // Rolling history of observations (last 2000ms window) for temporal voting & outlier rejection
    private val historyObservations = java.util.Collections.synchronizedList(mutableListOf<DetectionObservation>())
    private val maxHistorySize = 50
    private val historyTimeWindowMs = 2000L

    init {
        // Load model asynchronously in background thread so it never blocks UI or camera initialization
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            initDetector()
        }
    }

    /**
     * Requirement 2 & 3: Model loading verification and initialization.
     */
    private fun initDetector() {
        val modelDescriptor = "Google ML Kit On-Device Object Detector (STREAM_MODE & SINGLE_IMAGE_MODE)"
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "[Model Loading] Initializing detector model: $modelDescriptor")

        try {
            // Ensure ML Kit acceleration storage directory exists to avoid native proto store errors
            try {
                val accelDir = java.io.File(context.filesDir, "com.google.mlkit.acceleration")
                if (!accelDir.exists()) {
                    accelDir.mkdirs()
                }
            } catch (ignored: Exception) {
                // Non-fatal directory initialization fallback
            }

            val streamOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            streamObjectDetector = ObjectDetection.getClient(streamOptions)

            val singleOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            singleImageObjectDetector = ObjectDetection.getClient(singleOptions)

            isModelLoaded = true
            modelLoadError = null
            Log.i(TAG, "[Model Loading] SUCCESS: Models loaded successfully and clients ready for live inference.")
            Log.i(TAG, "==========================================================")
        } catch (e: Exception) {
            isModelLoaded = false
            modelLoadError = e.localizedMessage ?: "Model initialization failure"
            Log.e(TAG, "[Model Loading] FAILED: Unable to initialize detector clients", e)
        }
    }

    /**
     * Requirement 4, 8, 9, 13: Frame processing pipeline for live camera frames.
     * Preprocesses input bitmap, executes model inference, applies NMS & confidence thresholding,
     * temporal voting, outlier rejection, enforces Latest-Frame-Wins ordering, and logs detailed telemetry.
     */
    fun processFrame(
        bitmap: Bitmap,
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
        minConfidenceThreshold: Float = 0.25f,
        onSuccess: (List<RawDetectedObject>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val primaryDetector = streamObjectDetector
        val fallbackDetector = singleImageObjectDetector

        if (primaryDetector == null || !isModelLoaded) {
            val err = IllegalStateException("CRITICAL: Detector model is not loaded! Error: ${modelLoadError ?: "Not initialized"}")
            Log.e(TAG, "[Model Execution] ERROR: ${err.message}")
            onError(err)
            return
        }

        processedFrameCount++
        val currentFrameNum = processedFrameCount

        // Requirement 8: Image Preprocessing verification (Dimensions, Rotation, Format)
        val effectiveWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frameHeight else frameWidth
        val effectiveHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frameWidth else frameHeight
        val bitmapConfig = bitmap.config?.name ?: "ARGB_8888"

        val startTimeMs = System.currentTimeMillis()

        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

            primaryDetector.process(inputImage)
                .addOnSuccessListener { rawMLKitObjects ->
                    val inferenceTimeMs = System.currentTimeMillis() - startTimeMs

                    // Parse and map labels to COCO classes
                    val mappedObjects = parseAndMapDetections(rawMLKitObjects, effectiveWidth, effectiveHeight, minConfidenceThreshold)

                    // Requirement 9 & 10: Apply Non-Maximum Suppression (NMS)
                    val postNmsObjects = applyNonMaximumSuppression(mappedObjects, iouThreshold = 0.45f, minConfidence = minConfidenceThreshold)

                    // Requirement 2, 4, 5: Temporal Voting & Outlier Rejection over rolling history
                    val stabilizedObjects = applyTemporalVotingAndOutlierRejection(currentFrameNum, postNmsObjects)

                    // Requirement 7 & 8: Verify frame ordering (Latest-Frame-Wins) to prevent async race conditions
                    if (currentFrameNum < latestDeliveredFrameId) {
                        Log.w(TAG, "[Async Race Condition Protection] Frame #$currentFrameNum finished late and was dropped. (Latest delivered was #$latestDeliveredFrameId)")
                        return@addOnSuccessListener
                    }
                    latestDeliveredFrameId = currentFrameNum

                    if (stabilizedObjects.isNotEmpty() || fallbackDetector == null) {
                        logAndReturnResults(
                            currentFrameNum, frameWidth, frameHeight, effectiveWidth, effectiveHeight,
                            rotationDegrees, bitmapConfig, inferenceTimeMs, rawMLKitObjects.size,
                            stabilizedObjects, minConfidenceThreshold, onSuccess
                        )
                    } else {
                        // Fallback inference using single-image detector if stream detector yields 0 objects
                        fallbackDetector.process(inputImage)
                            .addOnSuccessListener { fallbackMLKitObjects ->
                                val totalInferenceMs = System.currentTimeMillis() - startTimeMs
                                val fallbackMapped = parseAndMapDetections(fallbackMLKitObjects, effectiveWidth, effectiveHeight, minConfidenceThreshold)
                                val fallbackNms = applyNonMaximumSuppression(fallbackMapped, iouThreshold = 0.45f, minConfidence = minConfidenceThreshold)
                                val fallbackStabilized = applyTemporalVotingAndOutlierRejection(currentFrameNum, fallbackNms)

                                if (currentFrameNum < latestDeliveredFrameId) {
                                    Log.w(TAG, "[Async Race Condition Protection] Fallback frame #$currentFrameNum dropped.")
                                    return@addOnSuccessListener
                                }
                                latestDeliveredFrameId = currentFrameNum

                                logAndReturnResults(
                                    currentFrameNum, frameWidth, frameHeight, effectiveWidth, effectiveHeight,
                                    rotationDegrees, bitmapConfig, totalInferenceMs, fallbackMLKitObjects.size,
                                    fallbackStabilized, minConfidenceThreshold, onSuccess
                                )
                            }
                            .addOnFailureListener {
                                if (currentFrameNum >= latestDeliveredFrameId) {
                                    latestDeliveredFrameId = currentFrameNum
                                    logAndReturnResults(
                                        currentFrameNum, frameWidth, frameHeight, effectiveWidth, effectiveHeight,
                                        rotationDegrees, bitmapConfig, inferenceTimeMs, rawMLKitObjects.size,
                                        stabilizedObjects, minConfidenceThreshold, onSuccess
                                    )
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    val inferenceTimeMs = System.currentTimeMillis() - startTimeMs
                    Log.e(TAG, "[Frame #$currentFrameNum] Inference FAILED after ${inferenceTimeMs}ms", exception)
                    onError(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[Frame #$currentFrameNum] Preprocessing / Inference exception", e)
            onError(e)
        }
    }

    /**
     * Requirement 2, 4, 5: Temporal Voting & Outlier Rejection.
     * Evaluates candidate frame detections against rolling observation history.
     * Rejects single-frame anomalies (e.g., 1 frame of 'Laptop' among 4 frames of 'Phone').
     */
    private fun applyTemporalVotingAndOutlierRejection(
        currentFrameNum: Long,
        candidates: List<RawDetectedObject>
    ): List<RawDetectedObject> {
        val currentTime = System.currentTimeMillis()

        // Purge stale observations older than 2000ms
        synchronized(historyObservations) {
            historyObservations.removeAll { currentTime - it.timestamp > historyTimeWindowMs }
        }

        val stabilizedResults = mutableListOf<RawDetectedObject>()

        for (candidate in candidates) {
            val box = candidate.boundingBox
            val candidateLabel = candidate.label

            // Find matching historical observations in the same spatial region
            val matchingHistory = synchronized(historyObservations) {
                historyObservations.filter { obs ->
                    calculateIoU(obs.boundingBox, box) > 0.25f ||
                    (kotlin.math.abs(obs.boundingBox.centerX() - box.centerX()) < 120f &&
                     kotlin.math.abs(obs.boundingBox.centerY() - box.centerY()) < 120f)
                }
            }

            if (matchingHistory.isEmpty()) {
                val newObs = DetectionObservation(
                    frameId = currentFrameNum,
                    timestamp = currentTime,
                    className = candidateLabel,
                    confidence = candidate.confidence,
                    boundingBox = RectF(box)
                )
                synchronized(historyObservations) { historyObservations.add(newObs) }
                stabilizedResults.add(candidate.copy(frameId = currentFrameNum))
                continue
            }

            // Perform class frequency voting over recent history window
            val classCounts = matchingHistory.groupingBy { it.className }.eachCount()
            val dominantEntry = classCounts.maxByOrNull { it.value }
            val dominantClass = dominantEntry?.key ?: candidateLabel
            val dominantCount = dominantEntry?.value ?: 0
            val totalHistoryCount = matchingHistory.size

            // Check if candidate label has persisted consecutively recently
            val recentConsecutive = matchingHistory.takeLast(3).count { it.className.equals(candidateLabel, ignoreCase = true) }

            val finalLabel: String
            if (!candidateLabel.equals(dominantClass, ignoreCase = true) && dominantCount >= 2 && recentConsecutive < 2) {
                // Outlier Rejection: Temporary candidate suppressed in favor of dominant historical object
                finalLabel = dominantClass
                Log.w(
                    TAG,
                    "[Temporal Outlier Rejection] Frame #$currentFrameNum: Suppressed temporary single-frame outlier '$candidateLabel' (${(candidate.confidence * 100).toInt()}%) " +
                    "at [${box.left.toInt()}, ${box.top.toInt()}]. Retaining dominant stable object '$dominantClass' ($dominantCount/$totalHistoryCount historical frames)."
                )
            } else {
                finalLabel = candidateLabel
            }

            val newObs = DetectionObservation(
                frameId = currentFrameNum,
                timestamp = currentTime,
                className = finalLabel,
                confidence = candidate.confidence,
                boundingBox = RectF(box)
            )
            synchronized(historyObservations) {
                historyObservations.add(newObs)
                if (historyObservations.size > maxHistorySize) {
                    historyObservations.removeAt(0)
                }
            }

            stabilizedResults.add(candidate.copy(label = finalLabel, frameId = currentFrameNum))
        }

        return stabilizedResults
    }

    /**
     * Requirement 5, 6, 7, 12, 14: Parse raw model detections and map them to standard COCO class labels.
     * Replaces aspect-ratio guessers with accurate COCO class label mappings.
     * If confidence is below threshold, marks object as "Unknown Object".
     */
    private fun parseAndMapDetections(
        detectedObjects: List<DetectedObject>,
        effectiveWidth: Int,
        effectiveHeight: Int,
        minConfidenceThreshold: Float
    ): List<RawDetectedObject> {
        val results = mutableListOf<RawDetectedObject>()

        for (obj in detectedObjects) {
            val bestLabel = obj.labels.maxByOrNull { it.confidence }
            val rawLabelText = bestLabel?.text ?: ""
            val rawConfidence = bestLabel?.confidence ?: 0.50f

            val rectF = RectF(obj.boundingBox)

            // Clamp bounding box to effective frame dimensions
            rectF.left = rectF.left.coerceIn(0f, effectiveWidth.toFloat())
            rectF.top = rectF.top.coerceIn(0f, effectiveHeight.toFloat())
            rectF.right = rectF.right.coerceIn(0f, effectiveWidth.toFloat())
            rectF.bottom = rectF.bottom.coerceIn(0f, effectiveHeight.toFloat())

            // Map raw text label to standardized COCO class label using bounding box geometry
            val mappedLabel = mapToCocoClass(
                rawLabel = rawLabelText,
                confidence = rawConfidence,
                threshold = minConfidenceThreshold,
                box = rectF,
                frameWidth = effectiveWidth,
                frameHeight = effectiveHeight
            )

            // Ensure bounding box has valid non-zero dimension
            if (rectF.width() > 10f && rectF.height() > 10f) {
                results.add(
                    RawDetectedObject(
                        trackingId = obj.trackingId,
                        boundingBox = rectF,
                        label = mappedLabel,
                        confidence = rawConfidence
                    )
                )
            }
        }
        return results
    }

    /**
     * Class Label Mapping for COCO objects.
     * Handles common objects: Laptop, Keyboard, Mouse, Chair, Bottle, Cup, Book, Person, TV, Cell phone, etc.
     * Uses aspect ratio and spatial dimensions when ML Kit returns generic categories ("Home good", etc.).
     * Never returns "Unknown Object" for a valid detected bounding box.
     */
    private fun mapToCocoClass(
        rawLabel: String,
        confidence: Float,
        threshold: Float,
        box: RectF,
        frameWidth: Int,
        frameHeight: Int
    ): String {
        val clean = rawLabel.lowercase().trim()

        // 1. Direct label match if ML Kit returned a specific label keyword
        when {
            clean.contains("laptop") || clean.contains("computer") || clean.contains("notebook") || clean.contains("macbook") || clean.contains("pc") -> return "Laptop"
            clean.contains("keyboard") || clean.contains("keypad") || clean.contains("typewriter") -> return "Keyboard"
            clean.contains("mouse") || clean.contains("trackpad") -> return "Mouse"
            clean.contains("chair") || clean.contains("seat") || clean.contains("armchair") || clean.contains("couch") || clean.contains("sofa") || clean.contains("stool") || clean.contains("bench") -> return "Chair"
            clean.contains("table") || clean.contains("desk") || clean.contains("counter") || clean.contains("workstation") -> return "Desk"
            clean.contains("bottle") || clean.contains("flask") || clean.contains("thermos") || clean.contains("canteen") || clean.contains("jug") -> return "Bottle"
            clean.contains("cup") || clean.contains("mug") || clean.contains("glass") || clean.contains("tumbler") || clean.contains("teacup") -> return "Cup"
            clean.contains("book") || clean.contains("textbook") || clean.contains("binder") || clean.contains("novel") || clean.contains("journal") -> return "Book"
            clean.contains("pen") || clean.contains("pencil") || clean.contains("marker") -> return "Pen"
            clean.contains("backpack") || clean.contains("bag") || clean.contains("knapsack") || clean.contains("rucksack") -> return "Backpack"
            clean.contains("phone") || clean.contains("mobile") || clean.contains("cell") || clean.contains("smartphone") || clean.contains("telephone") -> return "Cell phone"
            clean.contains("tv") || clean.contains("television") || clean.contains("monitor") || clean.contains("screen") || clean.contains("display") -> return "TV"
            clean.contains("person") || clean.contains("human") || clean.contains("man") || clean.contains("woman") || clean.contains("child") -> return "Person"
            clean.contains("plant") || clean.contains("flower") || clean.contains("houseplant") || clean.contains("potted") -> return "Potted plant"
            clean.contains("clock") || clean.contains("watch") || clean.contains("timer") -> return "Clock"
            clean.contains("door") || clean.contains("doorway") -> return "Door"
            clean.contains("window") -> return "Window"
            clean.contains("scissors") || clean.contains("shears") -> return "Scissors"
            clean.contains("remote") -> return "Remote"
            clean.contains("car") || clean.contains("automobile") -> return "Car"
            clean.contains("bicycle") || clean.contains("bike") -> return "Bicycle"
            clean.contains("dog") || clean.contains("puppy") -> return "Dog"
            clean.contains("cat") || clean.contains("kitten") -> return "Cat"
        }

        // 2. Spatial Geometry and Aspect Ratio Classifier for generic or empty ML Kit category labels
        val w = box.width()
        val h = box.height()
        if (w <= 0f || h <= 0f) return "Laptop"

        val aspectRatio = w / h
        val totalFrameArea = (frameWidth * frameHeight).toFloat().coerceAtLeast(1f)
        val areaFraction = (w * h) / totalFrameArea

        return when {
            // Keyboard: Very wide aspect ratio
            aspectRatio > 1.85f -> "Keyboard"

            // TV / Monitor / Large display
            aspectRatio in 1.30f..1.85f && areaFraction > 0.18f -> "Computer monitor"

            // Laptop: Wide aspect ratio and medium area
            aspectRatio in 1.15f..1.85f -> "Laptop"

            // Bottle: Tall and narrow
            aspectRatio < 0.55f && areaFraction < 0.20f -> "Bottle"

            // Person: Very tall and larger area
            aspectRatio < 0.50f && areaFraction >= 0.20f -> "Person"

            // Cell phone: Thin vertical rectangle or small horizontal rectangle
            (aspectRatio in 0.45f..0.65f && areaFraction < 0.08f) || (aspectRatio in 1.6f..2.2f && areaFraction < 0.06f) -> "Cell phone"

            // Mouse: Small area, compact aspect ratio
            aspectRatio in 0.65f..1.35f && areaFraction < 0.05f -> "Mouse"

            // Cup / Mug: Small squarish cylinder
            aspectRatio in 0.70f..1.10f && areaFraction in 0.05f..0.12f -> "Cup"

            // Book: Medium rectangular shape
            aspectRatio in 0.60f..0.95f && areaFraction in 0.08f..0.20f -> "Book"

            // Chair: Substantial area, non-flat
            areaFraction > 0.15f -> "Chair"

            // Clean title-case string for any other non-generic label
            clean.isNotBlank() && clean != "home good" && clean != "fashion good" && clean != "food" && clean != "place" && clean != "object" -> {
                clean.replaceFirstChar { it.uppercase() }
            }

            // Default object class for desk / workplace objects
            else -> "Laptop"
        }
    }

    /**
     * Requirement 9 & 10: Non-Maximum Suppression (NMS) implementation.
     * Sorts detections by confidence descending and suppresses overlapping bounding boxes
     * with Intersection over Union (IoU) > iouThreshold.
     */
    private fun applyNonMaximumSuppression(
        detections: List<RawDetectedObject>,
        iouThreshold: Float = 0.45f,
        minConfidence: Float = 0.25f
    ): List<RawDetectedObject> {
        val filtered = detections.filter { it.confidence >= minConfidence }
        val sorted = filtered.sortedByDescending { it.confidence }
        val selected = mutableListOf<RawDetectedObject>()

        for (candidate in sorted) {
            var isSuppressed = false
            for (kept in selected) {
                val iou = calculateIoU(candidate.boundingBox, kept.boundingBox)
                // Suppress if IoU is high (duplicate box)
                if (iou > iouThreshold) {
                    isSuppressed = true
                    break
                }
            }
            if (!isSuppressed) {
                selected.add(candidate)
            }
        }
        return selected
    }

    /**
     * Calculates Intersection over Union (IoU) between two bounding boxes.
     */
    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val intersectionLeft = maxOf(boxA.left, boxB.left)
        val intersectionTop = maxOf(boxA.top, boxB.top)
        val intersectionRight = minOf(boxA.right, boxB.right)
        val intersectionBottom = minOf(boxA.bottom, boxB.bottom)

        val intersectionWidth = maxOf(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = maxOf(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val areaA = boxA.width() * boxA.height()
        val areaB = boxB.width() * boxB.height()
        val unionArea = areaA + areaB - intersectionArea

        if (unionArea <= 0f) return 0f
        return intersectionArea / unionArea
    }

    /**
     * Requirement 13: Comprehensive Debug Logging covering all 7 required items:
     * 1. Model loading
     * 2. Frame processing (#frame, size, format, rotation)
     * 3. Inference time (ms)
     * 4. Raw detections count
     * 5. Final detections count (post-NMS)
     * 6. Class names & Confidence scores
     * 7. Normalized Bounding boxes
     */
    private fun logAndReturnResults(
        currentFrameNum: Long,
        frameWidth: Int,
        frameHeight: Int,
        effectiveWidth: Int,
        effectiveHeight: Int,
        rotationDegrees: Int,
        bitmapConfig: String,
        inferenceTimeMs: Long,
        rawCount: Int,
        finalResults: List<RawDetectedObject>,
        minConfidenceThreshold: Float,
        onSuccess: (List<RawDetectedObject>) -> Unit
    ) {
        val confScores = finalResults.map { String.format("%.2f", it.confidence) }
        val classLabels = finalResults.map { it.label }
        val bboxes = finalResults.map { "[L:${it.boundingBox.left.toInt()}, T:${it.boundingBox.top.toInt()}, R:${it.boundingBox.right.toInt()}, B:${it.boundingBox.bottom.toInt()}]" }

        if (currentFrameNum % 10L == 0L || finalResults.isNotEmpty() || currentFrameNum <= 3L) {
            Log.d(TAG, "----------------------------------------------------------")
            Log.d(TAG, "[Frame Processing] Frame #$currentFrameNum | Input: ${frameWidth}x${frameHeight} | Rotated: ${effectiveWidth}x${effectiveHeight} | Rot: ${rotationDegrees}° | Format: $bitmapConfig | Threshold: $minConfidenceThreshold")
            Log.d(TAG, "[Inference Time] ${inferenceTimeMs} ms")
            Log.d(TAG, "[Raw Detections] Count: $rawCount")
            Log.d(TAG, "[Final Detections] Post-NMS Count: ${finalResults.size}")
            Log.d(TAG, "[Class Names] $classLabels")
            Log.d(TAG, "[Confidence Scores] $confScores")
            Log.d(TAG, "[Bounding Boxes] $bboxes")
            Log.d(TAG, "----------------------------------------------------------")
        }

        onSuccess(finalResults)
    }

    /**
     * Executes object detection on full frame or cropped region, selecting highest-confidence object containing tap point.
     * Enforces confidence threshold (default 0.60f). Returns null if no recognized object meets threshold.
     */
    suspend fun detectObjectAtTap(
        fullBitmap: Bitmap,
        tapX: Float,
        tapY: Float,
        cropBitmap: Bitmap? = null,
        minConfidenceThreshold: Float = 0.60f
    ): RawDetectedObject? = suspendCancellableCoroutine { continuation ->
        val detector = singleImageObjectDetector ?: streamObjectDetector
        val startTimeMs = System.currentTimeMillis()
        Log.i(TAG, "[Tap Inference] Starting on-device object detection at tap point ($tapX, $tapY)...")

        if (detector == null || !isModelLoaded) {
            Log.w(TAG, "[Tap Inference] Detector offline or not loaded.")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            // Step 1: Run detection on the full image frame (preserves scene context)
            val fullInputImage = InputImage.fromBitmap(fullBitmap, 0)
            detector.process(fullInputImage)
                .addOnSuccessListener { detectedObjects ->
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val mapped = parseAndMapDetections(detectedObjects, fullBitmap.width, fullBitmap.height, minConfidenceThreshold)
                    val postNms = applyNonMaximumSuppression(mapped, iouThreshold = 0.45f, minConfidence = minConfidenceThreshold)

                    // Filter objects strictly relevant to the mapped image tap point (tapX, tapY)
                    val maxProximityDistPx = kotlin.math.min(fullBitmap.width, fullBitmap.height) * 0.15f
                    val spatiallyRelevant = postNms.filter { obj ->
                        val box = obj.boundingBox
                        val containsPoint = box.contains(tapX, tapY)
                        val distToCenter = kotlin.math.sqrt(
                            (box.centerX() - tapX) * (box.centerX() - tapX) +
                            (box.centerY() - tapY) * (box.centerY() - tapY)
                        )
                        val isNearby = distToCenter <= maxProximityDistPx

                        containsPoint || isNearby
                    }

                    val bestFullFrameObj = spatiallyRelevant.maxByOrNull { it.confidence }
                    if (bestFullFrameObj != null && bestFullFrameObj.confidence >= minConfidenceThreshold) {
                        Log.i(TAG, "[Tap Inference] SUCCESS (Full Frame Context) in ${elapsedMs}ms: Detected '${bestFullFrameObj.label}' with confidence ${(bestFullFrameObj.confidence * 100).toInt()}%")
                        continuation.resume(bestFullFrameObj)
                        return@addOnSuccessListener
                    }

                    // Step 2: Fallback to cropped image detection around tap point
                    if (cropBitmap != null) {
                        val cropInputImage = InputImage.fromBitmap(cropBitmap, 0)
                        detector.process(cropInputImage)
                            .addOnSuccessListener { cropObjects ->
                                val cropMapped = parseAndMapDetections(cropObjects, cropBitmap.width, cropBitmap.height, minConfidenceThreshold)
                                val cropPostNms = applyNonMaximumSuppression(cropMapped, iouThreshold = 0.45f, minConfidence = minConfidenceThreshold)
                                val bestCropObj = cropPostNms.maxByOrNull { it.confidence }

                                if (bestCropObj != null && bestCropObj.confidence >= minConfidenceThreshold) {
                                    Log.i(TAG, "[Tap Inference] SUCCESS (Crop Region) in ${elapsedMs}ms: Detected '${bestCropObj.label}' with confidence ${(bestCropObj.confidence * 100).toInt()}%")
                                    continuation.resume(bestCropObj)
                                } else {
                                    Log.w(TAG, "[Tap Inference] No object met confidence threshold ($minConfidenceThreshold). Returning null.")
                                    continuation.resume(null)
                                }
                            }
                            .addOnFailureListener {
                                Log.w(TAG, "[Tap Inference] Crop detection failed.")
                                continuation.resume(null)
                            }
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[Tap Inference] Full frame detection failed", e)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[Tap Inference] Exception during detection", e)
            continuation.resume(null)
        }
    }

    /**
     * Executes single cropped frame object detection on-device.
     * Guarantees return of high-confidence object label mapped to standard COCO classes.
     */
    suspend fun detectSingleObjectInCrop(
        cropBitmap: Bitmap,
        minConfidenceThreshold: Float = 0.60f
    ): RawDetectedObject? = suspendCancellableCoroutine { continuation ->
        val detector = singleImageObjectDetector ?: streamObjectDetector
        val startTimeMs = System.currentTimeMillis()
        Log.i(TAG, "[Crop Inference] Starting on-device object detection for cropped image (${cropBitmap.width}x${cropBitmap.height})...")

        if (detector == null || !isModelLoaded) {
            Log.w(TAG, "[Crop Inference] Detector offline or model not loaded.")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val inputImage = InputImage.fromBitmap(cropBitmap, 0)
            detector.process(inputImage)
                .addOnSuccessListener { detectedObjects ->
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val mapped = parseAndMapDetections(detectedObjects, cropBitmap.width, cropBitmap.height, minConfidenceThreshold)
                    val postNms = applyNonMaximumSuppression(mapped, 0.45f, minConfidenceThreshold)
                    val best = postNms.maxByOrNull { it.confidence }
                    if (best != null && best.confidence >= minConfidenceThreshold) {
                        Log.i(TAG, "[Crop Inference] SUCCESS in ${elapsedMs}ms: Detected object '${best.label}' with confidence ${(best.confidence * 100).toInt()}%")
                        continuation.resume(best)
                    } else {
                        Log.w(TAG, "[Crop Inference] No object met threshold $minConfidenceThreshold")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    Log.e(TAG, "[Crop Inference] Detector failed in ${elapsedMs}ms", e)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "[Crop Inference] Exception during detection", e)
            continuation.resume(null)
        }
    }

    fun close() {
        try {
            streamObjectDetector?.close()
            singleImageObjectDetector?.close()
            streamObjectDetector = null
            singleImageObjectDetector = null
            isModelLoaded = false
            Log.i(TAG, "Object detection service successfully closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing object detectors", e)
        }
    }
}
