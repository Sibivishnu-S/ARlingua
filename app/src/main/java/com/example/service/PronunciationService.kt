package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Clean Architecture Service providing IPA pronunciations, audio synthesis,
 * and Text-To-Speech (TTS) playback in target learning languages using Android's TextToSpeech engine.
 */
class PronunciationService(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "PronunciationService"
    private var tts: TextToSpeech? = null
    @Volatile private var isTtsInitialized = false

    private data class PendingSpeechRequest(
        val text: String,
        val languageName: String,
        val speed: Float,
        val isUtteranceFlush: Boolean
    )

    private val pendingQueue = ConcurrentLinkedQueue<PendingSpeechRequest>()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate Android TextToSpeech engine", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.i(TAG, "[TextToSpeech Engine] Initialized successfully.")

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "[TTS Playing] Utterance started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "[TTS Completed] Utterance completed: $utteranceId")
                }

                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "[TTS Error] Utterance failed: $utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "[TTS Error] Utterance failed: $utteranceId, Error code: $errorCode")
                }
            })

            // Flush any pending speech requests queued prior to initialization
            while (pendingQueue.isNotEmpty()) {
                val req = pendingQueue.poll()
                if (req != null) {
                    speakInternal(req.text, req.languageName, req.speed, req.isUtteranceFlush)
                }
            }
        } else {
            isTtsInitialized = false
            Log.e(TAG, "[TextToSpeech Engine] Initialization failed with status code $status")
        }
    }

    /**
     * Speaks the target text using Android TextToSpeech in the target language locale.
     */
    fun speak(text: String, languageName: String, speed: Float = 1.0f) {
        if (text.isBlank()) return

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "[TextToSpeech] Engine not ready yet. Queuing request for '$text' ($languageName)")
            pendingQueue.add(PendingSpeechRequest(text, languageName, speed, isUtteranceFlush = true))
            return
        }

        speakInternal(text, languageName, speed, isUtteranceFlush = true)
    }

    /**
     * Automatically reads out the generated target language word when an object is detected.
     */
    fun speakDetectedWord(targetWord: String, languageName: String) {
        Log.i(TAG, "[Auto TTS On Object Detected] Speaking target word '$targetWord' in $languageName")
        speak(targetWord, languageName, speed = 0.95f)
    }

    private fun speakInternal(text: String, languageName: String, speed: Float, isUtteranceFlush: Boolean) {
        val locale = getLocaleForLanguage(languageName)
        try {
            val ttsEngine = tts ?: return
            val result = ttsEngine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "[TextToSpeech] Locale '$locale' for $languageName missing or unsupported. Falling back to default locale.")
                ttsEngine.setLanguage(Locale.getDefault())
            }
            ttsEngine.setSpeechRate(speed)
            val queueMode = if (isUtteranceFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "OBJECT_DETECTION_TTS_${System.currentTimeMillis()}"
            ttsEngine.speak(text, queueMode, null, utteranceId)
            Log.d(TAG, "[TextToSpeech] Speaking text '$text' in language '$languageName' (Locale: $locale)")
        } catch (e: Exception) {
            Log.e(TAG, "[TextToSpeech Error] Exception speaking text '$text'", e)
        }
    }

    private fun getLocaleForLanguage(languageName: String): Locale {
        return when (languageName.trim()) {
            "Spanish" -> Locale("es", "ES")
            "French" -> Locale.FRANCE
            "German" -> Locale.GERMANY
            "Japanese" -> Locale.JAPAN
            "Chinese" -> Locale.CHINA
            "Hindi" -> Locale("hi", "IN")
            "Italian" -> Locale.ITALY
            "Korean" -> Locale.KOREA
            "Portuguese" -> Locale("pt", "BR")
            "Russian" -> Locale("ru", "RU")
            else -> Locale.US
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
            pendingQueue.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}

