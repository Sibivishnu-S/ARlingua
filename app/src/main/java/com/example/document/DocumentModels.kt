package com.example.document

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri

/**
 * Data structures for document processing, OCR, translation, and PDF generation.
 */

data class OcrLine(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float = 1.0f
)

data class OcrBlock(
    val text: String,
    val lines: List<OcrLine> = emptyList(),
    val boundingBox: Rect? = null,
    val confidence: Float = 1.0f
)

data class OcrPage(
    val pageNumber: Int,
    val blocks: List<OcrBlock> = emptyList(),
    val fullText: String = blocks.joinToString("\n\n") { it.text },
    val averageConfidence: Float = if (blocks.isNotEmpty()) blocks.map { it.confidence }.average().toFloat() else 0f
)

data class OcrDocument(
    val pages: List<OcrPage> = emptyList(),
    val detectedLanguageCode: String = "auto",
    val detectedLanguageName: String = "Auto-detected",
    val isHandwritten: Boolean = false,
    val qualityReport: DocumentQualityReport = DocumentQualityReport()
) {
    val fullText: String
        get() = pages.joinToString("\n\n--- Page Break ---\n\n") { it.fullText }
}

enum class PreprocessingMode {
    NORMAL,
    LOW_LIGHT,
    LOW_CONTRAST,
    SHADOWED,
    HANDWRITTEN
}

data class DocumentQualityReport(
    val isLowLight: Boolean = false,
    val isBlurry: Boolean = false,
    val isLowContrast: Boolean = false,
    val averageLuminance: Float = 128f,
    val blurScore: Float = 100f,
    val contrastScore: Float = 50f,
    val overallQualityScore: Float = 1.0f,
    val warningMessage: String? = null
)

enum class DocumentProcessingStage {
    IDLE,
    CAPTURING,
    PROCESSING_IMAGE,
    OCR_PROCESSING,
    OCR_COMPLETE,
    TRANSLATING,
    TRANSLATION_COMPLETE,
    GENERATING_PDF,
    PDF_COMPLETE,
    ERROR
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val chunksCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

data class GeneratedPdfInfo(
    val fileUri: Uri,
    val filePath: String,
    val fileName: String,
    val title: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val pageCount: Int,
    val fileSizeFormatted: String
)

data class DocumentLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String = displayName,
    val scriptName: String = "Latin"
)
