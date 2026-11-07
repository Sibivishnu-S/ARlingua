package com.example.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs

class OcrEngine(private val context: Context) {

    private val TAG = "OcrEngine"

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Executes primary ML Kit text recognition on bitmap.
     * Retries with ImagePreprocessor if confidence is low.
     * Falls back to Gemini Vision if ML Kit fails completely.
     */
    suspend fun recognizeDocumentText(
        bitmap: Bitmap,
        preprocessingMode: PreprocessingMode = PreprocessingMode.NORMAL,
        allowGeminiFallback: Boolean = true
    ): OcrDocument = withContext(Dispatchers.IO) {
        // Step 1: Quality check
        val qualityReport = ImagePreprocessor.analyzeQuality(bitmap)

        // Step 2: Apply preprocessing based on mode
        val processedBitmap = ImagePreprocessor.preprocessForOcr(bitmap, preprocessingMode)

        // Step 3: Run ML Kit OCR
        val mlKitResult = runMlKitOcr(processedBitmap)

        val ocrDocument = if (mlKitResult != null && mlKitResult.text.isNotBlank()) {
            parseMlKitTextToDocument(mlKitResult, qualityReport)
        } else {
            OcrDocument(qualityReport = qualityReport)
        }

        // Step 4: Validate OCR Quality & Fallback if necessary
        val isPoorOcr = ocrDocument.pages.isEmpty() ||
                ocrDocument.fullText.trim().length < 5 ||
                ocrDocument.pages.firstOrNull()?.averageConfidence ?: 1.0f < 0.35f

        if (isPoorOcr && allowGeminiFallback) {
            Log.w(TAG, "ML Kit OCR returned empty or low quality text. Initiating Gemini Vision OCR fallback...")
            val geminiOcrText = runGeminiVisionOcrFallback(bitmap)
            if (!geminiOcrText.isNullOrBlank()) {
                val validText = geminiOcrText!!
                val fallbackBlock = OcrBlock(text = validText, confidence = 0.9f)
                val fallbackPage = OcrPage(pageNumber = 1, blocks = listOf(fallbackBlock))
                val detectedLang = detectLanguageFromText(validText)
                return@withContext OcrDocument(
                    pages = listOf(fallbackPage),
                    detectedLanguageCode = detectedLang.first,
                    detectedLanguageName = detectedLang.second,
                    qualityReport = qualityReport
                )
            }
        }

        return@withContext ocrDocument
    }

    private fun String?.isNullInblank(): Boolean = this == null || this.isBlank()

    /**
     * Executes ML Kit TextRecognizer via Coroutine
     */
    private suspend fun runMlKitOcr(bitmap: Bitmap): Text? = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    continuation.resume(text)
                }
                .addOnFailureListener { ex ->
                    Log.e(TAG, "ML Kit recognition error", ex)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run ML Kit process", e)
            continuation.resume(null)
        }
    }

    /**
     * Converts ML Kit Text object to structured OcrDocument with reading order sorting
     */
    private fun parseMlKitTextToDocument(text: Text, qualityReport: DocumentQualityReport): OcrDocument {
        val rawBlocks = text.textBlocks

        // Sort blocks by logical reading order: Top-to-Bottom, then Left-to-Right
        val sortedBlocks = rawBlocks.sortedWith(Comparator { b1, b2 ->
            val box1 = b1.boundingBox ?: Rect()
            val box2 = b2.boundingBox ?: Rect()
            val yDiff = abs(box1.top - box2.top)
            if (yDiff < 30) {
                box1.left.compareTo(box2.left)
            } else {
                box1.top.compareTo(box2.top)
            }
        })

        val parsedBlocks = sortedBlocks.map { block ->
            val lines = block.lines.map { line ->
                OcrLine(
                    text = line.text,
                    boundingBox = line.boundingBox,
                    confidence = line.confidence ?: 0.9f
                )
            }
            OcrBlock(
                text = block.text,
                lines = lines,
                boundingBox = block.boundingBox,
                confidence = if (lines.isNotEmpty()) lines.map { it.confidence }.average().toFloat() else 0.85f
            )
        }

        val page = OcrPage(pageNumber = 1, blocks = parsedBlocks)
        val fullDocText = page.fullText
        val langInfo = detectLanguageFromText(fullDocText)

        return OcrDocument(
            pages = listOf(page),
            detectedLanguageCode = langInfo.first,
            detectedLanguageName = langInfo.second,
            isHandwritten = false,
            qualityReport = qualityReport
        )
    }

    /**
     * Lightweight script detector for unicode range identification (Tamil, Devanagari, Arabic, CJK, etc.)
     */
    private fun detectLanguageFromText(text: String): Pair<String, String> {
        var tamilCount = 0
        var devanagariCount = 0
        var arabicCount = 0
        var cjkCount = 0
        var latinCount = 0

        for (char in text) {
            val code = char.code
            when {
                code in 0x0B80..0x0BFF -> tamilCount++ // Tamil
                code in 0x0900..0x097F -> devanagariCount++ // Hindi/Marathi/Devanagari
                code in 0x0600..0x06FF -> arabicCount++ // Arabic
                code in 0x4E00..0x9FFF || code in 0x3040..0x30FF -> cjkCount++ // Chinese/Japanese
                code in 0x0041..0x007A -> latinCount++
            }
        }

        val totalSpecial = tamilCount + devanagariCount + arabicCount + cjkCount
        if (totalSpecial == 0) return Pair("en", "English")

        return when {
            tamilCount >= devanagariCount && tamilCount >= arabicCount && tamilCount >= cjkCount -> Pair("ta", "Tamil")
            devanagariCount >= arabicCount && devanagariCount >= cjkCount -> Pair("hi", "Hindi")
            arabicCount >= cjkCount -> Pair("ar", "Arabic")
            cjkCount > 0 -> Pair("zh", "Chinese/Japanese")
            else -> Pair("en", "English")
        }
    }

    /**
     * Gemini Vision Fallback for hard-to-read or handwritten documents
     */
    private suspend fun runGeminiVisionOcrFallback(bitmap: Bitmap): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key unavailable for OCR fallback")
            return null
        }

        return try {
            val base64Image = bitmapToBase64(bitmap)
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "You are a precise document OCR engine. Perform exact optical character recognition on this document image. Preserve reading order, headings, paragraphs, and list numbers. Do NOT translate or summarize. Return only the extracted text."),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Vision OCR fallback failed", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // Resize bitmap if very large to optimize bandwidth
        val maxDim = 1200
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap

        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun close() {
        try {
            textRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TextRecognizer", e)
        }
    }
}
