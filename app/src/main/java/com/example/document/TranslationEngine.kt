package com.example.document

import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationEngine {

    private val TAG = "TranslationEngine"

    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            DocumentLanguage("ta", "Tamil", "தமிழ்", "Tamil"),
            DocumentLanguage("hi", "Hindi", "हिन्दी", "Devanagari"),
            DocumentLanguage("en", "English", "English", "Latin"),
            DocumentLanguage("es", "Spanish", "Español", "Latin"),
            DocumentLanguage("fr", "French", "Français", "Latin"),
            DocumentLanguage("de", "German", "Deutsch", "Latin"),
            DocumentLanguage("te", "Telugu", "తెలుగు", "Telugu"),
            DocumentLanguage("ml", "Malayalam", "മലയാളം", "Malayalam"),
            DocumentLanguage("kn", "Kannada", "கன்னட", "Kannada"),
            DocumentLanguage("bn", "Bengali", "বাংলা", "Bengali"),
            DocumentLanguage("mr", "Marathi", "मराठी", "Devanagari"),
            DocumentLanguage("ar", "Arabic", "العربية", "Arabic"),
            DocumentLanguage("zh", "Chinese", "中文", "Hanzi"),
            DocumentLanguage("ja", "Japanese", "日本語", "Kanji/Kana"),
            DocumentLanguage("ko", "Korean", "한국어", "Hangul"),
            DocumentLanguage("pt", "Portuguese", "Português", "Latin"),
            DocumentLanguage("it", "Italian", "Italiano", "Latin"),
            DocumentLanguage("ru", "Russian", "Русский", "Cyrillic")
        )

        fun getLanguageNameByCode(code: String): String {
            return SUPPORTED_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }?.displayName ?: code
        }
    }

    /**
     * Translates document text faithfully into the target language using Gemini.
     * Supports document chunking for long texts.
     */
    suspend fun translateDocument(
        originalText: String,
        sourceLanguage: String,
        targetLanguage: String,
        onProgressUpdate: (Int, Int) -> Unit = { _, _ -> }
    ): TranslationResult = withContext(Dispatchers.IO) {

        if (originalText.isBlank()) {
            return@withContext TranslationResult(
                originalText = originalText,
                translatedText = "",
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key missing")
            throw IllegalStateException("Gemini API key is not configured. Please add your key in Settings.")
        }

        // Chunk text if longer than 3500 characters
        val chunks = chunkTextByParagraphs(originalText, maxChunkLength = 3500)
        val translatedChunks = mutableListOf<String>()

        for ((index, chunk) in chunks.withIndex()) {
            onProgressUpdate(index + 1, chunks.size)
            val translatedChunk = translateSingleChunk(
                chunkText = chunk,
                sourceLang = sourceLanguage,
                targetLang = targetLanguage,
                apiKey = apiKey
            )
            translatedChunks.add(translatedChunk)
        }

        val fullTranslation = translatedChunks.joinToString("\n\n")

        return@withContext TranslationResult(
            originalText = originalText,
            translatedText = fullTranslation,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            chunksCount = chunks.size
        )
    }

    /**
     * Translates a single text chunk with strict faithful translation prompt instructions
     */
    private suspend fun translateSingleChunk(
        chunkText: String,
        sourceLang: String,
        targetLang: String,
        apiKey: String
    ): String {
        val systemInstruction = """
            You are a professional document translator.
            Translate the provided text from the source language ($sourceLang) to the target language ($targetLang).

            CRITICAL REQUIREMENTS:
            1. Translate faithfully and accurately.
            2. Do NOT summarize or condense the document.
            3. Do NOT add commentary, introductions, or explanations.
            4. Do NOT remove any information.
            5. Preserve paragraph breaks, line breaks, headings, bullet points, numbers, dates, and names.
            6. Preserve technical terms, URLs, and symbols appropriately.
            7. Return ONLY the translated text itself.
        """.trimIndent()

        val prompt = "Source Text:\n$chunkText"

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "$systemInstruction\n\n$prompt")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2f // Low temperature for high translation accuracy and zero hallucination
            )
        )

        try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (candidateText.isNullOrBlank()) {
                throw IllegalStateException("Empty translation candidate received from Gemini")
            }
            return candidateText
        } catch (e: Exception) {
            Log.e(TAG, "Gemini translation request failed for chunk", e)
            throw e
        }
    }

    /**
     * Chunks long text safely at paragraph boundaries
     */
    private fun chunkTextByParagraphs(text: String, maxChunkLength: Int): List<String> {
        if (text.length <= maxChunkLength) return listOf(text)

        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > maxChunkLength) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }

                if (paragraph.length > maxChunkLength) {
                    // Force line split for huge paragraphs
                    val lineSplits = paragraph.split("\n")
                    for (line in lineSplits) {
                        if (currentChunk.length + line.length + 1 > maxChunkLength) {
                            if (currentChunk.isNotEmpty()) {
                                chunks.add(currentChunk.toString().trim())
                                currentChunk = StringBuilder()
                            }
                        }
                        currentChunk.append(line).append("\n")
                    }
                } else {
                    currentChunk.append(paragraph).append("\n\n")
                }
            } else {
                currentChunk.append(paragraph).append("\n\n")
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.ifEmpty { listOf(text) }
    }
}
