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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean Architecture Service responsible for real-time object label translation,
 * dictionary caching, example sentences, and language learning metadata.
 */
class TranslationService(private val context: Context) {

    private val TAG = "TranslationService"
    val mlKitGeminiService = MlKitGeminiTranslationService(context)
    
    // Thread-safe in-memory translation cache (Key: "$englishName_$targetLanguage")
    private val translationCache = ConcurrentHashMap<String, ArScanResult>()

    /**
     * Resolves translation for a detected object name in real-time.
     * Uses instant local dictionary cache first, falling back to background Gemini API lookup.
     */
    suspend fun translateObject(
        englishLabel: String,
        targetLanguage: String,
        confidence: Float
    ): ArScanResult = withContext(Dispatchers.Default) {
        val cleanLabel = englishLabel.lowercase().trim()
        val cacheKey = "${cleanLabel}_$targetLanguage"

        // Handle Unknown Object gracefully
        if (cleanLabel == "unknown object" || cleanLabel == "unknown") {
            return@withContext ArScanResult(
                objectNameEn = "Unknown Object",
                objectNameTarget = "Objeto Desconocido ($targetLanguage)",
                pronunciationIpa = "/ʌnˈnoʊn/",
                exampleSentenceTarget = "Focus closer to identify object.",
                exampleSentenceEn = "Focus closer to identify object.",
                grammarUsage = "Unknown item.",
                category = "General",
                difficulty = "Beginner",
                xPosPercent = 50,
                yPosPercent = 50
            )
        }

        // 1. Check in-memory cache
        translationCache[cacheKey]?.let { return@withContext it }

        // 2. Check instant local dictionary
        val localResult = getLocalDictionaryTranslation(cleanLabel, targetLanguage, confidence)
        if (localResult != null) {
            translationCache[cacheKey] = localResult
            return@withContext localResult
        }

        // 3. Fallback to default generic result while background fetch triggers
        val genericResult = createGenericResult(cleanLabel, targetLanguage, confidence)
        translationCache[cacheKey] = genericResult

        // Trigger asynchronous background translation update via Gemini API if key is available
        CoroutineScope(Dispatchers.IO).launch {
            fetchRemoteTranslation(cleanLabel, targetLanguage, confidence)
        }

        return@withContext genericResult
    }

    private suspend fun fetchRemoteTranslation(
        englishLabel: String,
        targetLanguage: String,
        confidence: Float
    ) {
        try {
            val result = mlKitGeminiService.translateLabelString(englishLabel, targetLanguage, confidence)
            val cacheKey = "${englishLabel.lowercase().trim()}_$targetLanguage"
            translationCache[cacheKey] = result
            Log.d(TAG, "[Backend API Parsing] SUCCESS: Cached translation via MlKitGeminiTranslationService for '$cacheKey': TargetName='${result.objectNameTarget}'")
        } catch (e: Exception) {
            Log.e(TAG, "[Backend API Error] Error fetching remote translation for $englishLabel", e)
        }
    }

    private fun getLocalDictionaryTranslation(
        label: String,
        language: String,
        confidence: Float
    ): ArScanResult? {
        val key = label.lowercase().trim()

        return when (language) {
            "Spanish" -> when (key) {
                "laptop", "computer", "notebook" -> ArScanResult("Laptop", "Portátil", "/porˈtatil/", "Uso mi portátil para trabajar.", "I use my laptop to work.", "Masculine noun: el portátil.", "Electronics", "Beginner", 50, 50)
                "keyboard" -> ArScanResult("Keyboard", "Teclado", "/teˈklaðo/", "Escribo en el teclado.", "I type on the keyboard.", "Masculine noun: el teclado.", "Electronics", "Beginner", 50, 50)
                "mouse" -> ArScanResult("Mouse", "Ratón", "/raˈton/", "Uso el ratón del ordenador.", "I use the computer mouse.", "Masculine noun: el ratón.", "Electronics", "Beginner", 50, 50)
                "chair", "seat", "armchair", "couch", "sofa" -> ArScanResult("Chair", "Silla", "/ˈsi.ʝa/", "La silla es muy cómoda.", "The chair is very comfortable.", "Feminine noun: la silla.", "Furniture", "Beginner", 50, 50)
                "bottle", "water bottle" -> ArScanResult("Bottle", "Botella", "/boˈteʝa/", "La botella tiene agua fresca.", "The bottle has fresh water.", "Feminine noun: la botella.", "Kitchen", "Beginner", 50, 50)
                "cup", "mug", "glass" -> ArScanResult("Cup", "Taza", "/ˈtaθa/", "Tomo una taza de café.", "I drink a cup of coffee.", "Feminine noun: la taza.", "Kitchen", "Beginner", 50, 50)
                "book", "textbook" -> ArScanResult("Book", "Libro", "/ˈliβɾo/", "Leo un libro interesante.", "I read an interesting book.", "Masculine noun: el libro.", "General", "Beginner", 50, 50)
                "person", "human", "man", "woman" -> ArScanResult("Person", "Persona", "/peɾˈsona/", "Hay una persona aquí.", "There is a person here.", "Feminine noun: la persona.", "General", "Beginner", 50, 50)
                "tv", "television", "screen", "monitor" -> ArScanResult("TV", "Televisión", "/teleβiˈsjon/", "Miro la televisión.", "I watch television.", "Feminine noun: la televisión.", "Electronics", "Beginner", 50, 50)
                "cell phone", "phone", "mobile phone", "smartphone" -> ArScanResult("Cell phone", "Teléfono", "/teˈlefono/", "Mi teléfono está cargando.", "My phone is charging.", "Masculine noun: el teléfono.", "Electronics", "Beginner", 50, 50)
                "backpack", "bag" -> ArScanResult("Backpack", "Mochila", "/moˈtʃila/", "Llevo mi mochila.", "I carry my backpack.", "Feminine noun: la mochila.", "Apparel", "Beginner", 50, 50)
                "potted plant", "plant" -> ArScanResult("Plant", "Planta", "/ˈplanta/", "La planta necesita sol.", "The plant needs sun.", "Feminine noun: la planta.", "Nature", "Beginner", 50, 50)
                "clock", "watch" -> ArScanResult("Clock", "Reloj", "/reˈlox/", "Miro el reloj.", "I look at the clock.", "Masculine noun: el reloj.", "General", "Beginner", 50, 50)
                "scissors" -> ArScanResult("Scissors", "Tijeras", "/tiˈxeras/", "Uso las tijeras para cortar.", "I use the scissors to cut.", "Feminine plural: las tijeras.", "Tools", "Beginner", 50, 50)
                "remote" -> ArScanResult("Remote", "Mando", "/ˈmando/", "El mando a distancia.", "The remote control.", "Masculine noun: el mando.", "Electronics", "Beginner", 50, 50)
                "dining table", "table", "desk" -> ArScanResult("Table", "Mesa", "/ˈmesa/", "La comida está en la mesa.", "The food is on the table.", "Feminine noun: la mesa.", "Furniture", "Beginner", 50, 50)
                else -> null
            }
            "French" -> when (key) {
                "laptop", "computer", "notebook" -> ArScanResult("Laptop", "Ordinateur", "/ɔʁ.di.na.tœʁ/", "J'utilise mon ordinateur.", "I use my computer.", "Masculine noun: l'ordinateur.", "Electronics", "Beginner", 50, 50)
                "keyboard" -> ArScanResult("Keyboard", "Clavier", "/kla.vje/", "Écrire au clavier.", "Type on the keyboard.", "Masculine noun: le clavier.", "Electronics", "Beginner", 50, 50)
                "mouse" -> ArScanResult("Mouse", "Souris", "/su.ʁi/", "Une souris d'ordinateur.", "A computer mouse.", "Feminine noun: la souris.", "Electronics", "Beginner", 50, 50)
                "chair", "seat", "armchair", "couch", "sofa" -> ArScanResult("Chair", "Chaise", "/ʃɛz/", "Une chaise confortable.", "A comfortable chair.", "Feminine noun: la chaise.", "Furniture", "Beginner", 50, 50)
                "bottle", "water bottle" -> ArScanResult("Bottle", "Bouteille", "/bu.tɛj/", "Une bouteille d'eau.", "A bottle of water.", "Feminine noun: la bouteille.", "Kitchen", "Beginner", 50, 50)
                "cup", "mug", "glass" -> ArScanResult("Cup", "Tasse", "/tas/", "Une tasse de café.", "A cup of coffee.", "Feminine noun: la tasse.", "Kitchen", "Beginner", 50, 50)
                "book", "textbook" -> ArScanResult("Book", "Livre", "/livʁ/", "Lire un bon livre.", "Read a good book.", "Masculine noun: le livre.", "General", "Beginner", 50, 50)
                "person", "human", "man", "woman" -> ArScanResult("Person", "Personne", "/pɛʁ.sɔn/", "Une personne agréable.", "A pleasant person.", "Feminine noun: la personne.", "General", "Beginner", 50, 50)
                "tv", "television", "screen", "monitor" -> ArScanResult("TV", "Télévision", "/te.le.vi.zjɔ̃/", "Regarder la télévision.", "Watch television.", "Feminine noun: la télévision.", "Electronics", "Beginner", 50, 50)
                "cell phone", "phone", "mobile phone", "smartphone" -> ArScanResult("Cell phone", "Téléphone", "/te.le.fɔn/", "Mon téléphone portable.", "My mobile phone.", "Masculine noun: le téléphone.", "Electronics", "Beginner", 50, 50)
                else -> null
            }
            "German" -> when (key) {
                "laptop", "computer", "notebook" -> ArScanResult("Laptop", "Computer", "/kɔmˈpjuːtɐ/", "Ich nutze meinen Computer.", "I use my computer.", "Masculine noun: der Computer.", "Electronics", "Beginner", 50, 50)
                "keyboard" -> ArScanResult("Keyboard", "Tastatur", "/tastaˈtuːɐ̯/", "Die Tastatur ist sauber.", "The keyboard is clean.", "Feminine noun: die Tastatur.", "Electronics", "Beginner", 50, 50)
                "mouse" -> ArScanResult("Mouse", "Maus", "/maʊ̯s/", "Die Computermaus.", "The computer mouse.", "Feminine noun: die Maus.", "Electronics", "Beginner", 50, 50)
                "chair", "seat", "armchair", "couch", "sofa" -> ArScanResult("Chair", "Stuhl", "/ʃtuːl/", "Ein bequemer Stuhl.", "A comfortable chair.", "Masculine noun: der Stuhl.", "Furniture", "Beginner", 50, 50)
                "bottle", "water bottle" -> ArScanResult("Bottle", "Flasche", "/ˈflaʃə/", "Eine Flasche Wasser.", "A bottle of water.", "Feminine noun: die Flasche.", "Kitchen", "Beginner", 50, 50)
                "cup", "mug", "glass" -> ArScanResult("Cup", "Tasse", "/ˈtasə/", "Eine Tasse Kaffee.", "A cup of coffee.", "Feminine noun: die Tasse.", "Kitchen", "Beginner", 50, 50)
                "book", "textbook" -> ArScanResult("Book", "Buch", "/buːx/", "Ein Buch lesen.", "Read a book.", "Neuter noun: das Buch.", "General", "Beginner", 50, 50)
                "tv", "television", "screen", "monitor" -> ArScanResult("TV", "Fernseher", "/ˈfɛʁnˌzeːɐ/", "Der Fernseher läuft.", "The TV is on.", "Masculine noun: der Fernseher.", "Electronics", "Beginner", 50, 50)
                "cell phone", "phone", "mobile phone", "smartphone" -> ArScanResult("Cell phone", "Handy", "/ˈhɛndi/", "Mein Handy klingelt.", "My phone is ringing.", "Neuter noun: das Handy.", "Electronics", "Beginner", 50, 50)
                else -> null
            }
            "Japanese" -> when (key) {
                "laptop", "computer", "notebook" -> ArScanResult("Laptop", "パソコン (Pasokon)", "/pa.so.koɴ/", "パソコンで作業をします。", "I work on my laptop.", "Katakana noun.", "Electronics", "Beginner", 50, 50)
                "keyboard" -> ArScanResult("Keyboard", "キーボード (Kībōdo)", "/kiː.boː.do/", "キーボードを打ちます。", "I type on the keyboard.", "Katakana noun.", "Electronics", "Beginner", 50, 50)
                "mouse" -> ArScanResult("Mouse", "マウス (Mausu)", "/ma.ɯ.sɯ/", "マウスを動かします。", "I move the mouse.", "Katakana noun.", "Electronics", "Beginner", 50, 50)
                "chair", "seat", "armchair", "couch", "sofa" -> ArScanResult("Chair", "椅子 (Isu)", "/i.sɯ/", "椅子に座ります。", "I sit on the chair.", "Noun: isu.", "Furniture", "Beginner", 50, 50)
                "bottle", "water bottle" -> ArScanResult("Bottle", "ボトル (Botoru)", "/bo.to.ɾɯ/", "ボトルに水が入っています。", "Water is in the bottle.", "Katakana noun.", "Kitchen", "Beginner", 50, 50)
                "cup", "mug", "glass" -> ArScanResult("Cup", "コップ (Koppu)", "/koː.pɯ/", "コップにお茶を注ぎます。", "I pour tea into the cup.", "Katakana noun.", "Kitchen", "Beginner", 50, 50)
                "book", "textbook" -> ArScanResult("Book", "本 (Hon)", "/hoɴ/", "本を読みます。", "I read a book.", "Noun: hon.", "General", "Beginner", 50, 50)
                "tv", "television", "screen", "monitor" -> ArScanResult("TV", "テレビ (Terebi)", "/te.ɾe.bi/", "テレビを見ます。", "I watch TV.", "Katakana noun.", "Electronics", "Beginner", 50, 50)
                "cell phone", "phone", "mobile phone", "smartphone" -> ArScanResult("Cell phone", "スマホ (Sumaho)", "/sɯ.ma.ho/", "スマホをチェックします。", "I check my phone.", "Katakana abbreviation.", "Electronics", "Beginner", 50, 50)
                else -> null
            }
            else -> null
        }
    }

    private fun createGenericResult(
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
            yPosPercent = 50
        )
    }
}
