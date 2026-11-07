package com.example.service

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.ArScanResult
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.GenerationConfig
import com.example.data.Part
import com.example.data.GeminiRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Data wrapper class for batch MLKit label translation responses from Gemini API.
 */
data class BatchLabelTranslationResult(
    val results: List<ArScanResult> = emptyList()
)

/**
 * MlKitGeminiTranslationService:
 * Clean Architecture service that receives detected object labels from the MLKit vision pipeline
 * and queries the Google Gemini API to produce high-accuracy, context-rich translations in the user's
 * chosen target language.
 *
 * Features:
 * - Real-time Gemini API translation for single and batch MLKit detected object labels.
 * - Thread-safe in-memory caching to eliminate redundant API network calls for previously scanned labels.
 * - Smart prompt engineering tailored for AR language learning (IPA phonetics, example sentences, grammar notes, difficulty).
 * - Instant offline local dictionary fallback when network or Gemini API keys are unconfigured.
 * - High-speed JSON schema parsing via Moshi and Retrofit.
 */
class MlKitGeminiTranslationService(private val context: Context) {

    private val TAG = "MlKitGeminiTranslation"

    // Thread-safe in-memory cache keyed by "label_targetLanguage"
    private val translationCache = ConcurrentHashMap<String, ArScanResult>()

    /**
     * Translates a single MLKit detected object label into the user's target language using Gemini API.
     */
    suspend fun translateMlKitLabel(
        rawObject: RawDetectedObject,
        targetLanguage: String
    ): ArScanResult = translateLabelString(
        englishLabel = rawObject.label,
        targetLanguage = targetLanguage,
        confidence = rawObject.confidence
    )

    /**
     * Translates a single label string into the target language using Gemini API.
     */
    suspend fun translateLabelString(
        englishLabel: String,
        targetLanguage: String,
        confidence: Float = 0.95f
    ): ArScanResult = withContext(Dispatchers.IO) {
        val cleanLabel = englishLabel.trim()
        if (cleanLabel.isBlank()) {
            return@withContext createFallbackResult("Unknown Object", targetLanguage, confidence)
        }

        val cacheKey = "${cleanLabel.lowercase()}_${targetLanguage.lowercase()}"

        // 1. Check in-memory cache
        translationCache[cacheKey]?.let { cached ->
            Log.d(TAG, "[Cache Hit] Returning cached Gemini translation for '$cacheKey'")
            return@withContext cached.copy(confidence = confidence)
        }

        // 2. Check local instant dictionary fallback
        val localMatch = getLocalDictionaryFallback(cleanLabel, targetLanguage, confidence)
        if (localMatch != null) {
            translationCache[cacheKey] = localMatch
        }

        // 3. Attempt Gemini API Translation
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "[Gemini API] API Key not set. Using local dictionary fallback for '$cleanLabel'")
            val fallback = localMatch ?: createFallbackResult(cleanLabel, targetLanguage, confidence)
            translationCache[cacheKey] = fallback
            return@withContext fallback
        }

        try {
            Log.i(TAG, "[Gemini API Request] Translating MLKit label '$cleanLabel' -> '$targetLanguage'")

            val prompt = """
                You are a real-time AR language tutor. An on-device MLKit object detector identified the physical object label "$cleanLabel".
                Translate this object name into $targetLanguage.
                
                Return JSON matching this exact structure:
                {
                  "objectNameEn": "$cleanLabel",
                  "objectNameTarget": "target translation in $targetLanguage",
                  "pronunciationIpa": "IPA phonetic guide",
                  "exampleSentenceTarget": "a short, natural learning sentence in $targetLanguage using this object name",
                  "exampleSentenceEn": "English translation of the example sentence",
                  "grammarUsage": "concise grammar usage or gender note (e.g. Masculine noun, Feminine noun, Katakana loanword)",
                  "category": "Workplace / Furniture / Kitchen / Electronics / Animals / General",
                  "difficulty": "Beginner"
                }
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!jsonText.isNull_or_blank()) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(ArScanResult::class.java)
                val result = adapter.fromJson(jsonText)
                if (result != null && result.objectNameTarget.isNotBlank()) {
                    val finalResult = result.copy(confidence = confidence)
                    translationCache[cacheKey] = finalResult
                    Log.i(TAG, "[Gemini API Success] Successfully translated '$cleanLabel' -> '${finalResult.objectNameTarget}'")
                    return@withContext finalResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini API Error] Failed translating label '$cleanLabel': ${e.message}", e)
        }

        val fallback = localMatch ?: createFallbackResult(cleanLabel, targetLanguage, confidence)
        translationCache[cacheKey] = fallback
        return@withContext fallback
    }

    /**
     * Helper extension for string null or blank check
     */
    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    /**
     * Batch translates a list of MLKit detected object labels in a single Gemini API call for efficiency.
     */
    suspend fun translateLabelBatch(
        englishLabels: List<String>,
        targetLanguage: String
    ): List<ArScanResult> = withContext(Dispatchers.IO) {
        val uniqueLabels = englishLabels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueLabels.isEmpty()) return@withContext emptyList()

        val resultsMap = mutableMapOf<String, ArScanResult>()
        val uncachedLabels = mutableListOf<String>()

        // Check cache first for all labels
        for (label in uniqueLabels) {
            val cacheKey = "${label.lowercase()}_${targetLanguage.lowercase()}"
            val cached = translationCache[cacheKey]
            if (cached != null) {
                resultsMap[label] = cached
            } else {
                uncachedLabels.add(label)
            }
        }

        if (uncachedLabels.isEmpty()) {
            return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            for (label in uncachedLabels) {
                val fallback = getLocalDictionaryFallback(label, targetLanguage, 0.95f)
                    ?: createFallbackResult(label, targetLanguage, 0.95f)
                val cacheKey = "${label.lowercase()}_${targetLanguage.lowercase()}"
                translationCache[cacheKey] = fallback
                resultsMap[label] = fallback
            }
            return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
        }

        try {
            Log.i(TAG, "[Gemini Batch API] Translating ${uncachedLabels.size} labels to $targetLanguage: $uncachedLabels")

            val prompt = """
                You are an AR object detection translation engine. Translate these MLKit detected object labels into $targetLanguage:
                ${uncachedLabels.joinToString(", ")}

                Return strictly a JSON array wrapper matching this schema:
                {
                  "results": [
                    {
                      "objectNameEn": "Laptop",
                      "objectNameTarget": "target translation in $targetLanguage",
                      "pronunciationIpa": "IPA guide",
                      "exampleSentenceTarget": "example sentence in $targetLanguage",
                      "exampleSentenceEn": "English translation of example sentence",
                      "grammarUsage": "grammar note",
                      "category": "Electronics",
                      "difficulty": "Beginner"
                    }
                  ]
                }
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!jsonText.isNull_or_blank()) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(BatchLabelTranslationResult::class.java)
                val batchWrapper = adapter.fromJson(jsonText)
                batchWrapper?.results?.forEach { item ->
                    val key = item.objectNameEn.trim()
                    val cacheKey = "${key.lowercase()}_${targetLanguage.lowercase()}"
                    translationCache[cacheKey] = item
                    resultsMap[key] = item
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini Batch API Error] Exception during batch translation: ${e.message}", e)
        }

        // Fill remaining missing items with local dictionary or fallback
        for (label in uncachedLabels) {
            if (!resultsMap.containsKey(label)) {
                val fallback = getLocalDictionaryFallback(label, targetLanguage, 0.95f)
                    ?: createFallbackResult(label, targetLanguage, 0.95f)
                val cacheKey = "${label.lowercase()}_${targetLanguage.lowercase()}"
                translationCache[cacheKey] = fallback
                resultsMap[label] = fallback
            }
        }

        return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
    }

    /**
     * Local instant dictionary lookup for common COCO object classes.
     */
    private fun getLocalDictionaryFallback(
        label: String,
        targetLanguage: String,
        confidence: Float
    ): ArScanResult? {
        val key = label.lowercase().trim()
        val cap = label.replaceFirstChar { it.uppercase() }

        return when (targetLanguage) {
            "Spanish" -> when {
                key.contains("laptop") || key.contains("computer") -> ArScanResult(cap, "Portátil", "/porˈtatil/", "Uso mi portátil para trabajar.", "I use my laptop to work.", "Masculine noun: el portátil.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("keyboard") -> ArScanResult(cap, "Teclado", "/teˈklaðo/", "Escribo en el teclado.", "I type on the keyboard.", "Masculine noun: el teclado.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("mouse") -> ArScanResult(cap, "Ratón", "/raˈton/", "Uso el ratón óptico.", "I use the optical mouse.", "Masculine noun: el ratón.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("chair") -> ArScanResult(cap, "Silla", "/ˈsi.ʝa/", "La silla es muy cómoda.", "The chair is very comfortable.", "Feminine noun: la silla.", "Furniture", "Beginner", 50, 50, confidence = confidence)
                key.contains("bottle") -> ArScanResult(cap, "Botella", "/boˈteʝa/", "La botella tiene agua fresca.", "The bottle has fresh water.", "Feminine noun: la botella.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("cup") || key.contains("mug") -> ArScanResult(cap, "Taza", "/ˈtaθa/", "Tomo una taza de café.", "I drink a cup of coffee.", "Feminine noun: la taza.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("book") -> ArScanResult(cap, "Libro", "/ˈliβɾo/", "Leo un libro interesante.", "I read an interesting book.", "Masculine noun: el libro.", "General", "Beginner", 50, 50, confidence = confidence)
                key.contains("phone") || key.contains("cell") -> ArScanResult(cap, "Teléfono", "/teˈlefono/", "Mi teléfono está cargando.", "My phone is charging.", "Masculine noun: el teléfono.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("tv") || key.contains("monitor") -> ArScanResult(cap, "Televisión", "/teleβiˈsjon/", "Miro la televisión.", "I watch television.", "Feminine noun: la televisión.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("person") -> ArScanResult(cap, "Persona", "/peɾˈsona/", "Hay una persona aquí.", "There is a person here.", "Feminine noun: la persona.", "General", "Beginner", 50, 50, confidence = confidence)
                else -> null
            }
            "French" -> when {
                key.contains("laptop") || key.contains("computer") -> ArScanResult(cap, "Ordinateur", "/ɔʁ.di.na.tœʁ/", "J'utilise mon ordinateur.", "I use my computer.", "Masculine noun: l'ordinateur.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("keyboard") -> ArScanResult(cap, "Clavier", "/kla.vje/", "Écrire au clavier.", "Type on the keyboard.", "Masculine noun: le clavier.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("chair") -> ArScanResult(cap, "Chaise", "/ʃɛz/", "Une chaise confortable.", "A comfortable chair.", "Feminine noun: la chaise.", "Furniture", "Beginner", 50, 50, confidence = confidence)
                key.contains("bottle") -> ArScanResult(cap, "Bouteille", "/bu.tɛj/", "Une bouteille d'eau.", "A bottle of water.", "Feminine noun: la bouteille.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("cup") -> ArScanResult(cap, "Tasse", "/tas/", "Une tasse de café.", "A cup of coffee.", "Feminine noun: la tasse.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("book") -> ArScanResult(cap, "Livre", "/livʁ/", "Lire un bon livre.", "Read a good book.", "Masculine noun: le livre.", "General", "Beginner", 50, 50, confidence = confidence)
                else -> null
            }
            "German" -> when {
                key.contains("laptop") || key.contains("computer") -> ArScanResult(cap, "Computer", "/kɔmˈpjuːtɐ/", "Ich nutze meinen Computer.", "I use my computer.", "Masculine noun: der Computer.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("chair") -> ArScanResult(cap, "Stuhl", "/ʃtuːl/", "Ein bequemer Stuhl.", "A comfortable chair.", "Masculine noun: der Stuhl.", "Furniture", "Beginner", 50, 50, confidence = confidence)
                key.contains("bottle") -> ArScanResult(cap, "Flasche", "/ˈflaʃə/", "Eine Flasche Wasser.", "A bottle of water.", "Feminine noun: die Flasche.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("book") -> ArScanResult(cap, "Buch", "/buːx/", "Ein Buch lesen.", "Read a book.", "Neuter noun: das Buch.", "General", "Beginner", 50, 50, confidence = confidence)
                else -> null
            }
            "Japanese" -> when {
                key.contains("laptop") || key.contains("computer") -> ArScanResult(cap, "パソコン", "/pasokon/", "パソコンで作業をします。", "I work on my laptop.", "Katakana noun.", "Electronics", "Beginner", 50, 50, confidence = confidence)
                key.contains("chair") -> ArScanResult(cap, "椅子 (Isu)", "/isu/", "椅子に座ります。", "I sit on the chair.", "Noun: isu.", "Furniture", "Beginner", 50, 50, confidence = confidence)
                key.contains("bottle") -> ArScanResult(cap, "ボトル", "/botoru/", "ボトルの水。", "Water in the bottle.", "Katakana noun.", "Kitchen", "Beginner", 50, 50, confidence = confidence)
                key.contains("book") -> ArScanResult(cap, "本 (Hon)", "/hon/", "本を読みます。", "I read a book.", "Noun: hon.", "General", "Beginner", 50, 50, confidence = confidence)
                else -> null
            }
            else -> null
        }
    }

    private fun createFallbackResult(
        label: String,
        targetLanguage: String,
        confidence: Float
    ): ArScanResult {
        val capitalized = label.replaceFirstChar { it.uppercase() }
        return ArScanResult(
            objectNameEn = capitalized,
            objectNameTarget = "$capitalized ($targetLanguage)",
            pronunciationIpa = "/${label.lowercase()}/",
            exampleSentenceTarget = "This object is a $capitalized.",
            exampleSentenceEn = "Identified object: $capitalized.",
            grammarUsage = "Noun: $capitalized.",
            category = "General",
            difficulty = "Beginner",
            xPosPercent = 50,
            yPosPercent = 50,
            confidence = confidence
        )
    }

    /**
     * Clears cached translations.
     */
    fun clearCache() {
        translationCache.clear()
        Log.i(TAG, "[Cache Cleared] Translation cache cleared.")
    }
}
