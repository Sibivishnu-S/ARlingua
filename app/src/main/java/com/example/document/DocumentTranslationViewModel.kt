package com.example.document

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OcrRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

class DocumentTranslationViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val TAG = "DocumentTransViewModel"

    private val ocrEngine by lazy { OcrEngine(application) }
    private val translationEngine by lazy { TranslationEngine() }
    private val pdfGenerator by lazy { PdfGenerator(application) }
    private val db by lazy { AppDatabase.getDatabase(application) }

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // State Machine
    private val _processingStage = MutableStateFlow(DocumentProcessingStage.IDLE)
    val processingStage: StateFlow<DocumentProcessingStage> = _processingStage.asStateFlow()

    private val _preprocessingMode = MutableStateFlow(PreprocessingMode.NORMAL)
    val preprocessingMode: StateFlow<PreprocessingMode> = _preprocessingMode.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _ocrDocument = MutableStateFlow<OcrDocument?>(null)
    val ocrDocument: StateFlow<OcrDocument?> = _ocrDocument.asStateFlow()

    private val _ocrQualityReport = MutableStateFlow(DocumentQualityReport())
    val ocrQualityReport: StateFlow<DocumentQualityReport> = _ocrQualityReport.asStateFlow()

    private val _sourceLanguage = MutableStateFlow("Auto-detected")
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow("Tamil")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _translationResult = MutableStateFlow<TranslationResult?>(null)
    val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

    private val _editedTranslationText = MutableStateFlow("")
    val editedTranslationText: StateFlow<String> = _editedTranslationText.asStateFlow()

    private val _generatedPdfInfo = MutableStateFlow<GeneratedPdfInfo?>(null)
    val generatedPdfInfo: StateFlow<GeneratedPdfInfo?> = _generatedPdfInfo.asStateFlow()

    private val _chunkProgress = MutableStateFlow(Pair(0, 0))
    val chunkProgress: StateFlow<Pair<Int, Int>> = _chunkProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    private val _documentHistory = MutableStateFlow<List<OcrRecord>>(emptyList())
    val documentHistory: StateFlow<List<OcrRecord>> = _documentHistory.asStateFlow()

    init {
        try {
            textToSpeech = TextToSpeech(application, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
        loadDocumentHistory()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isTtsSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                }
                override fun onError(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                }
            })
        }
    }

    fun setPreprocessingMode(mode: PreprocessingMode) {
        _preprocessingMode.value = mode
        val currentBitmap = _capturedBitmap.value
        if (currentBitmap != null) {
            processBitmapOcr(currentBitmap)
        }
    }

    fun setTargetLanguage(langName: String) {
        _targetLanguage.value = langName
    }

    fun updateEditedTranslationText(text: String) {
        _editedTranslationText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Loads image fromUri (Gallery or Camera capture) and executes OCR
     */
    fun processImageUri(uri: Uri) {
        viewModelScope.launch {
            _processingStage.value = DocumentProcessingStage.PROCESSING_IMAGE
            _errorMessage.value = null

            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream: InputStream? = getApplication<Application>().contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }

                if (bitmap == null) {
                    _errorMessage.value = "Failed to load selected image."
                    _processingStage.value = DocumentProcessingStage.ERROR
                    return@launch
                }

                _capturedBitmap.value = bitmap
                processBitmapOcr(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image URI", e)
                _errorMessage.value = "Error loading image: ${e.localizedMessage}"
                _processingStage.value = DocumentProcessingStage.ERROR
            }
        }
    }

    /**
     * Processes bitmap through OCR pipeline
     */
    fun processBitmapOcr(bitmap: Bitmap) {
        viewModelScope.launch {
            _processingStage.value = DocumentProcessingStage.OCR_PROCESSING
            _errorMessage.value = null

            try {
                val doc = ocrEngine.recognizeDocumentText(
                    bitmap = bitmap,
                    preprocessingMode = _preprocessingMode.value,
                    allowGeminiFallback = true
                )

                _ocrDocument.value = doc
                _ocrQualityReport.value = doc.qualityReport
                _sourceLanguage.value = doc.detectedLanguageName

                if (doc.pages.isEmpty() || doc.fullText.isBlank()) {
                    _errorMessage.value = doc.qualityReport.warningMessage ?: "No readable text detected. Ensure document is well lit and flat."
                    _processingStage.value = DocumentProcessingStage.ERROR
                } else {
                    _processingStage.value = DocumentProcessingStage.OCR_COMPLETE
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR processing error", e)
                _errorMessage.value = "OCR processing failed: ${e.localizedMessage}"
                _processingStage.value = DocumentProcessingStage.ERROR
            }
        }
    }

    /**
     * Translates extracted document text via Gemini
     */
    fun translateDocumentText() {
        val doc = _ocrDocument.value
        val rawText = doc?.fullText ?: ""
        if (rawText.isBlank()) {
            _errorMessage.value = "No OCR text available to translate."
            return
        }

        viewModelScope.launch {
            _processingStage.value = DocumentProcessingStage.TRANSLATING
            _errorMessage.value = null

            try {
                val result = translationEngine.translateDocument(
                    originalText = rawText,
                    sourceLanguage = _sourceLanguage.value,
                    targetLanguage = _targetLanguage.value,
                    onProgressUpdate = { current, total ->
                        _chunkProgress.value = Pair(current, total)
                    }
                )

                _translationResult.value = result
                _editedTranslationText.value = result.translatedText
                _processingStage.value = DocumentProcessingStage.TRANSLATION_COMPLETE

            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                _errorMessage.value = "Translation failed: ${e.localizedMessage}"
                _processingStage.value = DocumentProcessingStage.ERROR
            }
        }
    }

    /**
     * Generates Multilingual PDF from edited translation
     */
    fun generatePdf() {
        val textToExport = _editedTranslationText.value
        if (textToExport.isBlank()) {
            _errorMessage.value = "Cannot generate PDF with empty translation."
            return
        }

        viewModelScope.launch {
            _processingStage.value = DocumentProcessingStage.GENERATING_PDF
            _errorMessage.value = null

            try {
                val pdfInfo = pdfGenerator.generateTranslatedPdf(
                    title = "ARLinguaLearn Document Translation",
                    translatedText = textToExport,
                    sourceLanguage = _sourceLanguage.value,
                    targetLanguage = _targetLanguage.value,
                    originalText = _ocrDocument.value?.fullText
                )

                _generatedPdfInfo.value = pdfInfo
                _processingStage.value = DocumentProcessingStage.PDF_COMPLETE

                // Save to Room DB history
                saveToHistory(
                    recognizedText = _ocrDocument.value?.fullText ?: "",
                    translation = textToExport,
                    targetLanguage = _targetLanguage.value
                )

            } catch (e: Exception) {
                Log.e(TAG, "PDF generation failed", e)
                _errorMessage.value = "PDF export failed: ${e.localizedMessage}"
                _processingStage.value = DocumentProcessingStage.ERROR
            }
        }
    }

    fun openPdf(context: Context) {
        val pdfInfo = _generatedPdfInfo.value ?: return
        try {
            val intent = pdfGenerator.createOpenPdfIntent(pdfInfo.fileUri)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "No PDF viewer app found to open file."
        }
    }

    fun sharePdf(context: Context) {
        val pdfInfo = _generatedPdfInfo.value ?: return
        try {
            val intent = pdfGenerator.createSharePdfIntent(pdfInfo.fileUri, pdfInfo.fileName)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to open sharing menu."
        }
    }

    fun speakTranslation() {
        val text = _editedTranslationText.value
        if (text.isBlank() || !isTtsInitialized) return

        if (_isTtsSpeaking.value) {
            textToSpeech?.stop()
            _isTtsSpeaking.value = false
            return
        }

        val targetLangCode = TranslationEngine.SUPPORTED_LANGUAGES.find {
            it.displayName.equals(_targetLanguage.value, ignoreCase = true)
        }?.code ?: "en"

        val locale = Locale(targetLangCode)
        textToSpeech?.language = locale
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DocTranslationTTS")
    }

    private fun saveToHistory(recognizedText: String, translation: String, targetLanguage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = OcrRecord(
                    recognizedText = recognizedText.take(500),
                    translation = translation.take(500),
                    language = targetLanguage,
                    scanTime = System.currentTimeMillis()
                )
                db.ocrRecordDao().insertOcrRecord(record)
                loadDocumentHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving OCR history to DB", e)
            }
        }
    }

    private fun loadDocumentHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.ocrRecordDao().getAllOcrRecords().collect { records ->
                    _documentHistory.value = records
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading document history", e)
            }
        }
    }

    fun resetState() {
        _processingStage.value = DocumentProcessingStage.IDLE
        _capturedBitmap.value = null
        _ocrDocument.value = null
        _translationResult.value = null
        _editedTranslationText.value = ""
        _generatedPdfInfo.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        ocrEngine.close()
    }
}
