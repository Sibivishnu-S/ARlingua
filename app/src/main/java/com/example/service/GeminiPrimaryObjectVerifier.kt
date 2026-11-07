package com.example.service

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import com.example.data.GeminiObjectIdentificationResponse
import com.example.data.GeminiService
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class GeminiVerificationStatus {
    IDLE,
    CAPTURING_BURST,
    ANALYZING_QUALITY,
    GEMINI_PASS_1,
    GEMINI_PASS_2,
    GEMINI_PASS_3,
    CONFIRMED,
    UNCERTAIN,
    UNKNOWN,
    STALE_DISCARDED
}

data class FrameQualityMetrics(
    val frameIndex: Int,
    val bitmap: Bitmap,
    val brightness: Float,
    val contrastVariance: Float,
    val qualityScore: Float,
    val isQualityGood: Boolean
)

data class GeminiVerificationResult(
    val sessionId: String = "",
    val requestId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: GeminiVerificationStatus = GeminiVerificationStatus.IDLE,
    val tapXScreen: Float = 0f,
    val tapYScreen: Float = 0f,
    val cameraBoxWidthPx: Float = 0f,
    val cameraBoxHeightPx: Float = 0f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val imageRotation: Int = 0,
    val cropX: Int = 0,
    val cropY: Int = 0,
    val cropWidth: Int = 0,
    val cropHeight: Int = 0,
    val capturedFrameBitmap: Bitmap? = null,
    val cropBitmap: Bitmap? = null,
    val brightnessScore: Float = 0f,
    val blurScore: Float = 0f,
    val pass1Response: GeminiObjectIdentificationResponse? = null,
    val pass2Response: GeminiObjectIdentificationResponse? = null,
    val pass3Response: GeminiObjectIdentificationResponse? = null,
    val finalConfirmedObject: String? = null,
    val disambiguationCandidates: List<String> = emptyList(),
    val userGuidanceMessage: String? = null
)

/**
 * Primary Object Verification Engine using Gemini Vision as the single source of truth.
 *
 * Implements:
 * 1. Multi-frame quality burst capture (3-5 frames over 300-700ms).
 * 2. Explicit screen-to-image tap coordinate transformation.
 * 3. Crop extraction around target with 15-20% padding.
 * 4. Double independent Gemini Vision analysis passes without confirmation bias.
 * 5. Third majority tie-breaker pass for ambiguous cases.
 * 6. Explicit UNKNOWN / UNCERTAIN state handling with actionable user guidance.
 * 7. Session ID & Request ID validation to discard stale async requests.
 */
class GeminiPrimaryObjectVerifier(
    private val geminiService: GeminiService,
    private val cameraService: CameraService,
    private val objectDetectionService: ObjectDetectionService
) {
    companion object {
        private const val TAG = "GeminiPrimaryVerifier"
        private const val BURST_FRAME_COUNT = 4
        private const val BURST_INTERVAL_MS = 100L
    }

    private var activeSessionId: String = ""
    private var activeRequestId: String = ""

    /** Generates new Session ID and Request ID token */
    fun createSessionToken(): Pair<String, String> {
        val session = "sess_${System.currentTimeMillis()}"
        val request = "req_${(1000..9999).random()}"
        activeSessionId = session
        activeRequestId = request
        return Pair(session, request)
    }

    /** Validates if current execution matches the latest active session */
    fun isSessionValid(sessionId: String, requestId: String): Boolean {
        return sessionId == activeSessionId && requestId == activeRequestId
    }

    /**
     * Executes complete multi-stage primary object verification using Gemini Vision.
     */
    suspend fun verifyObjectWithGemini(
        sessionId: String,
        requestId: String,
        tapXScreen: Float,
        tapYScreen: Float,
        cameraBoxWidthPx: Float,
        cameraBoxHeightPx: Float,
        targetLanguage: String,
        captureFrameSupplier: suspend () -> FrameHolderData?,
        onProgressUpdate: (GeminiVerificationResult) -> Unit
    ): GeminiVerificationResult {

        if (!isSessionValid(sessionId, requestId)) {
            Log.w(TAG, "[Stale Check] Request $requestId for Session $sessionId is stale. Discarding.")
            return GeminiVerificationResult(
                sessionId = sessionId,
                requestId = requestId,
                status = GeminiVerificationStatus.STALE_DISCARDED
            )
        }

        var resultState = GeminiVerificationResult(
            sessionId = sessionId,
            requestId = requestId,
            status = GeminiVerificationStatus.CAPTURING_BURST,
            tapXScreen = tapXScreen,
            tapYScreen = tapYScreen,
            cameraBoxWidthPx = cameraBoxWidthPx,
            cameraBoxHeightPx = cameraBoxHeightPx
        )
        onProgressUpdate(resultState)

        // --- STEP 1: Multi-Frame Capture (3-5 frames over 300-500ms) ---
        val capturedFrames = mutableListOf<FrameHolderData>()
        for (i in 0 until BURST_FRAME_COUNT) {
            val frame = captureFrameSupplier()
            if (frame != null && frame.bitmap != null) {
                capturedFrames.add(frame)
            }
            delay(BURST_INTERVAL_MS)
        }

        if (capturedFrames.isEmpty()) {
            val failed = resultState.copy(
                status = GeminiVerificationStatus.UNKNOWN,
                userGuidanceMessage = "Camera frame capture failed. Please try again."
            )
            onProgressUpdate(failed)
            return failed
        }

        // --- STEP 2: Frame Quality Evaluation & Selection ---
        resultState = resultState.copy(status = GeminiVerificationStatus.ANALYZING_QUALITY)
        onProgressUpdate(resultState)

        val evaluatedFrames = capturedFrames.mapIndexed { idx, frame ->
            evaluateFrameQuality(idx, frame.bitmap!!, frame.rotationDegrees)
        }

        val bestFrameQuality = evaluatedFrames.maxByOrNull { it.qualityScore } ?: evaluatedFrames.first()
        val rawSelectedBitmap = bestFrameQuality.bitmap

        // Rotate selected frame bitmap
        val matrix = Matrix().apply { postRotate(capturedFrames.first().rotationDegrees.toFloat()) }
        val rotatedSelectedBitmap = Bitmap.createBitmap(
            rawSelectedBitmap, 0, 0, rawSelectedBitmap.width, rawSelectedBitmap.height, matrix, true
        )

        val imageWidth = rotatedSelectedBitmap.width
        val imageHeight = rotatedSelectedBitmap.height
        val rotationDeg = capturedFrames.first().rotationDegrees

        // --- STEP 3: Screen-to-Image Coordinate Transformation & Target Crop ---
        val mapper = ScreenToImageCoordinateMapper(
            cameraBoxWidthPx = cameraBoxWidthPx,
            cameraBoxHeightPx = cameraBoxHeightPx,
            imageWidthPx = imageWidth.toFloat(),
            imageHeightPx = imageHeight.toFloat()
        )

        val imageTapPoint = mapper.mapScreenToImagePixel(tapXScreen, tapYScreen)
        val imageTapX = imageTapPoint.x
        val imageTapY = imageTapPoint.y

        // Try to obtain bounding box suggestion from local ARVision detector
        val localDetection = objectDetectionService.detectObjectAtTap(
            fullBitmap = rotatedSelectedBitmap,
            tapX = imageTapX,
            tapY = imageTapY
        )

        val targetCropRect: RectF = if (localDetection != null && localDetection.boundingBox.width() > 30f) {
            // Add 15-20% padding to local bounding box
            val box = localDetection.boundingBox
            val padX = box.width() * 0.18f
            val padY = box.height() * 0.18f
            RectF(
                (box.left - padX).coerceAtLeast(0f),
                (box.top - padY).coerceAtLeast(0f),
                (box.right + padX).coerceAtMost(imageWidth.toFloat()),
                (box.bottom + padY).coerceAtMost(imageHeight.toFloat())
            )
        } else {
            // Default 360x360 crop centered at image tap point
            val cropSize = min(360f, min(imageWidth.toFloat(), imageHeight.toFloat()))
            val half = cropSize / 2f
            RectF(
                (imageTapX - half).coerceAtLeast(0f),
                (imageTapY - half).coerceAtLeast(0f),
                (imageTapX + half).coerceAtMost(imageWidth.toFloat()),
                (imageTapY + half).coerceAtMost(imageHeight.toFloat())
            )
        }

        val cropXInt = targetCropRect.left.toInt()
        val cropYInt = targetCropRect.top.toInt()
        val cropWidthInt = max(1, targetCropRect.width().toInt())
        val cropHeightInt = max(1, targetCropRect.height().toInt())

        val cropBitmap = Bitmap.createBitmap(
            rotatedSelectedBitmap,
            cropXInt,
            cropYInt,
            cropWidthInt,
            cropHeightInt
        )

        val base64Crop = cameraService.bitmapToBase64(cropBitmap, quality = 88)

        resultState = resultState.copy(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            imageRotation = rotationDeg,
            cropX = cropXInt,
            cropY = cropYInt,
            cropWidth = cropWidthInt,
            cropHeight = cropHeightInt,
            capturedFrameBitmap = rotatedSelectedBitmap,
            cropBitmap = cropBitmap,
            brightnessScore = bestFrameQuality.brightness,
            blurScore = bestFrameQuality.contrastVariance
        )
        onProgressUpdate(resultState)

        // Quality Gate 1: Check if crop region is too small
        if (cropWidthInt < 40 || cropHeightInt < 40) {
            val guidance = "Move closer to the object."
            val qualityFail = resultState.copy(
                status = GeminiVerificationStatus.UNKNOWN,
                userGuidanceMessage = guidance
            )
            onProgressUpdate(qualityFail)
            return qualityFail
        }

        // Quality Gate 2: Check lighting / brightness
        if (bestFrameQuality.brightness < 30f) {
            val guidance = "Improve lighting. The scene is too dark."
            val qualityFail = resultState.copy(
                status = GeminiVerificationStatus.UNKNOWN,
                userGuidanceMessage = guidance
            )
            onProgressUpdate(qualityFail)
            return qualityFail
        }

        // Quality Gate 3: Check blur / motion instability
        if (bestFrameQuality.contrastVariance < 120f) {
            val guidance = "Hold camera steady. Frame is blurry."
            val qualityFail = resultState.copy(
                status = GeminiVerificationStatus.UNKNOWN,
                userGuidanceMessage = guidance
            )
            onProgressUpdate(qualityFail)
            return qualityFail
        }

        // Validate session state before API calls
        if (!isSessionValid(sessionId, requestId)) {
            Log.w(TAG, "[Stale Check] Aborting API calls for stale session $sessionId")
            return resultState.copy(status = GeminiVerificationStatus.STALE_DISCARDED)
        }

        // --- STEP 4: FIRST INDEPENDENT GEMINI VISION PASS ---
        resultState = resultState.copy(status = GeminiVerificationStatus.GEMINI_PASS_1)
        onProgressUpdate(resultState)

        val pass1Response = geminiService.identifyObjectWithGeminiVisionPass(
            base64CropImage = base64Crop,
            passNumber = 1,
            targetLanguage = targetLanguage
        )

        resultState = resultState.copy(pass1Response = pass1Response)
        onProgressUpdate(resultState)

        if (!isSessionValid(sessionId, requestId)) {
            return resultState.copy(status = GeminiVerificationStatus.STALE_DISCARDED)
        }

        // --- STEP 5: SECOND INDEPENDENT GEMINI VISION PASS ---
        resultState = resultState.copy(status = GeminiVerificationStatus.GEMINI_PASS_2)
        onProgressUpdate(resultState)

        val pass2Response = geminiService.identifyObjectWithGeminiVisionPass(
            base64CropImage = base64Crop,
            passNumber = 2,
            targetLanguage = targetLanguage
        )

        resultState = resultState.copy(pass2Response = pass2Response)
        onProgressUpdate(resultState)

        if (!isSessionValid(sessionId, requestId)) {
            return resultState.copy(status = GeminiVerificationStatus.STALE_DISCARDED)
        }

        // --- STEP 6: EVALUATE CONSENSUS & TIE-BREAKER PASS IF AMBIGUOUS ---
        val pass1Label = pass1Response.objectName.trim()
        val pass2Label = pass2Response.objectName.trim()

        val pass1StatusConfirmed = pass1Response.status.equals("CONFIRMED", ignoreCase = true)
        val pass2StatusConfirmed = pass2Response.status.equals("CONFIRMED", ignoreCase = true)

        val areSynonymsOrEqual = isSameOrSynonymObject(pass1Label, pass2Label)

        var finalConfirmedLabel: String? = null
        var finalStatus = GeminiVerificationStatus.UNKNOWN
        var pass3Response: GeminiObjectIdentificationResponse? = null
        val disambiguationOptions = mutableListOf<String>()

        if (pass1StatusConfirmed && pass2StatusConfirmed && areSynonymsOrEqual) {
            // Perfect Consensus between Pass 1 and Pass 2
            finalConfirmedLabel = getPreferredObjectLabel(pass1Label, pass2Label)
            finalStatus = GeminiVerificationStatus.CONFIRMED
        } else {
            // Disagreement or UNCERTAIN state -> Run Pass 3 Tie-Breaker
            resultState = resultState.copy(status = GeminiVerificationStatus.GEMINI_PASS_3)
            onProgressUpdate(resultState)

            pass3Response = geminiService.identifyObjectWithGeminiVisionPass(
                base64CropImage = base64Crop,
                passNumber = 3,
                targetLanguage = targetLanguage
            )

            resultState = resultState.copy(pass3Response = pass3Response)

            val pass3Label = pass3Response.objectName.trim()
            val pass3StatusConfirmed = pass3Response.status.equals("CONFIRMED", ignoreCase = true)

            // Evaluate 2/3 Majority Consensus
            val p1_p2 = areSynonymsOrEqual && pass1StatusConfirmed && pass2StatusConfirmed
            val p1_p3 = isSameOrSynonymObject(pass1Label, pass3Label) && pass1StatusConfirmed && pass3StatusConfirmed
            val p2_p3 = isSameOrSynonymObject(pass2Label, pass3Label) && pass2StatusConfirmed && pass3StatusConfirmed

            when {
                p1_p2 -> {
                    finalConfirmedLabel = getPreferredObjectLabel(pass1Label, pass2Label)
                    finalStatus = GeminiVerificationStatus.CONFIRMED
                }
                p1_p3 -> {
                    finalConfirmedLabel = getPreferredObjectLabel(pass1Label, pass3Label)
                    finalStatus = GeminiVerificationStatus.CONFIRMED
                }
                p2_p3 -> {
                    finalConfirmedLabel = getPreferredObjectLabel(pass2Label, pass3Label)
                    finalStatus = GeminiVerificationStatus.CONFIRMED
                }
                else -> {
                    finalConfirmedLabel = null
                    finalStatus = GeminiVerificationStatus.UNKNOWN

                    // Collect plausible candidates for user disambiguation
                    val candidatePool = listOf(pass1Label, pass2Label, pass3Label) +
                            pass1Response.alternatives + pass2Response.alternatives
                    val validCandidates = candidatePool
                        .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                        .distinct()
                    if (validCandidates.size in 2..3) {
                        disambiguationOptions.addAll(validCandidates)
                    }
                }
            }
        }

        val userGuidance = if (finalStatus == GeminiVerificationStatus.CONFIRMED) {
            null
        } else if (disambiguationOptions.isNotEmpty()) {
            "Multiple potential objects detected. Please select the intended object."
        } else {
            "Object could not be identified clearly. Try moving closer, improving lighting, or changing camera angle."
        }

        val finalResult = resultState.copy(
            status = finalStatus,
            pass3Response = pass3Response,
            finalConfirmedObject = finalConfirmedLabel,
            disambiguationCandidates = disambiguationOptions,
            userGuidanceMessage = userGuidance
        )

        onProgressUpdate(finalResult)
        return finalResult
    }

    /**
     * Evaluates brightness and sharpness variance of a bitmap frame.
     */
    private fun evaluateFrameQuality(frameIdx: Int, bitmap: Bitmap, rotationDeg: Int): FrameQualityMetrics {
        val sampleSize = min(150, min(bitmap.width, bitmap.height))
        val sampleBitmap = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)

        var totalLuminance = 0L
        val pixelCount = sampleSize * sampleSize
        val luminances = IntArray(pixelCount)

        var idx = 0
        for (y in 0 until sampleSize) {
            for (x in 0 until sampleSize) {
                val p = sampleBitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                luminances[idx++] = lum
                totalLuminance += lum
            }
        }

        val avgBrightness = totalLuminance.toFloat() / pixelCount

        var sumSqDiff = 0.0
        for (lum in luminances) {
            val diff = lum - avgBrightness
            sumSqDiff += diff * diff
        }
        val contrastVariance = (sumSqDiff / pixelCount).toFloat()

        val brightnessScore = when {
            avgBrightness in 70f..210f -> 1.0f
            avgBrightness in 40f..235f -> 0.7f
            else -> 0.3f
        }

        val sharpnessScore = (contrastVariance / 1800f).coerceIn(0.1f, 1.0f)
        val qualityScore = (0.4f * brightnessScore) + (0.6f * sharpnessScore)

        return FrameQualityMetrics(
            frameIndex = frameIdx,
            bitmap = bitmap,
            brightness = avgBrightness,
            contrastVariance = contrastVariance,
            qualityScore = qualityScore,
            isQualityGood = qualityScore >= 0.5f
        )
    }

    /**
     * Checks if two object labels represent the same concept or known synonyms.
     */
    private fun isSameOrSynonymObject(label1: String, label2: String): Boolean {
        val l1 = label1.lowercase(Locale.ROOT).trim()
        val l2 = label2.lowercase(Locale.ROOT).trim()

        if (l1 == l2) return true
        if (l1.contains(l2) || l2.contains(l1)) return true

        val synonyms = mapOf(
            "smartphone" to setOf("phone", "cell phone", "mobile phone", "telephone"),
            "laptop" to setOf("computer", "notebook", "portable computer"),
            "coffee mug" to setOf("mug", "cup", "tea cup", "coffee cup"),
            "water bottle" to setOf("bottle", "flask", "drink bottle"),
            "keyboard" to setOf("computer keyboard", "keypad"),
            "mouse" to setOf("computer mouse", "trackpad"),
            "chair" to setOf("armchair", "seat", "office chair")
        )

        for ((key, synSet) in synonyms) {
            val group = synSet + key
            if (l1 in group && l2 in group) return true
        }

        return false
    }

    private fun getPreferredObjectLabel(l1: String, l2: String): String {
        return if (l1.length >= l2.length) l1 else l2
    }
}
data class FrameHolderData(
    val bitmap: Bitmap?,
    val rotationDegrees: Int,
    val frameWidth: Int,
    val frameHeight: Int
)
