package com.example.service

import android.graphics.RectF
import android.util.Log
import com.example.data.ArScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Data class representing an actively tracked AR object anchored to real screen space.
 */
data class TrackedArObject(
    val id: String,
    var labelEn: String,
    var currentBounds: RectF,
    var targetBounds: RectF,
    var confidence: Float,
    var translationResult: ArScanResult? = null,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    var alpha: Float = 1.0f,
    var isLocked: Boolean = false,
    val labelHistory: java.util.ArrayDeque<String> = java.util.ArrayDeque()
) {
    /**
     * Calculates center X position as percentage of screen width (0-100%).
     */
    fun getCenterXPercent(screenWidthPx: Float): Int {
        if (screenWidthPx <= 0f) return 50
        val cx = currentBounds.centerX()
        return ((cx / screenWidthPx) * 100f).toInt().coerceIn(5, 95)
    }

    /**
     * Calculates center Y position as percentage of screen height (0-100%).
     */
    fun getCenterYPercent(screenHeightPx: Float): Int {
        if (screenHeightPx <= 0f) return 50
        val cy = currentBounds.centerY()
        return ((cy / screenHeightPx) * 100f).toInt().coerceIn(5, 95)
    }
}

/**
 * Clean Architecture Service handling stable spatial object tracking, bounding box interpolation,
 * smooth animation without flickering, object lifecycle (add/remove), and spatial anchoring.
 * Requirement 11: Guarantees one physical object has ONE tracking ID and ONE overlay card.
 */
class ObjectTrackingService(
    private val translationService: TranslationService
) {
    private val TAG = "ObjectTrackingService"

    // Active tracked objects map keyed by unique tracking ID
    private val activeTrackedObjects = ConcurrentHashMap<String, TrackedArObject>()
    private var nextSyntheticId = 1

    /**
     * Returns the list of currently active tracked AR objects.
     */
    fun getActiveTrackedObjects(): List<TrackedArObject> {
        return activeTrackedObjects.values.toList()
    }

    /**
     * Updates spatial tracking state with newly detected raw objects from current camera frame.
     */
    suspend fun processFrameDetections(
        rawDetections: List<RawDetectedObject>,
        targetLanguage: String,
        frameWidth: Int,
        frameHeight: Int,
        screenWidthPx: Float,
        screenHeightPx: Float,
        rotationDegrees: Int = 90,
        scope: CoroutineScope
    ) = withContext(Dispatchers.Default) {
        val currentTime = System.currentTimeMillis()

        // Account for 90°/270° portrait rotation swap from CameraX frame to screen space
        val effectiveW = if (rotationDegrees == 90 || rotationDegrees == 270) frameHeight else frameWidth
        val effectiveH = if (rotationDegrees == 90 || rotationDegrees == 270) frameWidth else frameHeight

        // Normalize bounding box coordinates to match screen view aspect ratio
        val scaleX = if (effectiveW > 0) screenWidthPx / effectiveW.toFloat() else 1f
        val scaleY = if (effectiveH > 0) screenHeightPx / effectiveH.toFloat() else 1f

        val matchedExistingKeys = HashSet<String>()

        for (raw in rawDetections) {
            // Scale bounding box to screen coordinates with clamping to avoid off-screen artifacts
            val scaledRect = RectF(
                (raw.boundingBox.left * scaleX).coerceIn(0f, screenWidthPx),
                (raw.boundingBox.top * scaleY).coerceIn(0f, screenHeightPx),
                (raw.boundingBox.right * scaleX).coerceIn(0f, screenWidthPx),
                (raw.boundingBox.bottom * scaleY).coerceIn(0f, screenHeightPx)
            )

            // Requirement 11: Try spatial IoU & centroid matching with existing active tracked object
            val trackingKey = if (raw.trackingId != null && activeTrackedObjects.containsKey("ml_id_${raw.trackingId}")) {
                "ml_id_${raw.trackingId}"
            } else {
                findBestSpatialMatchKey(scaledRect, raw.label)
            }

            if (trackingKey != null && activeTrackedObjects.containsKey(trackingKey)) {
                val tracked = activeTrackedObjects[trackingKey]!!
                tracked.targetBounds = scaledRect
                tracked.confidence = raw.confidence
                tracked.lastSeenTimestamp = currentTime
                tracked.alpha = 1.0f
                matchedExistingKeys.add(trackingKey)

                if (!tracked.isLocked) {
                    // Update temporal label history (keep last 6)
                    synchronized(tracked.labelHistory) {
                        tracked.labelHistory.add(raw.label)
                        if (tracked.labelHistory.size > 6) {
                            tracked.labelHistory.removeFirst()
                        }

                        val counts = tracked.labelHistory.groupingBy { it }.eachCount()
                        val majorityLabel = counts.maxByOrNull { it.value }?.key ?: raw.label
                        val majorityCount = counts[majorityLabel] ?: 0

                        if (!raw.label.equals(tracked.labelEn, ignoreCase = true)) {
                            if (majorityLabel.equals(tracked.labelEn, ignoreCase = true) && majorityCount >= 2) {
                                Log.w(
                                    TAG,
                                    "[Tracking Outlier Suppressed] Tracked object '${tracked.id}': Suppressed single-frame '${raw.label}' " +
                                    "in favor of stable majority '${tracked.labelEn}' ($majorityCount/${tracked.labelHistory.size} frames)."
                                )
                            } else if (majorityCount >= 3) {
                                // Transition to new stable label after 3+ consistent frames
                                Log.i(TAG, "[Tracking Label Shift] Tracked object '${tracked.id}' shifted label: '${tracked.labelEn}' -> '$majorityLabel'")
                                tracked.labelEn = majorityLabel
                                scope.launch {
                                    val translation = translationService.translateObject(majorityLabel, targetLanguage, raw.confidence)
                                    activeTrackedObjects[trackingKey]?.let { obj ->
                                        obj.translationResult = translation.copy(
                                            xPosPercent = obj.getCenterXPercent(screenWidthPx),
                                            yPosPercent = obj.getCenterYPercent(screenHeightPx)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Create new tracked object with persistent unique ID
                val cleanLabelSlug = raw.label.lowercase().replace(" ", "_")
                val newId = trackingKey ?: "track_${cleanLabelSlug}_${nextSyntheticId++}"
                val newTracked = TrackedArObject(
                    id = newId,
                    labelEn = raw.label,
                    currentBounds = RectF(scaledRect),
                    targetBounds = scaledRect,
                    confidence = raw.confidence,
                    lastSeenTimestamp = currentTime,
                    alpha = 1.0f
                ).apply {
                    labelHistory.add(raw.label)
                }

                activeTrackedObjects[newId] = newTracked
                matchedExistingKeys.add(newId)

                // Fetch translation asynchronously in background without blocking frame tracking
                scope.launch {
                    val translation = translationService.translateObject(raw.label, targetLanguage, raw.confidence)
                    activeTrackedObjects[newId]?.let { obj ->
                        obj.translationResult = translation.copy(
                            xPosPercent = obj.getCenterXPercent(screenWidthPx),
                            yPosPercent = obj.getCenterYPercent(screenHeightPx)
                        )
                    }
                }
            }
        }

        // Cleanup & Smooth Exponential Interpolation (Lerp) loop
        val iterator = activeTrackedObjects.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val obj = entry.value
            val timeSinceSeen = currentTime - obj.lastSeenTimestamp

            if (timeSinceSeen > 800) {
                // Object has left view: fade out and remove overlay automatically
                obj.alpha -= 0.15f
                if (obj.alpha <= 0.0f || timeSinceSeen > 1200) {
                    iterator.remove()
                }
            } else {
                // Lerp bounding box coordinates to prevent jumpiness/flickering
                val lerpFactor = 0.30f
                obj.currentBounds.left += (obj.targetBounds.left - obj.currentBounds.left) * lerpFactor
                obj.currentBounds.top += (obj.targetBounds.top - obj.currentBounds.top) * lerpFactor
                obj.currentBounds.right += (obj.targetBounds.right - obj.currentBounds.right) * lerpFactor
                obj.currentBounds.bottom += (obj.targetBounds.bottom - obj.currentBounds.bottom) * lerpFactor

                // Update percentage coordinates for overlay placement
                obj.translationResult?.let { tr ->
                    obj.translationResult = tr.copy(
                        xPosPercent = obj.getCenterXPercent(screenWidthPx),
                        yPosPercent = obj.getCenterYPercent(screenHeightPx)
                    )
                }
            }
        }
    }

    /**
     * Requirement 11: Find best spatial match for an incoming box based on IoU and centroid distance.
     * Prevents duplicate ID creation for the same physical object in screen space.
     */
    private fun findBestSpatialMatchKey(rect: RectF, label: String): String? {
        var bestKey: String? = null
        var maxIoU = 0f
        var minDistance = Float.MAX_VALUE

        for ((key, obj) in activeTrackedObjects) {
            val iou = calculateIoU(obj.currentBounds, rect)
            val dx = obj.currentBounds.centerX() - rect.centerX()
            val dy = obj.currentBounds.centerY() - rect.centerY()
            val dist = abs(dx) + abs(dy)

            // If IoU is high or centroid distance is close, consider it the same physical object
            if (iou > 0.30f && iou > maxIoU) {
                maxIoU = iou
                bestKey = key
            } else if (maxIoU <= 0f && dist < 160f && dist < minDistance) {
                if (obj.labelEn.equals(label, ignoreCase = true) || obj.labelEn == "Unknown Object") {
                    minDistance = dist
                    bestKey = key
                }
            }
        }
        return bestKey
    }

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

    fun clear() {
        activeTrackedObjects.clear()
    }
}
