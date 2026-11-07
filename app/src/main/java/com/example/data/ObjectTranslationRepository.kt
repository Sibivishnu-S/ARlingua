package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.service.RawDetectedObject
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ObjTranslationRepo"

/**
 * Data class representing a complete translation result for an ML Kit detected object,
 * enriched with phonetic pronunciation, grammar usage, and deep cultural context notes.
 */
@JsonClass(generateAdapter = true)
data class ObjectCulturalTranslation(
    val objectNameEn: String,
    val objectNameTarget: String,
    val pronunciationIpa: String = "",
    val exampleSentenceTarget: String = "",
    val exampleSentenceEn: String = "",
    val grammarUsage: String = "",
    val culturalContext: String = "",
    val funFact: String = "",
    val category: String = "General",
    val difficulty: String = "Beginner",
    val confidence: Float = 0.95f,
    val targetLanguage: String = ""
)

/**
 * Moshi wrapper class for batch Gemini API responses.
 */
@JsonClass(generateAdapter = true)
data class ObjectCulturalTranslationBatchWrapper(
    val results: List<ObjectCulturalTranslation> = emptyList()
)

/**
 * Repository interface for translating ML Kit output labels into target languages
 * with AI-generated cultural context and persistence.
 */
interface ObjectTranslationRepository {
    suspend fun translateObject(
        label: String,
        targetLanguage: String,
        confidence: Float = 0.95f
    ): ObjectCulturalTranslation

    suspend fun translateObject(
        rawObject: RawDetectedObject,
        targetLanguage: String
    ): ObjectCulturalTranslation

    suspend fun translateBatchObjects(
        labels: List<String>,
        targetLanguage: String
    ): List<ObjectCulturalTranslation>

    suspend fun findCachedDetectedObject(objectNameEn: String, language: String): DetectedObjectEntity?

    fun clearCache()
}

/**
 * Production-Grade implementation of ObjectTranslationRepository.
 * Interoperates with ML Kit object detection pipeline, Gemini API, and Room database persistence.
 */
class ObjectTranslationRepositoryImpl(
    private val context: Context,
    private val detectedObjectDao: DetectedObjectDao? = null
) : ObjectTranslationRepository {

    private val inMemoryCache = ConcurrentHashMap<String, ObjectCulturalTranslation>()

    override suspend fun findCachedDetectedObject(objectNameEn: String, language: String): DetectedObjectEntity? {
        return try {
            detectedObjectDao?.findCachedObject(objectNameEn.trim(), language.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error finding cached detected object", e)
            null
        }
    }

    override suspend fun translateObject(
        rawObject: RawDetectedObject,
        targetLanguage: String
    ): ObjectCulturalTranslation {
        return translateObject(
            label = rawObject.label,
            targetLanguage = targetLanguage,
            confidence = rawObject.confidence
        )
    }

    override suspend fun translateObject(
        label: String,
        targetLanguage: String,
        confidence: Float
    ): ObjectCulturalTranslation = withContext(Dispatchers.IO) {
        val cleanLabel = label.trim()
        if (cleanLabel.isBlank()) {
            return@withContext createFallback(label, targetLanguage, confidence)
        }

        val cacheKey = "${cleanLabel.lowercase()}_${targetLanguage.lowercase()}"

        // 1. Check thread-safe in-memory cache
        inMemoryCache[cacheKey]?.let { cached ->
            Log.d(TAG, "[Cache Hit] Returning cached cultural translation for key: '$cacheKey'")
            return@withContext cached.copy(confidence = confidence)
        }

        // 2. Check local instant dictionary with rich cultural context
        val localFallback = getLocalCulturalDictionaryFallback(cleanLabel, targetLanguage, confidence)

        // 3. Query Gemini API
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "[Gemini API] Key omitted. Using local cultural fallback for '$cleanLabel'")
            val fallback = localFallback ?: createFallback(cleanLabel, targetLanguage, confidence)
            inMemoryCache[cacheKey] = fallback
            saveToRoomDatabaseQuietly(fallback)
            return@withContext fallback
        }

        try {
            Log.i(TAG, "[Gemini API Call] Fetching translation & cultural context for '$cleanLabel' -> '$targetLanguage'")

            val prompt = """
                You are a world-class cultural linguist and real-time AR language tutor.
                An on-device ML Kit object detection model identified the physical object "$cleanLabel".
                Translate this object name into $targetLanguage and explain its cultural context, social etiquette, or regional significance in cultures where $targetLanguage is spoken.

                Return JSON matching this exact structure:
                {
                  "objectNameEn": "$cleanLabel",
                  "objectNameTarget": "Exact translation in $targetLanguage",
                  "pronunciationIpa": "IPA phonetic spelling or romanization",
                  "exampleSentenceTarget": "Natural example sentence in $targetLanguage",
                  "exampleSentenceEn": "English translation of the example sentence",
                  "grammarUsage": "Grammar note, noun gender, or loanword origin",
                  "culturalContext": "1-2 engaging sentences detailing cultural significance, regional etiquette, or historical symbolism in $targetLanguage speaking countries.",
                  "funFact": "1 interesting linguistic fun fact or mnemonic tip",
                  "category": "Electronics / Kitchen / Furniture / Workplace / Food / Animals / General",
                  "difficulty": "Beginner",
                  "targetLanguage": "$targetLanguage"
                }
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.25f
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!jsonText.isNullOrBlank()) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(ObjectCulturalTranslation::class.java)
                val parsed = adapter.fromJson(jsonText)
                if (parsed != null && parsed.objectNameTarget.isNotBlank()) {
                    val finalResult = parsed.copy(
                        confidence = confidence,
                        targetLanguage = targetLanguage
                    )
                    inMemoryCache[cacheKey] = finalResult
                    saveToRoomDatabaseQuietly(finalResult)
                    Log.i(TAG, "[Gemini API Success] Translated '$cleanLabel' -> '${finalResult.objectNameTarget}' with cultural note: '${finalResult.culturalContext}'")
                    return@withContext finalResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini API Error] Failed to get cultural translation for '$cleanLabel': ${e.message}", e)
        }

        val fallback = localFallback ?: createFallback(cleanLabel, targetLanguage, confidence)
        inMemoryCache[cacheKey] = fallback
        saveToRoomDatabaseQuietly(fallback)
        return@withContext fallback
    }

    override suspend fun translateBatchObjects(
        labels: List<String>,
        targetLanguage: String
    ): List<ObjectCulturalTranslation> = withContext(Dispatchers.IO) {
        val uniqueLabels = labels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueLabels.isEmpty()) return@withContext emptyList()

        val resultsMap = mutableMapOf<String, ObjectCulturalTranslation>()
        val uncached = mutableListOf<String>()

        for (label in uniqueLabels) {
            val key = "${label.lowercase()}_${targetLanguage.lowercase()}"
            val cached = inMemoryCache[key]
            if (cached != null) {
                resultsMap[label] = cached
            } else {
                uncached.add(label)
            }
        }

        if (uncached.isEmpty()) {
            return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            for (label in uncached) {
                val fallback = getLocalCulturalDictionaryFallback(label, targetLanguage, 0.95f)
                    ?: createFallback(label, targetLanguage, 0.95f)
                val key = "${label.lowercase()}_${targetLanguage.lowercase()}"
                inMemoryCache[key] = fallback
                resultsMap[label] = fallback
                saveToRoomDatabaseQuietly(fallback)
            }
            return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
        }

        try {
            Log.i(TAG, "[Gemini Batch Call] Translating ${uncached.size} ML Kit labels with cultural context to $targetLanguage: $uncached")

            val prompt = """
                Translate the following ML Kit detected object labels into $targetLanguage and include cultural context for each:
                ${uncached.joinToString(", ")}

                Return JSON matching this wrapper structure:
                {
                  "results": [
                    {
                      "objectNameEn": "Laptop",
                      "objectNameTarget": "Portátil",
                      "pronunciationIpa": "/porˈtatil/",
                      "exampleSentenceTarget": "Uso mi portátil.",
                      "exampleSentenceEn": "I use my laptop.",
                      "grammarUsage": "Masculine noun",
                      "culturalContext": "Laptops are central to digital nomad hubs in Madrid and Barcelona.",
                      "funFact": "In Spain, portable computers are commonly called 'portátil' rather than 'laptop'.",
                      "category": "Electronics",
                      "difficulty": "Beginner",
                      "targetLanguage": "$targetLanguage"
                    }
                  ]
                }
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.25f
                )
            )

            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!jsonText.isNullOrBlank()) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(ObjectCulturalTranslationBatchWrapper::class.java)
                val wrapper = adapter.fromJson(jsonText)
                wrapper?.results?.forEach { item ->
                    val key = item.objectNameEn.trim()
                    val cacheKey = "${key.lowercase()}_${targetLanguage.lowercase()}"
                    inMemoryCache[cacheKey] = item
                    resultsMap[key] = item
                    saveToRoomDatabaseQuietly(item)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini Batch API Error] ${e.message}", e)
        }

        for (label in uncached) {
            if (!resultsMap.containsKey(label)) {
                val fallback = getLocalCulturalDictionaryFallback(label, targetLanguage, 0.95f)
                    ?: createFallback(label, targetLanguage, 0.95f)
                val key = "${label.lowercase()}_${targetLanguage.lowercase()}"
                inMemoryCache[key] = fallback
                resultsMap[label] = fallback
                saveToRoomDatabaseQuietly(fallback)
            }
        }

        return@withContext uniqueLabels.mapNotNull { resultsMap[it] }
    }

    override fun clearCache() {
        inMemoryCache.clear()
        Log.i(TAG, "[Repository Cache Cleared]")
    }

    private suspend fun saveToRoomDatabaseQuietly(item: ObjectCulturalTranslation) {
        if (detectedObjectDao == null) return
        try {
            val entity = DetectedObjectEntity(
                objectNameEn = item.objectNameEn,
                objectNameTarget = item.objectNameTarget,
                pronunciationIpa = item.pronunciationIpa,
                exampleSentenceTarget = item.exampleSentenceTarget,
                exampleSentenceEn = item.exampleSentenceEn,
                category = item.category,
                difficulty = item.difficulty,
                language = item.targetLanguage.ifBlank { "Spanish" },
                confidence = item.confidence,
                detectedTimestamp = System.currentTimeMillis()
            )
            detectedObjectDao.insertDetectedObject(entity)
            Log.d(TAG, "[Room Database Saved] Persisted '${item.objectNameEn}' -> '${item.objectNameTarget}'")
        } catch (e: Exception) {
            Log.e(TAG, "[Room Database Error] Failed to persist detected object: ${e.message}")
        }
    }

    private fun getLocalCulturalDictionaryFallback(
        label: String,
        targetLanguage: String,
        confidence: Float
    ): ObjectCulturalTranslation? {
        val key = label.lowercase().trim()
        val cap = label.replaceFirstChar { it.uppercase() }

        return when (targetLanguage) {
            "Spanish" -> when {
                key.contains("laptop") || key.contains("computer") -> ObjectCulturalTranslation(
                    objectNameEn = cap,
                    objectNameTarget = "Portátil",
                    pronunciationIpa = "/porˈtatil/",
                    exampleSentenceTarget = "Trabajo con mi portátil en el café.",
                    exampleSentenceEn = "I work on my laptop in the cafe.",
                    grammarUsage = "Masculine noun: el portátil",
                    culturalContext = "Café culture in Spain and Latin America increasingly embraces remote work with laptops in vibrant plazas.",
                    funFact = "In Peninsular Spanish, 'portátil' is preferred over 'laptop'.",
                    category = "Electronics",
                    difficulty = "Beginner",
                    confidence = confidence,
                    targetLanguage = "Spanish"
                )
                key.contains("cup") || key.contains("mug") || key.contains("coffee") -> ObjectCulturalTranslation(
                    objectNameEn = cap,
                    objectNameTarget = "Taza de café",
                    pronunciationIpa = "/ˈtaθa ðe kaˈfe/",
                    exampleSentenceTarget = "Me gusta tomar una taza de café solo.",
                    exampleSentenceEn = "I like drinking a cup of black coffee.",
                    grammarUsage = "Feminine noun: la taza",
                    culturalContext = "Coffee breaks ('la hora del café') are vital social rituals in Spanish workplaces and family gatherings.",
                    funFact = "Asking for 'café con leche' in the morning is a staple Spanish breakfast habit.",
                    category = "Kitchen",
                    difficulty = "Beginner",
                    confidence = confidence,
                    targetLanguage = "Spanish"
                )
                key.contains("book") -> ObjectCulturalTranslation(
                    objectNameEn = cap,
                    objectNameTarget = "Libro",
                    pronunciationIpa = "/ˈliβɾo/",
                    exampleSentenceTarget = "Leo un libro en la biblioteca.",
                    exampleSentenceEn = "I read a book in the library.",
                    grammarUsage = "Masculine noun: el libro",
                    culturalContext = "Spain celebrates 'Sant Jordi' (St. George's Day) on April 23 by gifting books and roses to loved ones.",
                    funFact = "Miguel de Cervantes' Don Quixote is considered the first modern novel in Western literature.",
                    category = "General",
                    difficulty = "Beginner",
                    confidence = confidence,
                    targetLanguage = "Spanish"
                )
                else -> null
            }
            "Japanese" -> when {
                key.contains("cup") || key.contains("tea") || key.contains("mug") -> ObjectCulturalTranslation(
                    objectNameEn = cap,
                    objectNameTarget = "湯呑み (Yunomi)",
                    pronunciationIpa = "/junomi/",
                    exampleSentenceTarget = "温かいお茶を湯呑みで飲みます。",
                    exampleSentenceEn = "I drink warm tea from a tea cup.",
                    grammarUsage = "Noun: yunomi",
                    culturalContext = "Yunomi are handleless ceramic teacups designed for daily green tea drinking, reflecting the rustic aesthetics of wabi-sabi.",
                    funFact = "Unlike Western mugs, traditional yunomi cups are held with both hands to feel the warmth of green tea.",
                    category = "Kitchen",
                    difficulty = "Beginner",
                    confidence = confidence,
                    targetLanguage = "Japanese"
                )
                key.contains("laptop") || key.contains("computer") -> ObjectCulturalTranslation(
                    objectNameEn = cap,
                    objectNameTarget = "パソコン (Pasokon)",
                    pronunciationIpa = "/pasokon/",
                    exampleSentenceTarget = "カフェでパソコンを使います。",
                    exampleSentenceEn = "I use my laptop at the cafe.",
                    grammarUsage = "Katakana loanword abbreviation of 'Personal Computer'",
                    culturalContext = "Japanese workplace technology blends cutting-edge computing with traditional paper hanko stamps.",
                    funFact = "'Pasokon' is a uniquely Japanese portmanteau (wasei-eigo) of 'personal computer'.",
                    category = "Electronics",
                    difficulty = "Beginner",
                    confidence = confidence,
                    targetLanguage = "Japanese"
                )
                else -> null
            }
            else -> null
        }
    }

    private fun createFallback(
        label: String,
        targetLanguage: String,
        confidence: Float
    ): ObjectCulturalTranslation {
        val capitalized = label.replaceFirstChar { it.uppercase() }
        return ObjectCulturalTranslation(
            objectNameEn = capitalized,
            objectNameTarget = "$capitalized ($targetLanguage)",
            pronunciationIpa = "/${label.lowercase()}/",
            exampleSentenceTarget = "This object is a $capitalized.",
            exampleSentenceEn = "Identified object: $capitalized.",
            grammarUsage = "Noun: $capitalized",
            culturalContext = "$capitalized plays an everyday role in $targetLanguage-speaking households and workplaces.",
            funFact = "Linguists observe that terms for everyday objects like $capitalized often reflect local historical trade and usage.",
            category = "General",
            difficulty = "Beginner",
            confidence = confidence,
            targetLanguage = targetLanguage
        )
    }
}
