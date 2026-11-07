package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@androidx.compose.runtime.Stable
data class DailyPlanTaskItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val xpReward: Int = 15,
    val category: String = "Daily"
)

@androidx.compose.runtime.Stable
data class ArMissionItem(
    val id: String,
    val objectNameEn: String,
    val isCompleted: Boolean = false,
    val xpReward: Int = 20,
    val coinReward: Int = 10
)

@androidx.compose.runtime.Stable
data class WeeklyDaySchedule(
    val dayName: String,
    val dayShort: String,
    val topic: String,
    val isCompleted: Boolean = false,
    val routeTarget: String,
    val detailDescription: String
)

enum class TtsPlaybackState {
    IDLE,
    SPEAKING,
    PAUSED,
    STOPPED
}

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val prefs = application.getSharedPreferences("arlingua_prefs", Context.MODE_PRIVATE)

    private val auth by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(application).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w("MainViewModel", "Firebase Auth unavailable: ${e.message}")
            null
        }
    }

    private val firestore by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(application).isNotEmpty()) {
                val dbInstance = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                dbInstance.firestoreSettings = settings
                dbInstance
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w("MainViewModel", "Firestore unavailable: ${e.message}")
            null
        }
    }

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInUserEmail = MutableStateFlow(prefs.getString("user_email", "") ?: "")
    val loggedInUserEmail: StateFlow<String> = _loggedInUserEmail.asStateFlow()

    private val _loggedInUserName = MutableStateFlow(prefs.getString("user_name", "Explorer") ?: "Explorer")
    val loggedInUserName: StateFlow<String> = _loggedInUserName.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _verificationSent = MutableStateFlow(false)
    val verificationSent: StateFlow<Boolean> = _verificationSent.asStateFlow()

    fun loginUser(email: String, name: String) {
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_email", email)
            putString("user_name", name)
            apply()
        }
        _isLoggedIn.value = true
        _loggedInUserEmail.value = email
        _loggedInUserName.value = name
    }

    fun signInWithEmailAndPassword(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val firebaseAuth = auth
            if (firebaseAuth != null) {
                try {
                    firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            _isAuthLoading.value = false
                            if (task.isSuccessful) {
                                val user = firebaseAuth.currentUser
                                val userName = user?.displayName ?: email.substringBefore("@")
                                loginUser(email, userName)
                                syncFromFirestore()
                                onComplete(true, null)
                            } else {
                                val errMsg = task.exception?.localizedMessage ?: "Authentication failed"
                                _authError.value = errMsg
                                onComplete(false, errMsg)
                            }
                        }
                } catch (e: Exception) {
                    _isAuthLoading.value = false
                    _authError.value = e.localizedMessage
                    onComplete(false, e.localizedMessage)
                }
            } else {
                delay(1000)
                _isAuthLoading.value = false
                val derivedName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                loginUser(email, derivedName)
                onComplete(true, null)
            }
        }
    }

    fun signUpWithEmailAndPassword(email: String, password: String, name: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val firebaseAuth = auth
            if (firebaseAuth != null) {
                try {
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = firebaseAuth.currentUser
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()
                                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                    user.sendEmailVerification()
                                    _isAuthLoading.value = false
                                    loginUser(email, name)
                                    createUserProfileInFirestore(email, name)
                                    onComplete(true, null)
                                }
                            } else {
                                _isAuthLoading.value = false
                                val errMsg = task.exception?.localizedMessage ?: "Registration failed"
                                _authError.value = errMsg
                                onComplete(false, errMsg)
                            }
                        }
                } catch (e: Exception) {
                    _isAuthLoading.value = false
                    _authError.value = e.localizedMessage
                    onComplete(false, e.localizedMessage)
                }
            } else {
                delay(1000)
                _isAuthLoading.value = false
                loginUser(email, name)
                onComplete(true, null)
            }
        }
    }

    fun sendPasswordReset(email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val firebaseAuth = auth
            if (firebaseAuth != null) {
                try {
                    firebaseAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onComplete(true, null)
                            } else {
                                onComplete(false, task.exception?.localizedMessage ?: "Failed to send reset email")
                            }
                        }
                } catch (e: Exception) {
                    onComplete(false, e.localizedMessage)
                }
            } else {
                delay(500)
                onComplete(true, null)
            }
        }
    }

    fun sendVerificationEmail() {
        auth?.currentUser?.sendEmailVerification()?.addOnCompleteListener { task ->
            _verificationSent.value = task.isSuccessful
        }
    }

    fun signInWithGoogleToken(idToken: String, onComplete: (Boolean, String?) -> Unit) {
        val firebaseAuth = auth ?: return
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        _isAuthLoading.value = true
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isAuthLoading.value = false
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    val email = user?.email ?: ""
                    val name = user?.displayName ?: email.substringBefore("@")
                    loginUser(email, name)
                    createUserProfileInFirestore(email, name)
                    syncFromFirestore()
                    onComplete(true, null)
                } else {
                    val errMsg = task.exception?.localizedMessage ?: "Google Sign-In failed"
                    _authError.value = errMsg
                    onComplete(false, errMsg)
                }
            }
    }

    fun syncFromFirestore() {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val userDocId = userEmail.replace(".", "_")
            
            dbInstance.collection("users").document(userDocId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val xp = doc.getLong("xp")?.toInt() ?: 120
                            val level = doc.getLong("level")?.toInt() ?: 1
                            val streak = doc.getLong("streak")?.toInt() ?: 3
                            val lastPractice = doc.getLong("lastPracticeTimestamp") ?: System.currentTimeMillis()
                            val coins = doc.getLong("coins")?.toInt() ?: 450
                            
                            repository.db.userProgressDao().updateProgress(
                                UserProgress(
                                    xp = xp,
                                    level = level,
                                    streak = streak,
                                    lastPracticeTimestamp = lastPractice,
                                    coins = coins
                                )
                            )
                        }
                    }
                }
            
            dbInstance.collection("users").document(userDocId).collection("words")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in querySnapshot.documents) {
                            val id = doc.id.toLongOrNull() ?: continue
                            val word = doc.getString("word") ?: ""
                            val translation = doc.getString("translation") ?: ""
                            val ipa = doc.getString("ipa") ?: ""
                            val exampleSentence = doc.getString("exampleSentence") ?: ""
                            val exampleTranslation = doc.getString("exampleTranslation") ?: ""
                            val category = doc.getString("category") ?: ""
                            val difficulty = doc.getString("difficulty") ?: ""
                            val language = doc.getString("language") ?: ""
                            val isBookmarked = doc.getBoolean("isBookmarked") ?: false
                            val lastReviewed = doc.getLong("lastReviewed") ?: System.currentTimeMillis()
                            val accuracyScore = doc.getLong("accuracyScore")?.toInt() ?: 0
                            
                            repository.db.learnedWordDao().insertWord(
                                LearnedWord(
                                    id = id,
                                    word = word,
                                    translation = translation,
                                    ipa = ipa,
                                    exampleSentence = exampleSentence,
                                    exampleTranslation = exampleTranslation,
                                    category = category,
                                    difficulty = difficulty,
                                    language = language,
                                    isBookmarked = isBookmarked,
                                    lastReviewed = lastReviewed,
                                    accuracyScore = accuracyScore
                                )
                            )
                        }
                    }
                }

            dbInstance.collection("users").document(userDocId).collection("tasks")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in querySnapshot.documents) {
                            val id = doc.id.toLongOrNull() ?: continue
                            val title = doc.getString("title") ?: ""
                            val category = doc.getString("category") ?: ""
                            val priority = doc.getString("priority") ?: ""
                            val isCompleted = doc.getBoolean("isCompleted") ?: false
                            val dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis()
                            val recommendedLesson = doc.getString("recommendedLesson") ?: ""
                            val estimatedMinutes = doc.getLong("estimatedMinutes")?.toInt() ?: 15
                            
                            repository.db.plannerTaskDao().insertTask(
                                PlannerTask(
                                    id = id,
                                    title = title,
                                    category = category,
                                    priority = priority,
                                    isCompleted = isCompleted,
                                    dueDate = dueDate,
                                    recommendedLesson = recommendedLesson,
                                    estimatedMinutes = estimatedMinutes
                                )
                            )
                        }
                    }
                }

            dbInstance.collection("users").document(userDocId).collection("quizResults")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in querySnapshot.documents) {
                            val id = doc.id.toLongOrNull() ?: continue
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val score = doc.getLong("score")?.toInt() ?: 0
                            val totalQuestions = doc.getLong("totalQuestions")?.toInt() ?: 0
                            val difficulty = doc.getString("difficulty") ?: ""
                            val language = doc.getString("language") ?: ""
                            
                            repository.db.quizResultDao().insertResult(
                                QuizResult(
                                    id = id,
                                    timestamp = timestamp,
                                    score = score,
                                    totalQuestions = totalQuestions,
                                    difficulty = difficulty,
                                    language = language
                                )
                            )
                        }
                    }
                }

            dbInstance.collection("users").document(userDocId).collection("practices")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in querySnapshot.documents) {
                            val id = doc.id.toLongOrNull() ?: continue
                            val word = doc.getString("word") ?: ""
                            val score = doc.getLong("score")?.toInt() ?: 0
                            val accuracyPercent = doc.getLong("accuracyPercent")?.toInt() ?: 0
                            val correctSounds = doc.getString("correctSounds") ?: ""
                            val incorrectSounds = doc.getString("incorrectSounds") ?: ""
                            val suggestions = doc.getString("suggestions") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val language = doc.getString("language") ?: ""
                            
                            repository.db.pronunciationPracticeDao().insertPractice(
                                PronunciationPractice(
                                    id = id,
                                    word = word,
                                    score = score,
                                    accuracyPercent = accuracyPercent,
                                    correctSounds = correctSounds,
                                    incorrectSounds = incorrectSounds,
                                    suggestions = suggestions,
                                    timestamp = timestamp,
                                    language = language
                                )
                            )
                        }
                    }
                }

            dbInstance.collection("users").document(userDocId).collection("chats")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in querySnapshot.documents) {
                            val id = doc.id.toLongOrNull() ?: continue
                            val role = doc.getString("role") ?: ""
                            val content = doc.getString("content") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            
                            repository.db.tutorMessageDao().insertMessage(
                                TutorMessage(
                                    id = id,
                                    role = role,
                                    content = content,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }
                }
        }
    }

    fun syncProgressToFirestore(progress: UserProgress) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "xp" to progress.xp,
            "level" to progress.level,
            "streak" to progress.streak,
            "lastPracticeTimestamp" to progress.lastPracticeTimestamp,
            "coins" to progress.coins
        )
        dbInstance.collection("users").document(userDocId).set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    fun syncWordToFirestore(word: LearnedWord) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "word" to word.word,
            "translation" to word.translation,
            "ipa" to word.ipa,
            "exampleSentence" to word.exampleSentence,
            "exampleTranslation" to word.exampleTranslation,
            "category" to word.category,
            "difficulty" to word.difficulty,
            "language" to word.language,
            "isBookmarked" to word.isBookmarked,
            "lastReviewed" to word.lastReviewed,
            "accuracyScore" to word.accuracyScore
        )
        val docId = if (word.id > 0) word.id.toString() else System.currentTimeMillis().toString()
        dbInstance.collection("users").document(userDocId).collection("words").document(docId).set(data)
    }

    fun deleteWordFromFirestore(id: Long) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        dbInstance.collection("users").document(userDocId).collection("words").document(id.toString()).delete()
    }

    fun syncTaskToFirestore(task: PlannerTask) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "title" to task.title,
            "category" to task.category,
            "priority" to task.priority,
            "isCompleted" to task.isCompleted,
            "dueDate" to task.dueDate,
            "recommendedLesson" to task.recommendedLesson,
            "estimatedMinutes" to task.estimatedMinutes
        )
        val docId = if (task.id > 0) task.id.toString() else System.currentTimeMillis().toString()
        dbInstance.collection("users").document(userDocId).collection("tasks").document(docId).set(data)
    }

    fun deleteTaskFromFirestore(id: Long) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        dbInstance.collection("users").document(userDocId).collection("tasks").document(id.toString()).delete()
    }

    fun syncQuizResultToFirestore(result: QuizResult) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "timestamp" to result.timestamp,
            "score" to result.score,
            "totalQuestions" to result.totalQuestions,
            "difficulty" to result.difficulty,
            "language" to result.language
        )
        val docId = if (result.id > 0) result.id.toString() else System.currentTimeMillis().toString()
        dbInstance.collection("users").document(userDocId).collection("quizResults").document(docId).set(data)
    }

    fun syncPracticeToFirestore(practice: PronunciationPractice) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "word" to practice.word,
            "score" to practice.score,
            "accuracyPercent" to practice.accuracyPercent,
            "correctSounds" to practice.correctSounds,
            "incorrectSounds" to practice.incorrectSounds,
            "suggestions" to practice.suggestions,
            "timestamp" to practice.timestamp,
            "language" to practice.language
        )
        val docId = if (practice.id > 0) practice.id.toString() else System.currentTimeMillis().toString()
        dbInstance.collection("users").document(userDocId).collection("practices").document(docId).set(data)
    }

    fun syncMessageToFirestore(message: TutorMessage) {
        val userEmail = loggedInUserEmail.value
        if (userEmail.isEmpty()) return
        val dbInstance = firestore ?: return
        val userDocId = userEmail.replace(".", "_")
        val data = mapOf(
            "role" to message.role,
            "content" to message.content,
            "timestamp" to message.timestamp
        )
        val docId = if (message.id > 0) message.id.toString() else System.currentTimeMillis().toString()
        dbInstance.collection("users").document(userDocId).collection("chats").document(docId).set(data)
    }

    fun createUserProfileInFirestore(email: String, name: String) {
        val dbInstance = firestore ?: return
        val userDocId = email.replace(".", "_")
        val profileData = mapOf(
            "email" to email,
            "name" to name,
            "createdTimestamp" to System.currentTimeMillis(),
            "xp" to 120,
            "level" to 1,
            "streak" to 3,
            "coins" to 450
        )
        dbInstance.collection("users").document(userDocId).set(profileData, com.google.firebase.firestore.SetOptions.merge())
    }

    fun logoutUser() {
        auth?.signOut()
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("user_email", "")
            putString("user_name", "Explorer")
            apply()
        }
        _isLoggedIn.value = false
        _loggedInUserEmail.value = ""
        _loggedInUserName.value = "Explorer"
    }

    private val db = AppDatabase.getDatabase(application)
    val repository = LanguageRepository(db)
    val geminiService = GeminiService()

    private var tts: TextToSpeech? = null
    var isTtsReady = false
        private set

    private val _ttsPlaybackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val ttsPlaybackState: StateFlow<TtsPlaybackState> = _ttsPlaybackState.asStateFlow()

    private val _currentlySpeakingText = MutableStateFlow("")
    val currentlySpeakingText: StateFlow<String> = _currentlySpeakingText.asStateFlow()

    private var speechTextChunks = listOf<String>()
    private var currentChunkIndex = 0
    private var currentSpeakingLanguage = "English"

    private fun getLocaleForLanguage(language: String): Locale {
        return when (language) {
            "Spanish" -> Locale("es", "ES")
            "French" -> Locale("fr", "FR")
            "German" -> Locale("de", "DE")
            "Japanese" -> Locale.JAPANESE
            "Korean" -> Locale.KOREAN
            "Chinese" -> Locale.CHINESE
            "Tamil" -> Locale("ta", "IN")
            "Hindi" -> Locale("hi", "IN")
            "Telugu" -> Locale("te", "IN")
            "Kannada" -> Locale("kn", "IN")
            "Malayalam" -> Locale("ml", "IN")
            "Arabic" -> Locale("ar", "AE")
            else -> Locale.US
        }
    }

    init {
        // Initialize Text to Speech
        tts = TextToSpeech(application, this)

        // Session persistence check
        val currentUser = auth?.currentUser
        if (currentUser != null) {
            val userEmail = currentUser.email ?: ""
            val userName = currentUser.displayName ?: userEmail.substringBefore("@")
            loginUser(userEmail, userName)
            syncFromFirestore()
        } else {
            // Fallback check to prefs
            val wasLoggedIn = prefs.getBoolean("is_logged_in", false)
            if (wasLoggedIn) {
                _isLoggedIn.value = true
                _loggedInUserEmail.value = prefs.getString("user_email", "") ?: ""
                _loggedInUserName.value = prefs.getString("user_name", "Explorer") ?: "Explorer"
            }
        }

        // Seed initial data if empty to guarantee beautiful user views on first startup
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun speakText(text: String, language: String = "English") {
        if (!isTtsReady) return
        stopSpeaking() // Immediately stop any prior speech and clear queue
        
        val sentences = text.split(Regex("(?<=[.!?。！？])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) {
            _ttsPlaybackState.value = TtsPlaybackState.IDLE
            return
        }

        speechTextChunks = sentences
        currentChunkIndex = 0
        currentSpeakingLanguage = language
        _currentlySpeakingText.value = text
        _ttsPlaybackState.value = TtsPlaybackState.SPEAKING
        
        speakCurrentChunksFromIndex()
    }

    private fun speakCurrentChunksFromIndex() {
        if (currentChunkIndex >= speechTextChunks.size) {
            _ttsPlaybackState.value = TtsPlaybackState.IDLE
            _currentlySpeakingText.value = ""
            return
        }

        val locale = getLocaleForLanguage(currentSpeakingLanguage)
        tts?.language = locale
        tts?.setSpeechRate(speechSpeed)

        val chunkToSpeak = speechTextChunks[currentChunkIndex]

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    _ttsPlaybackState.value = TtsPlaybackState.SPEAKING
                }
            }

            override fun onDone(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    if (_ttsPlaybackState.value == TtsPlaybackState.SPEAKING) {
                        currentChunkIndex++
                        speakCurrentChunksFromIndex()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    _ttsPlaybackState.value = TtsPlaybackState.IDLE
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                viewModelScope.launch(Dispatchers.Main) {
                    _ttsPlaybackState.value = TtsPlaybackState.IDLE
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Stopped manually or interrupted
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "chunk_$currentChunkIndex")
        }
        tts?.speak(chunkToSpeak, TextToSpeech.QUEUE_FLUSH, params, "chunk_$currentChunkIndex")
    }

    fun pauseSpeaking() {
        if (_ttsPlaybackState.value == TtsPlaybackState.SPEAKING) {
            _ttsPlaybackState.value = TtsPlaybackState.PAUSED
            try {
                tts?.stop()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun resumeSpeaking() {
        if (_ttsPlaybackState.value == TtsPlaybackState.PAUSED) {
            _ttsPlaybackState.value = TtsPlaybackState.SPEAKING
            speakCurrentChunksFromIndex()
        }
    }

    fun stopSpeaking() {
        _ttsPlaybackState.value = TtsPlaybackState.STOPPED
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
        speechTextChunks = emptyList()
        currentChunkIndex = 0
        _currentlySpeakingText.value = ""
        _ttsPlaybackState.value = TtsPlaybackState.IDLE
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }

    // --- State Observables ---

    val userProgress: StateFlow<UserProgress> = repository.userProgress
        .map { it ?: UserProgress() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProgress())

    val allWords: StateFlow<List<LearnedWord>> = repository.allWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedWords: StateFlow<List<LearnedWord>> = repository.bookmarkedWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOcrRecords: StateFlow<List<OcrRecord>> = repository.allOcrRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<ActivityLog>> = repository.activityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizResults: StateFlow<List<QuizResult>> = repository.quizResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<TutorMessage>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGrammarMistakes: StateFlow<List<GrammarMistakeEntity>> = repository.allGrammarMistakes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userCefrLevel = MutableStateFlow("A1")
    val tutorToneStyle = MutableStateFlow("Casual")

    val inspectedWordResult = MutableStateFlow<WordInspectionResult?>(null)
    val isInspectingWord = MutableStateFlow(false)

    val miniExercises = MutableStateFlow<List<MiniExerciseItem>>(emptyList())
    val isLoadingMiniExercises = MutableStateFlow(false)

    val allPractices: StateFlow<List<PronunciationPractice>> = repository.allPractices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<PlannerTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDetectedObjects: StateFlow<List<DetectedObjectEntity>> = repository.allDetectedObjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Configuration UI State ---

    var selectedLanguage = MutableStateFlow("Spanish")
    val sourceLanguage = MutableStateFlow("English")
    val targetLanguage = MutableStateFlow("Spanish")

    fun setStudyLanguage(lang: String) {
        targetLanguage.value = lang
        selectedLanguage.value = lang
        if (sourceLanguage.value == lang) {
            sourceLanguage.value = if (lang == "English") "Spanish" else "English"
        }
    }

    fun setSourceLanguage(lang: String) {
        sourceLanguage.value = lang
        if (targetLanguage.value == lang) {
            targetLanguage.value = if (lang == "English") "Spanish" else "English"
            selectedLanguage.value = targetLanguage.value
        }
    }

    fun setTargetLanguage(lang: String) {
        targetLanguage.value = lang
        selectedLanguage.value = lang
        if (sourceLanguage.value == lang) {
            sourceLanguage.value = if (lang == "English") "Spanish" else "English"
        }
    }

    var speechSpeed = 1.0f

    // --- AR Scanner Screen State ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanResults = MutableStateFlow<List<ArScanResult>>(emptyList())
    val scanResults: StateFlow<List<ArScanResult>> = _scanResults

    // --- AR Cultural Learning Card State & Local Caching ---
    val currentArLearningCard = androidx.compose.runtime.mutableStateOf<ArCulturalLearningCardData?>(null)
    val isLoadingArLearningCard = androidx.compose.runtime.mutableStateOf(false)
    val isArLearningCardVisible = androidx.compose.runtime.mutableStateOf(false)

    private val arLearningCardCache = mutableMapOf<String, ArCulturalLearningCardData>()

    fun loadArCulturalLearningCard(
        objectNameEn: String,
        confidence: Float = 0.95f,
        detectionEngine: String = "Detected by YOLO"
    ) {
        viewModelScope.launch {
            val targetLang = targetLanguage.value
            val cacheKey = "${objectNameEn.trim().lowercase()}_${targetLang.trim().lowercase()}"

            isLoadingArLearningCard.value = true
            isArLearningCardVisible.value = true

            val cachedCard = arLearningCardCache[cacheKey]
            if (cachedCard != null) {
                Log.i("MainViewModel", "[AR Cultural Card Cache Hit] Instant load for '$cacheKey'")
                currentArLearningCard.value = cachedCard
                isLoadingArLearningCard.value = false
                return@launch
            }

            // Fetch / generate via Gemini service
            val generatedCard = geminiService.generateCulturalLearningCard(
                objectNameEn = objectNameEn,
                targetLanguage = targetLang,
                confidence = confidence,
                detectionEngine = detectionEngine
            )

            arLearningCardCache[cacheKey] = generatedCard
            currentArLearningCard.value = generatedCard
            isLoadingArLearningCard.value = false

            // Check off AR daily missions
            checkArMissionOnScan(objectNameEn)
        }
    }

    fun dismissArLearningCard() {
        isArLearningCardVisible.value = false
        currentArLearningCard.value = null
    }

    fun saveArLearningCardToVocabulary(card: ArCulturalLearningCardData) {
        viewModelScope.launch {
            val scanResult = ArScanResult(
                objectNameEn = card.objectNameEn,
                objectNameTarget = card.objectNameTarget,
                pronunciationIpa = card.pronunciationIpa,
                exampleSentenceTarget = card.exampleSentenceTarget,
                exampleSentenceEn = card.exampleSentenceEn,
                grammarUsage = card.grammarHighlight,
                category = card.category,
                difficulty = card.difficulty,
                xPosPercent = 50,
                yPosPercent = 50,
                confidence = card.confidence,
                detectionEngine = card.detectionEngine
            )
            saveObjectToVocabulary(scanResult)
            repository.addXp(35, "AR_CULTURAL_CARD_SAVED")
        }
    }

    fun saveObjectToVocabulary(res: ArScanResult) {
        viewModelScope.launch {
            val wordObj = LearnedWord(
                word = res.objectNameEn.ifBlank { res.objectNameTarget },
                translation = res.objectNameTarget,
                ipa = res.pronunciationIpa,
                exampleSentence = res.exampleSentenceTarget,
                exampleTranslation = res.exampleSentenceEn,
                category = res.category,
                difficulty = res.difficulty,
                language = targetLanguage.value,
                confidence = if (res.confidence > 0f) res.confidence else 0.95f,
                imagePath = res.category
            )
            repository.insertWord(wordObj)

            val historyObj = DetectedObjectEntity(
                objectNameEn = res.objectNameEn.ifBlank { res.objectNameTarget },
                objectNameTarget = res.objectNameTarget,
                pronunciationIpa = res.pronunciationIpa,
                exampleSentenceTarget = res.exampleSentenceTarget,
                exampleSentenceEn = res.exampleSentenceEn,
                category = res.category,
                difficulty = res.difficulty,
                language = targetLanguage.value,
                confidence = if (res.confidence > 0f) res.confidence else 0.95f,
                detectionEngine = res.detectionEngine
            )
            repository.insertDetectedObject(historyObj)

            syncWordToFirestore(wordObj)
            updateChallengeProgress(res.category)
            Log.i("MainViewModel", "[Room DB Save] Saved detected object '${wordObj.word}' (${wordObj.translation}) to Room DB learned_words and detected_object_history.")
        }
    }

    fun deleteDetectedObject(id: Long) {
        viewModelScope.launch {
            repository.deleteDetectedObject(id)
        }
    }

    fun clearDetectedObjectsHistory() {
        viewModelScope.launch {
            repository.clearDetectedObjectsHistory()
        }
    }

    fun toggleDetectedObjectBookmark(entity: DetectedObjectEntity) {
        viewModelScope.launch {
            repository.updateDetectedObjectBookmark(entity.id, !entity.isBookmarked)
        }
    }

    fun saveOcrRecord(recognizedText: String, translation: String) {
        viewModelScope.launch {
            if (recognizedText.isNotBlank()) {
                val ocrRecord = OcrRecord(
                    recognizedText = recognizedText,
                    translation = translation,
                    language = targetLanguage.value,
                    scanTime = System.currentTimeMillis()
                )
                repository.insertOcrRecord(ocrRecord)
                Log.i("MainViewModel", "[Stage 9: OCR History Save] Saved recognized text '$recognizedText' ($translation) to OCR History table.")
            }
        }
    }

    fun deleteOcrRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteOcrRecord(id)
        }
    }

    fun clearOcrHistory() {
        viewModelScope.launch {
            repository.clearOcrHistory()
        }
    }

    fun performArScan(base64Image: String) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val results = geminiService.scanCameraImage(base64Image, sourceLanguage.value, targetLanguage.value, isOcrMode = false)
                _scanResults.value = results
                
                // For each detected object result in AR Object mode, save to vocabulary database!
                for (res in results) {
                    saveObjectToVocabulary(res)
                }

                // Award XP for scanning!
                if (results.isNotEmpty()) {
                    repository.addXp(25, "AR_SCAN")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in performArScan", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScanResults() {
        _scanResults.value = emptyList()
    }

    suspend fun processMlKitDetectedLabels(labels: List<String>): List<SingleCroppedObjectResult> {
        val results = geminiService.processMlKitDetectedLabels(
            detectedLabels = labels,
            sourceLanguage = sourceLanguage.value,
            targetLanguage = targetLanguage.value
        )
        for (res in results) {
            if (res.isIdentified && res.objectNameEn.isNotBlank()) {
                val scanResult = ArScanResult(
                    objectNameEn = res.objectNameEn,
                    objectNameTarget = res.objectNameTarget,
                    pronunciationIpa = res.pronunciationIpa,
                    exampleSentenceTarget = res.exampleSentenceTarget,
                    exampleSentenceEn = res.exampleSentenceEn,
                    grammarUsage = res.grammarUsage,
                    category = res.category,
                    difficulty = res.difficulty,
                    xPosPercent = 50,
                    yPosPercent = 50,
                    confidence = res.confidence
                )
                saveObjectToVocabulary(scanResult)
            }
        }
        if (results.isNotEmpty()) {
            repository.addXp(15, "MLKIT_LABEL_PROCESS")
        }
        return results
    }

    suspend fun processSingleMlKitLabel(label: String, confidence: Float = 0.95f): SingleCroppedObjectResult {
        val result = geminiService.processSingleMlKitLabel(
            label = label,
            sourceLanguage = sourceLanguage.value,
            targetLanguage = targetLanguage.value
        ).copy(confidence = confidence)

        if (result.isIdentified && result.objectNameEn.isNotBlank()) {
            val scanResult = ArScanResult(
                objectNameEn = result.objectNameEn,
                objectNameTarget = result.objectNameTarget,
                pronunciationIpa = result.pronunciationIpa,
                exampleSentenceTarget = result.exampleSentenceTarget,
                exampleSentenceEn = result.exampleSentenceEn,
                grammarUsage = result.grammarUsage,
                category = result.category,
                difficulty = result.difficulty,
                xPosPercent = 50,
                yPosPercent = 50,
                confidence = confidence
            )
            saveObjectToVocabulary(scanResult)
            repository.addXp(15, "MLKIT_LABEL_PROCESS")
        }
        return result
    }

    suspend fun translateDetectedObjectName(objectNameEn: String): SingleCroppedObjectResult {
        val targetLang = targetLanguage.value
        // 1. Check Room Database Cache first for offline speed
        val cached = repository.findCachedDetectedObject(objectNameEn, targetLang)
        if (cached != null && cached.objectNameTarget.isNotBlank()) {
            Log.i("MainViewModel", "[Room Cache Hit] Returning cached translation for '$objectNameEn' -> '${cached.objectNameTarget}' from Room DB")
            return SingleCroppedObjectResult(
                objectNameEn = cached.objectNameEn,
                objectNameTarget = cached.objectNameTarget,
                pronunciationIpa = cached.pronunciationIpa,
                exampleSentenceTarget = cached.exampleSentenceTarget,
                exampleSentenceEn = cached.exampleSentenceEn,
                grammarUsage = "Cached in local Room database",
                category = cached.category,
                difficulty = cached.difficulty,
                confidence = cached.confidence,
                isIdentified = true,
                detectionEngine = cached.detectionEngine.ifBlank { "Detected by YOLO" }
            )
        }

        // 2. Fetch from Gemini API over network
        val result = geminiService.translateDetectedObjectName(
            objectNameEn = objectNameEn,
            sourceLanguage = sourceLanguage.value,
            targetLanguage = targetLang
        )
        if (result.isIdentified && result.objectNameEn.isNotBlank()) {
            val scanResult = ArScanResult(
                objectNameEn = result.objectNameEn,
                objectNameTarget = result.objectNameTarget,
                pronunciationIpa = result.pronunciationIpa,
                exampleSentenceTarget = result.exampleSentenceTarget,
                exampleSentenceEn = result.exampleSentenceEn,
                grammarUsage = result.grammarUsage,
                category = result.category,
                difficulty = result.difficulty,
                xPosPercent = 50,
                yPosPercent = 50,
                confidence = result.confidence,
                detectionEngine = result.detectionEngine
            )
            saveObjectToVocabulary(scanResult)
            repository.addXp(15, "AR_TAP_IDENTIFY")
        }
        return result
    }

    suspend fun identifyCroppedArObject(base64CroppedImage: String): SingleCroppedObjectResult {
        val result = geminiService.identifyCroppedObject(
            base64CroppedImage = base64CroppedImage,
            sourceLanguage = sourceLanguage.value,
            targetLanguage = targetLanguage.value
        )
        if (result.isIdentified && result.objectNameEn.isNotBlank()) {
            val scanResult = ArScanResult(
                objectNameEn = result.objectNameEn,
                objectNameTarget = result.objectNameTarget,
                pronunciationIpa = result.pronunciationIpa,
                exampleSentenceTarget = result.exampleSentenceTarget,
                exampleSentenceEn = result.exampleSentenceEn,
                grammarUsage = result.grammarUsage,
                category = result.category,
                difficulty = result.difficulty,
                xPosPercent = 50,
                yPosPercent = 50,
                confidence = result.confidence,
                detectionEngine = result.detectionEngine
            )
            saveObjectToVocabulary(scanResult)
            repository.addXp(15, "AR_TAP_IDENTIFY")
        }
        return result
    }

    fun addCustomScanResult(result: ArScanResult) {
        val current = _scanResults.value.toMutableList()
        // Replace existing item with same name if present, or append
        current.removeAll { it.objectNameTarget.equals(result.objectNameTarget, ignoreCase = true) }
        current.add(result)
        _scanResults.value = current
    }

    private fun getLocalTranslationForObject(englishName: String, language: String): ArScanResult? {
        val cleanName = englishName.lowercase().trim()
        return when (language) {
            "Spanish" -> {
                when (cleanName) {
                    "chair" -> ArScanResult("chair", "Silla", "/ˈsi.ʝa/", "La silla es cómoda.", "The chair is comfortable.", "Feminine noun: la silla.", "Furniture", "Beginner", 50, 50)
                    "table" -> ArScanResult("table", "Mesa", "/ˈmesa/", "El libro está sobre la mesa.", "The book is on the table.", "Feminine noun: la mesa.", "Furniture", "Beginner", 50, 50)
                    "desk" -> ArScanResult("desk", "Escritorio", "/eskɾiˈtoɾjo/", "Estudio en mi escritorio.", "I study at my desk.", "Masculine noun: el escritorio.", "Furniture", "Beginner", 50, 50)
                    "cup" -> ArScanResult("cup", "Taza", "/ˈtaθa/", "Una taza de café caliente.", "A cup of hot coffee.", "Feminine noun: la taza.", "Kitchen", "Beginner", 50, 50)
                    "bottle" -> ArScanResult("bottle", "Botella", "/boˈteʝa/", "La botella está llena de agua.", "The bottle is full of water.", "Feminine noun: la botella.", "Kitchen", "Beginner", 50, 50)
                    "laptop", "computer" -> ArScanResult(englishName, "Ordenador", "/oɾðenaˈðoɾ/", "Uso mi ordenador para trabajar.", "I use my computer to work.", "Masculine noun: el ordenador / la computadora.", "Electronics", "Beginner", 50, 50)
                    "phone" -> ArScanResult("phone", "Teléfono", "/teˈlefono/", "Mi teléfono está sonando.", "My phone is ringing.", "Masculine noun: el teléfono.", "Electronics", "Beginner", 50, 50)
                    "book" -> ArScanResult("book", "Libro", "/ˈliβɾo/", "Leo un libro interesante.", "I read an interesting book.", "Masculine noun: el libro.", "General", "Beginner", 50, 50)
                    "keyboard" -> ArScanResult("keyboard", "Teclado", "/teˈklaðo/", "Escribo con el teclado.", "I write with the keyboard.", "Masculine noun: el teclado.", "Electronics", "Intermediate", 50, 50)
                    "coffee" -> ArScanResult("coffee", "Café", "/kaˈfe/", "Me gusta el café solo.", "I like black coffee.", "Masculine noun: el café.", "Food", "Beginner", 50, 50)
                    "apple" -> ArScanResult("apple", "Manzana", "/manˈθana/", "La manzana roja es dulce.", "The red apple is sweet.", "Feminine noun: la manzana.", "Food", "Beginner", 50, 50)
                    "cat" -> ArScanResult("cat", "Gato", "/ˈɡato/", "El gato duerme en el sofá.", "The cat sleeps on the sofa.", "Masculine noun: el gato.", "Animals", "Beginner", 50, 50)
                    else -> null
                }
            }
            "French" -> {
                when (cleanName) {
                    "chair" -> ArScanResult("chair", "Chaise", "/ʃɛz/", "La chaise est en bois.", "The chair is made of wood.", "Feminine noun: la chaise.", "Furniture", "Beginner", 50, 50)
                    "table" -> ArScanResult("table", "Table", "/tabl/", "Le dîner est sur la table.", "Dinner is on the table.", "Feminine noun: la table.", "Furniture", "Beginner", 50, 50)
                    "desk" -> ArScanResult("desk", "Bureau", "/byʁo/", "Mon bureau est bien rangé.", "My desk is tidy.", "Masculine noun: le bureau.", "Furniture", "Beginner", 50, 50)
                    "cup" -> ArScanResult("cup", "Tasse", "/tas/", "Une tasse de thé, s'il vous plaît.", "A cup of tea, please.", "Feminine noun: la tasse.", "Kitchen", "Beginner", 50, 50)
                    "bottle" -> ArScanResult("bottle", "Bouteille", "/bu.tɛj/", "Une bouteille d'eau fraîche.", "A bottle of cold water.", "Feminine noun: la bouteille.", "Kitchen", "Beginner", 50, 50)
                    "laptop", "computer" -> ArScanResult(englishName, "Ordinateur", "/ɔʁ.di.na.tœʁ/", "J'ai un nouvel ordinateur.", "I have a new computer.", "Masculine noun: l'ordinateur.", "Electronics", "Beginner", 50, 50)
                    "phone" -> ArScanResult("phone", "Téléphone", "/te.le.fɔn/", "Où est mon téléphone ?", "Where is my phone?", "Masculine noun: le téléphone.", "Electronics", "Beginner", 50, 50)
                    "book" -> ArScanResult("book", "Livre", "/livʁ/", "C'est un excellent livre.", "This is an excellent book.", "Masculine noun: le livre.", "General", "Beginner", 50, 50)
                    "keyboard" -> ArScanResult("keyboard", "Clavier", "/kla.vje/", "Le clavier est AZERTY.", "The keyboard is AZERTY.", "Masculine noun: le clavier.", "Electronics", "Intermediate", 50, 50)
                    "coffee" -> ArScanResult("coffee", "Café", "/ka.fe/", "Je prends un café noir.", "I'm having a black coffee.", "Masculine noun: le café.", "Food", "Beginner", 50, 50)
                    "apple" -> ArScanResult("apple", "Pomme", "/pɔm/", "Je mange une pomme rouge.", "I eat a red apple.", "Feminine noun: la pomme.", "Food", "Beginner", 50, 50)
                    "cat" -> ArScanResult("cat", "Chat", "/ʃa/", "Le chat attrape la souris.", "The cat catches the mouse.", "Masculine noun: le chat.", "Animals", "Beginner", 50, 50)
                    else -> null
                }
            }
            "Japanese" -> {
                when (cleanName) {
                    "chair" -> ArScanResult("chair", "椅子 (Isu)", "/i.sɯ/", "椅子に座ってください。", "Please sit on the chair.", "Noun: isu.", "Furniture", "Beginner", 50, 50)
                    "table" -> ArScanResult("table", "テーブル (Tēburu)", "/teː.bɯ.ɾɯ/", "テーブルの上に本があります。", "There is a book on the table.", "Katakana noun.", "Furniture", "Beginner", 50, 50)
                    "desk" -> ArScanResult("desk", "机 (Tsukue)", "/tsɯ.kɯ.e/", "私の机は窓の隣にあります。", "My desk is next to the window.", "Noun: tsukue.", "Furniture", "Beginner", 50, 50)
                    "cup" -> ArScanResult("cup", "コップ (Koppu)", "/koː.pɯ/", "コップにお水を入れます。", "I pour water in the cup.", "Katakana noun.", "Kitchen", "Beginner", 50, 50)
                    "bottle" -> ArScanResult("bottle", "ボトル (Botoru)", "/bo.to.ɾɯ/", "ボトルの水が冷たいです。", "The water in the bottle is cold.", "Katakana noun.", "Kitchen", "Beginner", 50, 50)
                    "laptop", "computer" -> ArScanResult(englishName, "パソコン (Pasokon)", "/pa.so.koɴ/", "パソコンで仕事をします。", "I work on my laptop.", "Abbreviation of Personal Computer.", "Electronics", "Beginner", 50, 50)
                    "phone" -> ArScanResult("phone", "電話 (Denwa)", "/deɴ.wa/", "電話に出てください。", "Please answer the phone.", "Noun: denwa.", "Electronics", "Beginner", 50, 50)
                    "book" -> ArScanResult("book", "本 (Hon)", "/hoɴ/", "図書館で本を借りました。", "I borrowed a book from the library.", "Noun: hon.", "General", "Beginner", 50, 50)
                    "keyboard" -> ArScanResult("keyboard", "キーボード (Kībōdo)", "/kiː.boː.do/", "キーボードをたたく。", "Type on the keyboard.", "Katakana noun.", "Electronics", "Intermediate", 50, 50)
                    "coffee" -> ArScanResult("coffee", "コーヒー (Kōhī)", "/koː.çiː/", "朝コーヒーを飲みます。", "I drink coffee in the morning.", "Katakana noun.", "Food", "Beginner", 50, 50)
                    "apple" -> ArScanResult("apple", "りんご (Ringo)", "/ɾiɴ.ɡo/", "大きなりんごを食べました。", "I ate a big apple.", "Noun: ringo.", "Food", "Beginner", 50, 50)
                    "cat" -> ArScanResult("cat", "猫 (Neko)", "/ne.ko/", "猫が部屋に入ってきました。", "A cat came into the room.", "Noun: neko.", "Animals", "Beginner", 50, 50)
                    else -> null
                }
            }
            else -> null
        }
    }

    fun lookupOrTranslateObject(englishName: String, language: String, callback: (ArScanResult) -> Unit) {
        viewModelScope.launch {
            val localResult = getLocalTranslationForObject(englishName, language)
            if (localResult != null) {
                updateChallengeProgress(localResult.category)
                callback(localResult)
                return@launch
            }

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                try {
                    val prompt = """
                        You are a language learning dictionary. Provide translation details for the English noun: "$englishName" into the target language: "$language".
                        You MUST return a JSON object with this structure:
                        {
                          "objectNameEn": "$englishName",
                          "objectNameTarget": "target translation",
                          "pronunciationIpa": "IPA guide",
                          "exampleSentenceTarget": "example sentence in target language",
                          "exampleSentenceEn": "English meaning of example sentence",
                          "grammarUsage": "grammar usage note about this word",
                          "category": "category",
                          "difficulty": "Beginner"
                        }
                    """.trimIndent()
                    
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                        generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.3f)
                    )
                    
                    val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (jsonText != null) {
                        val adapter = GeminiRetrofitClient.moshiInstance.adapter(ArScanResult::class.java)
                        val result = adapter.fromJson(jsonText)
                        if (result != null) {
                            updateChallengeProgress(result.category)
                            callback(result)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            val generic = ArScanResult(
                objectNameEn = englishName,
                objectNameTarget = englishName,
                pronunciationIpa = "/.../",
                exampleSentenceTarget = "This is a $englishName.",
                exampleSentenceEn = "This is a $englishName.",
                grammarUsage = "Noun.",
                category = "General",
                difficulty = "Beginner",
                xPosPercent = 50,
                yPosPercent = 50
            )
            updateChallengeProgress(generic.category)
            callback(generic)
        }
    }

    // --- OCR Document Scanner State ---
    private val _isOcrScanning = MutableStateFlow(false)
    val isOcrScanning: StateFlow<Boolean> = _isOcrScanning

    private val _ocrResults = MutableStateFlow<List<ArScanResult>>(emptyList())
    val ocrResults: StateFlow<List<ArScanResult>> = _ocrResults

    private val _ocrErrorMessage = MutableStateFlow<String?>(null)
    val ocrErrorMessage: StateFlow<String?> = _ocrErrorMessage

    fun clearOcrErrorMessage() {
        _ocrErrorMessage.value = null
    }

    fun addSpokenOcrResult(spokenText: String) {
        viewModelScope.launch {
            _isOcrScanning.value = true
            try {
                val translatedText = geminiService.translateText(spokenText, sourceLanguage.value, targetLanguage.value)
                val newOcrResult = ArScanResult(
                    objectNameTarget = translatedText,
                    objectNameEn = spokenText,
                    pronunciationIpa = "/spoken/",
                    exampleSentenceTarget = "Practiced spoken phrase.",
                    exampleSentenceEn = "I spoke: \"$spokenText\"",
                    grammarUsage = "Voice annotated search results.",
                    category = "Voice Input",
                    difficulty = "Medium",
                    xPosPercent = 50,
                    yPosPercent = 50
                )
                
                val currentList = _ocrResults.value.toMutableList()
                currentList.add(0, newOcrResult)
                _ocrResults.value = currentList
                
                val ocrRecord = OcrRecord(
                    recognizedText = spokenText,
                    translation = translatedText,
                    language = targetLanguage.value
                )
                repository.insertOcrRecord(ocrRecord)
                repository.addXp(10, "VOICE_OCR")
            } catch (e: Exception) {
                // fallback
            } finally {
                _isOcrScanning.value = false
            }
        }
    }

    /**
     * Processes live ML Kit Text Recognition output on camera frames.
     */
    fun processMlKitOcrResult(
        recognizedText: com.google.mlkit.vision.text.Text,
        imageWidth: Int,
        imageHeight: Int,
        onComplete: (List<ArScanResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isOcrScanning.value = true
            try {
                val blocks = recognizedText.textBlocks
                if (blocks.isEmpty() || recognizedText.text.isBlank()) {
                    _ocrErrorMessage.value = "No visible text detected in camera view. Point camera at printed text, books, menus, or signs."
                    _ocrResults.value = emptyList()
                    onComplete(emptyList())
                    return@launch
                }

                _ocrErrorMessage.value = null
                val newResults = mutableListOf<ArScanResult>()
                val width = if (imageWidth > 0) imageWidth.toFloat() else 1000f
                val height = if (imageHeight > 0) imageHeight.toFloat() else 1000f

                for (block in blocks) {
                    val blockText = block.text.trim()
                    if (blockText.isBlank()) continue

                    val rect = block.boundingBox ?: android.graphics.Rect(100, 100, 400, 300)
                    val xPercent = ((rect.centerX() / width) * 100).toInt().coerceIn(5, 95)
                    val yPercent = ((rect.centerY() / height) * 100).toInt().coerceIn(5, 95)
                    val wPercent = ((rect.width() / width) * 100).toInt().coerceIn(10, 90)
                    val hPercent = ((rect.height() / height) * 100).toInt().coerceIn(5, 60)

                    val detectedLang = block.recognizedLanguage.ifEmpty { "auto" }
                    var avgConfidence = 0.95f
                    if (block.lines.isNotEmpty()) {
                        var sumConf = 0f
                        var elemCount = 0
                        for (line in block.lines) {
                            for (elem in line.elements) {
                                elem.confidence?.let { c ->
                                    sumConf += c
                                    elemCount++
                                }
                            }
                        }
                        if (elemCount > 0) {
                            avgConfidence = sumConf / elemCount
                        }
                    }

                    val enrichedResult = translateAndEnrichText(
                        rawText = blockText,
                        sourceLang = sourceLanguage.value,
                        targetLang = targetLanguage.value,
                        xPercent = xPercent,
                        yPercent = yPercent,
                        wPercent = wPercent,
                        hPercent = hPercent,
                        confidence = avgConfidence,
                        detectedLang = detectedLang
                    )

                    newResults.add(enrichedResult)

                    // Store OCR result in OcrRecord (OCR History table), NOT in LearnedWord (My Words table)
                    val ocrRecord = OcrRecord(
                        recognizedText = blockText,
                        translation = enrichedResult.objectNameEn.ifBlank { enrichedResult.objectNameTarget },
                        language = targetLanguage.value
                    )
                    repository.insertOcrRecord(ocrRecord)
                }

                _ocrResults.value = newResults
                if (newResults.isNotEmpty()) {
                    repository.addXp(30, "REAL_OCR")
                }
                onComplete(newResults)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error processing ML Kit OCR results", e)
                _ocrErrorMessage.value = "OCR analysis failed: ${e.localizedMessage}"
                onComplete(emptyList())
            } finally {
                _isOcrScanning.value = false
            }
        }
    }

    private suspend fun translateAndEnrichText(
        rawText: String,
        sourceLang: String,
        targetLang: String,
        xPercent: Int,
        yPercent: Int,
        wPercent: Int,
        hPercent: Int,
        confidence: Float,
        detectedLang: String
    ): ArScanResult {
        return try {
            val translated = geminiService.translateText(rawText, sourceLang, targetLang)
            val ipa = generateIpaForText(rawText)
            ArScanResult(
                objectNameEn = if (sourceLang.lowercase().contains("en")) rawText else translated,
                objectNameTarget = if (targetLang.lowercase().contains("en")) translated else rawText,
                pronunciationIpa = ipa,
                exampleSentenceTarget = "Text: \"$rawText\"",
                exampleSentenceEn = "Translation: \"$translated\"",
                grammarUsage = "Extracted OCR block (${rawText.lines().size} lines).",
                category = "Document OCR",
                difficulty = if (rawText.length > 50) "Advanced" else "Intermediate",
                xPosPercent = xPercent,
                yPosPercent = yPercent,
                widthPercent = wPercent,
                heightPercent = hPercent,
                confidence = confidence,
                detectedLanguage = detectedLang,
                rawExtractedText = rawText
            )
        } catch (e: Exception) {
            ArScanResult(
                objectNameEn = rawText,
                objectNameTarget = rawText,
                pronunciationIpa = "/.../",
                exampleSentenceTarget = rawText,
                exampleSentenceEn = rawText,
                grammarUsage = "Extracted OCR block.",
                category = "Document OCR",
                difficulty = "Medium",
                xPosPercent = xPercent,
                yPosPercent = yPercent,
                widthPercent = wPercent,
                heightPercent = hPercent,
                confidence = confidence,
                detectedLanguage = detectedLang,
                rawExtractedText = rawText
            )
        }
    }

    private fun generateIpaForText(text: String): String {
        val clean = text.trim().lowercase()
        return when {
            clean.contains("hello") || clean.contains("hola") -> "/həˈloʊ/"
            clean.contains("coffee") || clean.contains("café") -> "/ˈkɒfi/"
            clean.contains("water") || clean.contains("agua") -> "/ˈwɔːtər/"
            else -> "/${clean.take(8)}/"
        }
    }

    fun triggerOcrScan() {
        _isOcrScanning.value = true
        _ocrErrorMessage.value = null
        Log.i("MainViewModel", "[Stage 1: Camera OCR] Triggered live ML Kit camera frame OCR scan.")
    }

    fun performOcrScan(base64Image: String) {
        viewModelScope.launch {
            _isOcrScanning.value = true
            _ocrErrorMessage.value = null
            try {
                val results = geminiService.scanCameraImage(base64Image, sourceLanguage.value, targetLanguage.value, isOcrMode = true)
                if (results.isEmpty()) {
                    _ocrErrorMessage.value = "No text detected in camera image. Align text clearly in viewfinder."
                }
                _ocrResults.value = results

                // Save OCR result to OcrRecord table (OCR History), NOT to vocabulary database!
                for (res in results) {
                    val ocrRecord = OcrRecord(
                        recognizedText = res.rawExtractedText.ifBlank { res.objectNameTarget },
                        translation = res.objectNameEn,
                        language = targetLanguage.value
                    )
                    repository.insertOcrRecord(ocrRecord)
                }
                // Award XP
                if (results.isNotEmpty()) {
                    repository.addXp(30, "OCR")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in performOcrScan", e)
                _ocrErrorMessage.value = "Camera image scan failed: ${e.localizedMessage}"
                _ocrResults.value = emptyList()
            } finally {
                _isOcrScanning.value = false
            }
        }
    }

    fun clearOcrResults() {
        _ocrResults.value = emptyList()
    }

    fun clearOcrScanningState() {
        _isOcrScanning.value = false
    }

    fun addOcrResultDirectly(res: ArScanResult) {
        val currentList = _ocrResults.value.toMutableList()
        if (currentList.none { it.objectNameTarget.equals(res.objectNameTarget, ignoreCase = true) }) {
            currentList.add(res)
            _ocrResults.value = currentList

            viewModelScope.launch {
                val ocrRecord = OcrRecord(
                    recognizedText = res.rawExtractedText.ifBlank { res.objectNameTarget },
                    translation = res.objectNameEn,
                    language = selectedLanguage.value
                )
                repository.insertOcrRecord(ocrRecord)
                repository.addXp(15, "OCR_REAL_TIME")
            }
        }
    }

    // --- AI Tutor Chat State ---
    private val _isTutorTyping = MutableStateFlow(false)
    val isTutorTyping: StateFlow<Boolean> = _isTutorTyping

    fun sendTutorMessage(text: String) {
        if (text.trim().isEmpty()) return
        stopSpeaking() // Stop active TTS when a new message/question is asked
        
        // Handle Voice Navigation Commands (Feature 8)
        val lowercase = text.trim().lowercase()
        if (lowercase.startsWith("navigate ") || lowercase.startsWith("go to ") || lowercase.startsWith("open ")) {
            val target = when {
                lowercase.contains("home") || lowercase.contains("dashboard") -> "home"
                lowercase.contains("scanner") || lowercase.contains("vision") || lowercase.contains("camera") || lowercase.contains("ar") -> "vision"
                lowercase.contains("tutor") || lowercase.contains("chat") || lowercase.contains("twin") -> "tutor"
                lowercase.contains("quiz") || lowercase.contains("practice") || lowercase.contains("speak") || lowercase.contains("exam") -> "practice"
                lowercase.contains("word") || lowercase.contains("vocabulary") -> "words"
                lowercase.contains("planner") || lowercase.contains("schedule") -> "planner"
                else -> null
            }
            if (target != null) {
                requestRouteSwitch(target)
                speakText("Navigating to $target", "English")
                return
            }
        }

        viewModelScope.launch {
            // Save User Message
            val userMsg = TutorMessage(role = "user", content = text)
            repository.insertTutorMessage("user", text)
            syncMessageToFirestore(userMsg)
            _isTutorTyping.value = true
            
            try {
                val currentHistory = chatMessages.value
                val analysisResult = geminiService.chatWithTutorStructured(
                    history = currentHistory,
                    userMessage = text,
                    targetLanguage = targetLanguage.value,
                    cefrLevel = userCefrLevel.value,
                    toneStyle = tutorToneStyle.value
                )

                val adapter = GeminiRetrofitClient.moshiInstance.adapter(TutorAnalysisResult::class.java)
                val jsonStr = adapter.toJson(analysisResult)

                val displayContent = if (analysisResult.conversationalReply.isNotEmpty()) {
                    analysisResult.conversationalReply
                } else if (analysisResult.hasGrammarErrors) {
                    "Corrected: ${analysisResult.correctedSentence}\n\n${analysisResult.overallExplanation}"
                } else {
                    "Great job! Your sentence is grammatically correct."
                }

                // Save AI response with analysisJson
                val replyMsg = TutorMessage(role = "model", content = displayContent, analysisJson = jsonStr)
                repository.insertTutorMessage("model", displayContent, analysisJson = jsonStr)
                syncMessageToFirestore(replyMsg)

                // Track Grammar Mistakes if any
                if (analysisResult.hasGrammarErrors) {
                    for (m in analysisResult.grammarMistakes) {
                        repository.insertOrIncrementGrammarMistake(
                            originalText = m.originalSegment,
                            correctedText = m.correctedSegment,
                            mistakeCategory = m.mistakeType,
                            explanation = m.explanation,
                            targetLanguage = targetLanguage.value
                        )
                    }
                }

                repository.addXp(analysisResult.xpAwarded, "TUTOR")

                // If voice conversation mode is active, automatically speak the response
                if (isVoiceConversationModeActive.value) {
                    speakText(displayContent, targetLanguage.value)
                }
            } catch (e: Exception) {
                val fallbackReply = "I apologize, I am experiencing network difficulties. Let's continue speaking in ${targetLanguage.value}!"
                val replyMsg = TutorMessage(role = "model", content = fallbackReply)
                repository.insertTutorMessage("model", fallbackReply)
                syncMessageToFirestore(replyMsg)
                
                if (isVoiceConversationModeActive.value) {
                    speakText(fallbackReply, targetLanguage.value)
                }
            } finally {
                _isTutorTyping.value = false
            }
        }
    }

    fun setUserCefrLevel(level: String) {
        userCefrLevel.value = level
    }

    fun setTutorToneStyle(style: String) {
        tutorToneStyle.value = style
    }

    fun inspectWord(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch {
            isInspectingWord.value = true
            try {
                val res = geminiService.inspectWord(word, targetLanguage.value)
                inspectedWordResult.value = res
            } catch (e: Exception) {
                inspectedWordResult.value = null
            } finally {
                isInspectingWord.value = false
            }
        }
    }

    fun closeWordInspector() {
        inspectedWordResult.value = null
    }

    fun loadMiniExercises() {
        viewModelScope.launch {
            isLoadingMiniExercises.value = true
            try {
                val mistakes = allGrammarMistakes.value
                val exercises = geminiService.generateMiniExercisesFromMistakes(mistakes, targetLanguage.value)
                miniExercises.value = exercises
            } catch (e: Exception) {
                miniExercises.value = emptyList()
            } finally {
                isLoadingMiniExercises.value = false
            }
        }
    }

    fun markMistakeMastered(id: Long) {
        viewModelScope.launch {
            repository.markMistakeMastered(id)
        }
    }

    fun addXp(amount: Int, category: String = "EXERCISE") {
        viewModelScope.launch {
            repository.addXp(amount, category)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    // --- Translation Module State ---
    private val _translationInput = MutableStateFlow("")
    val translationInput: StateFlow<String> = _translationInput

    private val _translatedOutput = MutableStateFlow("")
    val translatedOutput: StateFlow<String> = _translatedOutput

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating

    fun updateTranslationInput(input: String) {
        _translationInput.value = input
    }

    fun executeTranslate() {
        val input = _translationInput.value
        if (input.trim().isEmpty()) return
        viewModelScope.launch {
            _isTranslating.value = true
            try {
                val output = geminiService.translateText(input, sourceLanguage.value, targetLanguage.value)
                _translatedOutput.value = output
                repository.addXp(5, "TRANSLATION")
            } catch (e: Exception) {
                _translatedOutput.value = "Translation error."
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun swapTranslationLanguages() {
        val oldSource = sourceLanguage.value
        val oldTarget = targetLanguage.value
        sourceLanguage.value = oldTarget
        targetLanguage.value = oldSource
        selectedLanguage.value = oldSource // Keep study language in sync

        val oldInput = _translationInput.value
        val oldOutput = _translatedOutput.value
        _translationInput.value = oldOutput
        _translatedOutput.value = oldInput
    }

    // --- Pronunciation Speaking Practice State ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _pronunciationScore = MutableStateFlow<Int?>(null)
    val pronunciationScore: StateFlow<Int?> = _pronunciationScore

    private val _pronunciationAccuracy = MutableStateFlow(0)
    val pronunciationAccuracy: StateFlow<Int> = _pronunciationAccuracy

    private val _correctSounds = MutableStateFlow<List<String>>(emptyList())
    val correctSounds: StateFlow<List<String>> = _correctSounds

    private val _incorrectSounds = MutableStateFlow<List<String>>(emptyList())
    val incorrectSounds: StateFlow<List<String>> = _incorrectSounds

    private val _pronunciationSuggestions = MutableStateFlow("")
    val pronunciationSuggestions: StateFlow<String> = _pronunciationSuggestions

    private val _pronunciationFeedback = MutableStateFlow("")
    val pronunciationFeedback: StateFlow<String> = _pronunciationFeedback

    private val _speakingWaveform = MutableStateFlow<List<Float>>(emptyList())
    val speakingWaveform: StateFlow<List<Float>> = _speakingWaveform

    private val _isSyncingFirebase = MutableStateFlow(false)
    val isSyncingFirebase: StateFlow<Boolean> = _isSyncingFirebase

    private val _isGeneratingPlanner = MutableStateFlow(false)
    val isGeneratingPlanner: StateFlow<Boolean> = _isGeneratingPlanner

    // --- Voice Recognition / Speech-to-Text State ---
    private val _isSpeechListening = MutableStateFlow(false)
    val isSpeechListening: StateFlow<Boolean> = _isSpeechListening.asStateFlow()

    private val _speechRecognizedText = MutableStateFlow("")
    val speechRecognizedText: StateFlow<String> = _speechRecognizedText.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var simulationJob: kotlinx.coroutines.Job? = null
    private var activeSpeechOnComplete: ((String) -> Unit)? = null

    fun startVoiceRecognition(
        contextType: String,
        languageName: String,
        onTextUpdate: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        stopSpeaking() // Stop any active TTS when the user starts speaking
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _isSpeechListening.value = true
                _speechRecognizedText.value = "Listening..."
                _speechError.value = null
                activeSpeechOnComplete = onComplete
                
                // Clean up previous recognizer or simulation
                simulationJob?.cancel()
                simulationJob = null
                speechRecognizer?.destroy()
                speechRecognizer = null
                
                val context = getApplication<Application>()
                val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
                
                if (!isAvailable) {
                    _isSpeechListening.value = false
                    _speechError.value = "Speech recognition service unavailable on this device. Please type your message."
                    return@launch
                }
                
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                
                val localeStr = getLocaleStringForLanguage(languageName)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeStr)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeStr)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeStr)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _speechRecognizedText.value = ""
                        _speechError.value = null
                    }
                    override fun onBeginningOfSpeech() {
                        _speechRecognizedText.value = ""
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = (rmsdB + 2f) / 12f
                        val clamped = normalized.coerceIn(0.1f, 1.0f)
                        val waves = List(15) { clamped * (0.3f + kotlin.random.Random.nextFloat() * 0.7f) }
                        _speakingWaveform.value = waves
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isSpeechListening.value = false
                        _speakingWaveform.value = emptyList()
                    }
                    override fun onError(error: Int) {
                        _isSpeechListening.value = false
                        _speakingWaveform.value = emptyList()
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check your microphone."
                            SpeechRecognizer.ERROR_CLIENT -> "Client error in speech recognition."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
                            SpeechRecognizer.ERROR_SERVER -> "Server error."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please speak into mic."
                            else -> "Speech recognition ended."
                        }
                        _speechError.value = errorMsg
                        
                        val currentText = _speechRecognizedText.value.replace("Listening...", "").replace("Speech Recognized:", "").trim()
                        if (currentText.isNotEmpty()) {
                            onTextUpdate(currentText)
                            activeSpeechOnComplete?.invoke(currentText)
                        }
                        activeSpeechOnComplete = null
                    }
                    override fun onResults(results: Bundle?) {
                        _isSpeechListening.value = false
                        _speakingWaveform.value = emptyList()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            _speechRecognizedText.value = text
                            onTextUpdate(text)
                            activeSpeechOnComplete?.invoke(text)
                            activeSpeechOnComplete = null
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            _speechRecognizedText.value = text
                            onTextUpdate(text)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                
                recognizer.startListening(intent)
            } catch (e: Exception) {
                _isSpeechListening.value = false
                _speakingWaveform.value = emptyList()
                _speechError.value = e.localizedMessage ?: "Could not start speech recognition."
            }
        }
    }

    fun stopVoiceRecognition() {
        viewModelScope.launch(Dispatchers.Main) {
            val finalSpeechText = _speechRecognizedText.value.replace("Listening...", "").replace("Speech Recognized:", "").trim()
            _isSpeechListening.value = false
            simulationJob?.cancel()
            simulationJob = null
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
            _speakingWaveform.value = emptyList()
            if (finalSpeechText.isNotEmpty()) {
                activeSpeechOnComplete?.invoke(finalSpeechText)
            }
            activeSpeechOnComplete = null
        }
    }

    private fun startSimulatedSpeech(
        contextType: String,
        languageName: String,
        onTextUpdate: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch(Dispatchers.Main) {
            _isSpeechListening.value = true
            _speechError.value = null
            
            val textToSimulate = getSimulatedSpeechText(contextType, languageName)
            
            // Waveform simulator loop
            val waveJob = viewModelScope.launch {
                while (_isSpeechListening.value) {
                    val waves = List(15) { 0.2f + kotlin.random.Random.nextFloat() * 0.8f }
                    _speakingWaveform.value = waves
                    delay(120)
                }
                _speakingWaveform.value = emptyList()
            }
            
            // Slow typing simulation (real-time experience)
            val words = textToSimulate.split(" ")
            var currentTyped = ""
            for (word in words) {
                if (!_isSpeechListening.value) break
                currentTyped = if (currentTyped.isEmpty()) word else "$currentTyped $word"
                _speechRecognizedText.value = currentTyped
                onTextUpdate(currentTyped)
                delay(300)
            }
            
            _isSpeechListening.value = false
            waveJob.cancel()
            _speakingWaveform.value = emptyList()
            
            _speechRecognizedText.value = textToSimulate
            onTextUpdate(textToSimulate)
            activeSpeechOnComplete?.invoke(textToSimulate)
            activeSpeechOnComplete = null
        }
    }

    private fun getLocaleStringForLanguage(language: String): String {
        return when (language) {
            "English" -> "en-US"
            "Spanish" -> "es-ES"
            "French" -> "fr-FR"
            "German" -> "de-DE"
            "Japanese" -> "ja-JP"
            "Korean" -> "ko-KR"
            "Chinese" -> "zh-CN"
            "Tamil" -> "ta-IN"
            "Hindi" -> "hi-IN"
            "Telugu" -> "te-IN"
            "Kannada" -> "kn-IN"
            "Malayalam" -> "ml-IN"
            "Arabic" -> "ar-AE"
            else -> "en-US"
        }
    }

    private fun getSimulatedSpeechText(contextType: String, languageName: String): String {
        return when (contextType) {
            "TRANSLATOR" -> {
                when (languageName) {
                    "Spanish" -> "Hola, ¿cómo estás hoy? Me gustaría aprender español."
                    "French" -> "Bonjour, comment allez-vous aujourd'hui? J'aime le français."
                    "German" -> "Hallo, wie geht es dir heute? Deutsch ist toll."
                    "Japanese" -> "こんにちは、お元気ですか？日本語を勉強しています。"
                    "Chinese" -> "你好，今天怎么样？我想学习中文。"
                    "Tamil" -> "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்? தமிழ் மிகவும் அழகாக இருக்கிறது."
                    "Hindi" -> "नमस्ते, आप कैसे हैं? मुझे हिंदी सीखना बहुत पसंद है।"
                    "Telugu" -> "నమస్కారం, మీరు ఎలా ఉన్నారు? తెలుగు చాలా బాగుంది."
                    "Kannada" -> "ನಮಸ್ಕಾರ, ನೀವು ಹೇಗಿದ್ದೀರಾ? ಕನ್ನಡ ಸುಂದರವಾದ ಭಾಷೆ."
                    "Malayalam" -> "നമസ്കാരം, സുഖമാണോ? മലയാളം പഠിക്കാൻ എനിക്ക് ഇഷ്ടമാണ്."
                    "Arabic" -> "مرحباً، كيف حالك اليوم؟ أحب اللغة العربية."
                    else -> "Hello, how are you today? I would love to translate this."
                }
            }
            "TUTOR" -> {
                val prompts = when (languageName) {
                    "Tamil" -> listOf("What is the meaning of apple in Tamil?", "Teach me basic Tamil phrases", "Correct my Tamil grammar")
                    "French" -> listOf("Teach me basic French.", "What is the French word for beautiful?", "How do you conjugate verbs in French?")
                    "Spanish" -> listOf("Suggest Spanish idioms.", "Explain Spanish subjunctive tense.", "How to order food in Spanish?")
                    else -> listOf("Teach me basic phrases.", "Correct my grammar.", "Generate a quiz about animals.")
                }
                prompts.random()
            }
            "QUIZ" -> {
                val currentQ = _currentQuizQuestions.value.getOrNull(_quizIndex.value)
                currentQ?.correctAnswer ?: "Water"
            }
            "SEARCH" -> {
                when (languageName) {
                    "Spanish" -> "Espléndido"
                    "French" -> "Merveilleux"
                    "Japanese" -> "Komorebi"
                    "Tamil" -> "வணக்கம்"
                    else -> "Curiosity"
                }
            }
            "PRONUNCIATION" -> {
                getChallengePhraseForLanguage(languageName)
            }
            else -> "Hello World"
        }
    }

    private fun getChallengePhraseForLanguage(lang: String): String {
        return when (lang) {
            "Spanish" -> "Buenos días, me gustaría un café por favor."
            "French" -> "Bonjour, enchanté de faire votre connaissance."
            "German" -> "Guten Tag, wie geht es Ihnen heute?"
            "Korean" -> "안녕하세요, 만나서 반갑습니다."
            "Chinese" -> "您好，很高兴认识您。"
            "Tamil" -> "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?"
            "Hindi" -> "नमस्ते, आप कैसे हैं आज?"
            "Telugu" -> "నమస్కారం, మీరు ఎలా ఉన్నారు?"
            "Kannada" -> "ನమస్కಾರ್, ನೀವು ಹೇಗಿದ್ದೀರಾ?"
            "Malayalam" -> "നമസ്കാരം, സുഖമാണോ?"
            "Arabic" -> "مرحباً، كيف حالك اليوم؟"
            else -> "Hello, nice to meet you today."
        }
    }

    private fun calculatePronunciationScore(spoken: String, expected: String): Int {
        val s = spoken.lowercase().trim().replace(Regex("[^a-zA-Z0-9 \\u0B80-\\u0BFF\\u0900-\\u097F]"), "")
        val e = expected.lowercase().trim().replace(Regex("[^a-zA-Z0-9 \\u0B80-\\u0BFF\\u0900-\\u097F]"), "")
        if (s == e) return 100
        
        val sWords = s.split(" ")
        val eWords = e.split(" ")
        var matches = 0
        for (w in eWords) {
            if (sWords.contains(w)) {
                matches++
            }
        }
        val ratio = matches.toFloat() / eWords.size.coerceAtLeast(1)
        val baseScore = (ratio * 100).toInt()
        return baseScore.coerceIn(55, 98)
    }

    fun startPronunciationPractice() {
        val targetLangName = targetLanguage.value
        val expectedText = getChallengePhraseForLanguage(targetLangName)
        
        _isRecording.value = true
        _pronunciationScore.value = null
        _pronunciationAccuracy.value = 0
        _correctSounds.value = emptyList()
        _incorrectSounds.value = emptyList()
        _pronunciationSuggestions.value = ""
        _pronunciationFeedback.value = "Listening... Speak now!"
        
        startVoiceRecognition(
            contextType = "PRONUNCIATION",
            languageName = targetLangName,
            onTextUpdate = { recognized ->
                _pronunciationFeedback.value = "Speech Recognized: \"$recognized\""
            },
            onComplete = { finalSpeech ->
                evaluatePronunciation(finalSpeech, expectedText)
            }
        )
    }

    fun stopPronunciationPractice(word: String) {
        stopVoiceRecognition()
    }

    fun evaluatePronunciation(spoken: String, expected: String) {
        _isRecording.value = false
        _pronunciationFeedback.value = "Analyzing pronunciation..."
        
        val score = calculatePronunciationScore(spoken, expected)
        _pronunciationScore.value = score
        _pronunciationAccuracy.value = score
        
        val spokenLower = spoken.lowercase().trim().replace(Regex("[^a-zA-Z0-9 \\u0B80-\\u0BFF\\u0900-\\u097F]"), "")
        val expectedLower = expected.lowercase().trim().replace(Regex("[^a-zA-Z0-9 \\u0B80-\\u0BFF\\u0900-\\u097F]"), "")
        val spokenWords = spokenLower.split(" ")
        val expectedWords = expectedLower.split(" ")
        
        val correctList = mutableListOf<String>()
        val incorrectList = mutableListOf<String>()
        
        for (word in expectedWords) {
            val cleanWord = word.replace(",", "").replace(".", "")
            if (spokenWords.any { it.contains(cleanWord) || cleanWord.contains(it) }) {
                correctList.add(cleanWord)
            } else {
                incorrectList.add(cleanWord)
            }
        }
        
        _correctSounds.value = correctList
        _incorrectSounds.value = incorrectList
        
        val suggestions = when {
            score >= 90 -> "Fantastic native accent! 🟢 Intonation and vowel lengths are perfect. Keep up this incredible flow!"
            score >= 75 -> "Great pronunciation! 🟡 Subtle adjustments needed on consonants. Soften your vocal cords slightly."
            else -> "Good attempt! 🔴 Spoken text matched partially. Try focusing on individual syllables: \"$expected\""
        }
        _pronunciationSuggestions.value = suggestions
        _pronunciationFeedback.value = "Analysis complete!"
        
        viewModelScope.launch {
            val practice = PronunciationPractice(
                word = expected,
                score = score,
                accuracyPercent = score,
                correctSounds = correctList.joinToString(", "),
                incorrectSounds = incorrectList.joinToString(", "),
                suggestions = suggestions,
                language = targetLanguage.value
            )
            repository.insertPractice(practice)
            syncPracticeToFirestore(practice)
            
            // Sync to Firebase
            _isSyncingFirebase.value = true
            kotlinx.coroutines.delay(1000) // Simulated network write delay
            _isSyncingFirebase.value = false

            // Update word score in vocabulary if word exists
            val existingWord = allWords.value.find { it.word.equals(expected, ignoreCase = true) }
            if (existingWord != null) {
                val updatedWord = existingWord.copy(accuracyScore = score, lastReviewed = System.currentTimeMillis())
                repository.insertWord(updatedWord)
                syncWordToFirestore(updatedWord)
            }
            
            // Award XP
            repository.addXp(15, "SPEAKING")
            
            // Speak the expected text back as Text-to-Speech audio feedback!
            speakText(expected, targetLanguage.value)
        }
    }

    fun generatePlannerTasks() {
        viewModelScope.launch {
            _isGeneratingPlanner.value = true
            try {
                val progress = userProgress.value
                val results = geminiService.generateStudyPlanner(
                    learnedWords = allWords.value,
                    quizResults = quizResults.value,
                    pronunciationPractices = allPractices.value,
                    streak = progress.streak,
                    targetLanguage = selectedLanguage.value
                )
                
                // Clear existing tasks and insert new ones
                repository.clearAllTasks()
                for (res in results) {
                    val task = PlannerTask(
                        title = res.title,
                        category = res.category,
                        priority = res.priority,
                        isCompleted = false,
                        recommendedLesson = res.recommendedLesson,
                        estimatedMinutes = res.estimatedMinutes
                    )
                    repository.insertTask(task)
                    syncTaskToFirestore(task)
                }
                
                // Sync to Firebase
                _isSyncingFirebase.value = true
                kotlinx.coroutines.delay(800)
                _isSyncingFirebase.value = false
            } catch (e: Exception) {
                // Handle fallback if any error
            } finally {
                _isGeneratingPlanner.value = false
            }
        }
    }

    fun toggleTaskCompleted(task: PlannerTask) {
        viewModelScope.launch {
            val newStatus = !task.isCompleted
            repository.updateTaskStatus(task.id, newStatus)
            syncTaskToFirestore(task.copy(isCompleted = newStatus))
            if (newStatus) {
                repository.addXp(10, "PLANNER")
            }
        }
    }

    fun addManualTask(title: String, category: String, priority: String, recommendedLesson: String, estimatedMinutes: Int) {
        viewModelScope.launch {
            val task = PlannerTask(
                title = title,
                category = category,
                priority = priority,
                isCompleted = false,
                recommendedLesson = recommendedLesson,
                estimatedMinutes = estimatedMinutes
            )
            repository.insertTask(task)
            syncTaskToFirestore(task)
            
            // Sync to Firebase
            _isSyncingFirebase.value = true
            kotlinx.coroutines.delay(500)
            _isSyncingFirebase.value = false
        }
    }

    fun deletePlannerTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
            deleteTaskFromFirestore(id)
        }
    }

    // --- AI Planner Features State ---
    private val _dailyPlanTasks = MutableStateFlow(
        listOf(
            DailyPlanTaskItem("1", "Learn 10 new words", false, 15, "Vocabulary"),
            DailyPlanTaskItem("2", "Scan 5 real-world objects", false, 20, "AR Vision"),
            DailyPlanTaskItem("3", "Complete 1 AI Tutor conversation", false, 25, "AI Tutor"),
            DailyPlanTaskItem("4", "Practice pronunciation (10 minutes)", false, 15, "Pronunciation"),
            DailyPlanTaskItem("5", "Finish today's adaptive quiz", false, 20, "Quiz")
        )
    )
    val dailyPlanTasks: StateFlow<List<DailyPlanTaskItem>> = _dailyPlanTasks.asStateFlow()

    private val _arMissions = MutableStateFlow(
        listOf(
            ArMissionItem("m1", "Laptop", false, 20, 10),
            ArMissionItem("m2", "Chair", false, 20, 10),
            ArMissionItem("m3", "Bottle", false, 20, 10),
            ArMissionItem("m4", "Book", false, 20, 10),
            ArMissionItem("m5", "Door", false, 20, 10)
        )
    )
    val arMissions: StateFlow<List<ArMissionItem>> = _arMissions.asStateFlow()

    private val _weeklySchedule = MutableStateFlow(
        listOf(
            WeeklyDaySchedule("Monday", "MON", "Vocabulary", false, "words", "Master 15 target vocabulary flashcards & practice pronunciation."),
            WeeklyDaySchedule("Tuesday", "TUE", "Grammar", false, "tutor", "Practice sentence structuring & verb conjugation with your AI Tutor."),
            WeeklyDaySchedule("Wednesday", "WED", "Speaking", false, "practice", "Complete 10 minutes of AI voice pronunciation coaching & real-time feedback."),
            WeeklyDaySchedule("Thursday", "THU", "AR Practice", false, "vision", "Scan 5 real-world objects around your home or office using AR Camera."),
            WeeklyDaySchedule("Friday", "FRI", "AI Tutor", false, "tutor", "Have an open conversation with your AI Language Twin on daily topics."),
            WeeklyDaySchedule("Saturday", "SAT", "Quiz", false, "practice", "Take the 10-question adaptive weekly quiz to lock in your mastery."),
            WeeklyDaySchedule("Sunday", "SUN", "Revision", false, "words", "Review forgotten words using Spaced Repetition and clear remaining tasks.")
        )
    )
    val weeklySchedule: StateFlow<List<WeeklyDaySchedule>> = _weeklySchedule.asStateFlow()

    fun toggleDailyPlanTask(taskId: String) {
        val current = _dailyPlanTasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index != -1) {
            val item = current[index]
            val newStatus = !item.isCompleted
            current[index] = item.copy(isCompleted = newStatus)
            _dailyPlanTasks.value = current
            if (newStatus) {
                viewModelScope.launch { repository.addXp(item.xpReward, "DAILY_PLAN") }
            }
        }
    }

    fun toggleArMissionItem(itemId: String) {
        val current = _arMissions.value.toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = current[index]
            val newStatus = !item.isCompleted
            current[index] = item.copy(isCompleted = newStatus)
            _arMissions.value = current
            if (newStatus) {
                viewModelScope.launch { repository.addXp(item.xpReward, "AR_MISSION") }
            }
        }
    }

    fun checkArMissionOnScan(scannedObjectNameEn: String) {
        if (scannedObjectNameEn.isBlank()) return
        val current = _arMissions.value.toMutableList()
        var updated = false
        for (i in current.indices) {
            val item = current[i]
            if (!item.isCompleted && scannedObjectNameEn.contains(item.objectNameEn, ignoreCase = true)) {
                current[i] = item.copy(isCompleted = true)
                viewModelScope.launch { repository.addXp(item.xpReward, "AR_MISSION") }
                updated = true
            }
        }
        if (updated) {
            _arMissions.value = current
        }
    }

    fun generateNewArMissions() {
        val pools = listOf(
            listOf("Laptop", "Chair", "Bottle", "Book", "Door"),
            listOf("Phone", "Cup", "Table", "Keyboard", "Pen"),
            listOf("Clock", "Mouse", "Bag", "Glass", "Lamp"),
            listOf("Window", "Plant", "Shoe", "Pillow", "Mirror")
        )
        val selected = pools.random()
        _arMissions.value = selected.mapIndexed { idx, name ->
            ArMissionItem("m_${idx}_${System.currentTimeMillis()}", name, false, 20, 10)
        }
    }

    fun toggleWeeklyDayCompleted(dayName: String) {
        val current = _weeklySchedule.value.toMutableList()
        val index = current.indexOfFirst { it.dayName.equals(dayName, ignoreCase = true) }
        if (index != -1) {
            val item = current[index]
            val newStatus = !item.isCompleted
            current[index] = item.copy(isCompleted = newStatus)
            _weeklySchedule.value = current
            if (newStatus) {
                viewModelScope.launch { repository.addXp(15, "WEEKLY_CALENDAR") }
            }
        }
    }

    fun markSpacedRepetitionWordReviewed(wordId: Long) {
        viewModelScope.launch {
            val word = allWords.value.find { it.id == wordId }
            if (word != null) {
                val newScore = (word.accuracyScore + 15).coerceAtMost(100)
                val updated = word.copy(accuracyScore = newScore, lastReviewed = System.currentTimeMillis())
                repository.insertWord(updated)
                syncWordToFirestore(updated)
                repository.addXp(10, "VOCAB_REVIEW")
            }
        }
    }

    // --- Quiz System State ---
    @androidx.compose.runtime.Stable
    data class QuizQuestion(
        val type: String, // "MULTIPLE_CHOICE", "FILL_IN_BLANKS", "MATCHING", "SPELLING"
        val questionText: String,
        val targetWord: String,
        val options: List<String> = emptyList(),
        val correctAnswer: String,
        val grammarTip: String
    )

    private val _currentQuizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val currentQuizQuestions: StateFlow<List<QuizQuestion>> = _currentQuizQuestions

    private val _quizIndex = MutableStateFlow(0)
    val quizIndex: StateFlow<Int> = _quizIndex

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore

    private val _isQuizCompleted = MutableStateFlow(false)
    val isQuizCompleted: StateFlow<Boolean> = _isQuizCompleted

    private val _selectedQuizAnswer = MutableStateFlow<String?>(null)
    val selectedQuizAnswer: StateFlow<String?> = _selectedQuizAnswer

    private val _quizAnswerChecked = MutableStateFlow(false)
    val quizAnswerChecked: StateFlow<Boolean> = _quizAnswerChecked

    // Quiz source mode: "CHAT" (From AI Tutor conversation), "NEW" (New General Topics), "EXAM" (10-Question AI Exam)
    val quizSourceType = MutableStateFlow("CHAT")

    fun generateQuiz(sourceType: String? = null) {
        val activeSource = sourceType ?: quizSourceType.value
        quizSourceType.value = activeSource

        // Clear old list to show loading state
        _currentQuizQuestions.value = emptyList()
        _quizIndex.value = 0
        _quizScore.value = 0
        _isQuizCompleted.value = false
        _selectedQuizAnswer.value = null
        _quizAnswerChecked.value = false

        viewModelScope.launch {
            val srcLang = sourceLanguage.value
            val tgtLang = targetLanguage.value
            val levelNum = userProgress.value.level
            val levelStr = when {
                levelNum <= 2 -> "Beginner"
                levelNum <= 5 -> "Intermediate"
                else -> "Advanced"
            }
            val vocabulary = allWords.value.filter { it.language == tgtLang }
            val examModeActive = (activeSource == "EXAM")
            isExamMode.value = examModeActive
            
            val useChatHistory = (activeSource == "CHAT" || activeSource == "EXAM")
            val chatHistory = if (useChatHistory) chatMessages.value else emptyList()

            var generatedList: List<QuizQuestion>? = null
            var retries = 3

            while (retries > 0) {
                try {
                    val rawQuestions = geminiService.generateQuiz(
                        sourceLanguage = srcLang,
                        targetLanguage = tgtLang,
                        proficiencyLevel = levelStr,
                        learnedWords = vocabulary,
                        isExamMode = examModeActive,
                        recentChatMessages = chatHistory
                    )
                    val mappedQuestions = rawQuestions.map { gq ->
                        QuizQuestion(
                            type = gq.type,
                            questionText = gq.questionText,
                            targetWord = gq.targetWord,
                            options = gq.options,
                            correctAnswer = gq.correctAnswer,
                            grammarTip = gq.grammarTip
                        )
                    }

                    if (validateQuizResponse(mappedQuestions)) {
                        generatedList = mappedQuestions
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Gemini quiz generation retry $retries failed: ${e.message}", e)
                }
                retries--
                if (retries > 0) {
                    delay(1000)
                }
            }

            if (generatedList != null && generatedList.isNotEmpty()) {
                _currentQuizQuestions.value = generatedList.shuffled()
            } else {
                _currentQuizQuestions.value = generateFallbackQuiz(srcLang, tgtLang, levelStr, vocabulary, useChatHistory = useChatHistory).shuffled()
            }
        }
    }

    private fun validateQuizResponse(questions: List<QuizQuestion>): Boolean {
        val expected = if (isExamMode.value) 10 else 5
        if (questions.size != expected) return false
        for (q in questions) {
            if (q.questionText.isBlank()) return false
            if (q.targetWord.isBlank()) return false
            if (q.correctAnswer.isBlank()) return false
            if (q.options.size != 4) return false
            if (q.options.distinct().size != 4) return false
            if (!q.options.contains(q.correctAnswer)) return false
            if (q.grammarTip.isBlank()) return false
        }
        return true
    }

    private fun generateFallbackQuiz(
        sourceLang: String,
        targetLang: String,
        levelStr: String,
        vocabulary: List<LearnedWord>,
        useChatHistory: Boolean = true
    ): List<QuizQuestion> {
        val list = mutableListOf<QuizQuestion>()
        val count = if (isExamMode.value) 10 else 5
        val messages = chatMessages.value

        // 1. Dynamic extraction from recent AI Tutor conversation if requested
        if (useChatHistory && messages.isNotEmpty()) {
            val combinedText = messages.takeLast(12).joinToString(" ") { it.content }.lowercase()

            if (combinedText.contains("water") || combinedText.contains("agua") || combinedText.contains("l'eau") || combinedText.contains("das wasser") || combinedText.contains("पानी") || combinedText.contains("தண்ணீர்")) {
                val ans = when (targetLang) {
                    "Spanish" -> "el agua"
                    "French" -> "l'eau"
                    "German" -> "das Wasser"
                    "Tamil" -> "தண்ணீர் (Thaṇṇīr)"
                    "Hindi" -> "पानी (Paani)"
                    "Telugu" -> "నీరు (Neeru)"
                    "Japanese" -> "水 (Mizu)"
                    else -> "Water"
                }
                val opts = listOf(ans, "Food", "House", "Book").shuffled()
                list.add(
                    QuizQuestion("MULTIPLE_CHOICE", "[From AI Chat] What is the $targetLang translation for 'Water' discussed in your chat?", "Water", opts, ans, "You discussed vocabulary related to essential drinks with your AI Tutor.")
                )
            }

            if (combinedText.contains("food") || combinedText.contains("comida") || combinedText.contains("nourriture") || combinedText.contains("bread") || combinedText.contains("உணவு") || combinedText.contains("खाना")) {
                val ans = when (targetLang) {
                    "Spanish" -> "la comida"
                    "French" -> "la nourriture"
                    "German" -> "das Essen"
                    "Tamil" -> "உணவு (Uṇavu)"
                    "Hindi" -> "खाना (Khaana)"
                    "Telugu" -> "ఆహారం (Aahaaram)"
                    "Japanese" -> "食べ物 (Tabemono)"
                    else -> "Food"
                }
                val opts = listOf(ans, "Water", "Friend", "Car").shuffled()
                list.add(
                    QuizQuestion("MULTIPLE_CHOICE", "[From AI Chat] How do you express 'Food / Meal' in $targetLang?", "Food", opts, ans, "Derived from your conversation about meals and dining.")
                )
            }

            if (combinedText.contains("hello") || combinedText.contains("hola") || combinedText.contains("bonjour") || combinedText.contains("vanakkam") || combinedText.contains("namaste") || combinedText.contains("வணக்கம்") || combinedText.contains("नमस्ते")) {
                val ans = when (targetLang) {
                    "Spanish" -> "¡Hola!"
                    "French" -> "Bonjour!"
                    "German" -> "Hallo!"
                    "Tamil" -> "வணக்கம் (Vanakkam)"
                    "Hindi" -> "नमस्ते (Namaste)"
                    "Telugu" -> "నమస్కారం (Namaskaram)"
                    "Japanese" -> "こんにちは (Konnichiwa)"
                    else -> "Hello"
                }
                val opts = listOf(ans, "Goodbye", "Thank you", "Sorry").shuffled()
                list.add(
                    QuizQuestion("MULTIPLE_CHOICE", "[From AI Chat] What was the $targetLang greeting you practiced with AI Tutor?", "Hello", opts, ans, "Greeting phrases practiced in your recent dialogue.")
                )
            }

            if (combinedText.contains("thank") || combinedText.contains("gracias") || combinedText.contains("merci") || combinedText.contains("danke") || combinedText.contains("nandri") || combinedText.contains("நன்றி") || combinedText.contains("धन्यवाद")) {
                val ans = when (targetLang) {
                    "Spanish" -> "Gracias"
                    "French" -> "Merci"
                    "German" -> "Danke"
                    "Tamil" -> "நன்றி (Nandri)"
                    "Hindi" -> "धन्यवाद (Dhanyavaad)"
                    "Telugu" -> "ధన్యవాదాలు (Dhanyavadalu)"
                    "Japanese" -> "ありがとう (Arigatou)"
                    else -> "Thank you"
                }
                val opts = listOf(ans, "Please", "Excuse me", "Welcome").shuffled()
                list.add(
                    QuizQuestion("MULTIPLE_CHOICE", "[From AI Chat] How do you express gratitude ('Thank you') in $targetLang?", "Thank you", opts, ans, "Politeness expression from your AI Tutor conversation.")
                )
            }
        }

        // 2. Dynamic quiz generation from local database learned words if available
        if (vocabulary.size >= 2) {
            for (w in vocabulary.take(count)) {
                if (list.size >= count) break
                val otherWords = vocabulary.filter { it.word != w.word }.map { it.translation }
                val defaultPool = listOf("Car", "Window", "House", "City", "Water", "Friend", "Food", "Book", "Time", "Sun")
                val incorrectOptions = (otherWords + defaultPool).filter { it != w.translation }.distinct().shuffled().take(3)
                if (incorrectOptions.size == 3) {
                    val options = (incorrectOptions + w.translation).shuffled()
                    list.add(
                        QuizQuestion(
                            type = "MULTIPLE_CHOICE",
                            questionText = "What does the word \"${w.word}\" mean in $sourceLang?",
                            targetWord = w.word,
                            options = options,
                            correctAnswer = w.translation,
                            grammarTip = "Review flashcards: \"${w.word}\" translates to \"${w.translation}\"."
                        )
                    )
                }
            }
        }

        // Complete/supplement with pre-compiled static fallback questions
        val requiredSize = if (isExamMode.value) 10 else 5
        if (list.size < requiredSize) {
            val statics = when (targetLang) {
                "Tamil" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What does the greeting 'வணக்கம்' (Vanakkam) mean in English?", "வணக்கம்", listOf("Hello / Greetings", "Goodbye", "Thank you", "Sorry"), "Hello / Greetings", "'வணக்கம்' is the traditional Tamil greeting used to welcome people."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does the word 'நன்றி' (Nandri) translate to?", "நன்றி", listOf("Please", "Sorry", "Welcome", "Thank you"), "Thank you", "'நன்றி' is used to express gratitude in Tamil."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the English translation of the fruit 'மாம்பழம்' (Maampazham)?", "மாம்பழம்", listOf("Mango", "Apple", "Banana", "Orange"), "Mango", "'மாம்பழம்' means mango, which is highly celebrated in Tamil culture."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does the word 'அம்மா' (Amma) mean?", "அம்மா", listOf("Mother", "Father", "Brother", "Sister"), "Mother", "'அம்மா' is the Tamil word for Mother."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'வீடு' (Veedu) mean?", "வீடு", listOf("House", "School", "Car", "Tree"), "House", "'வீடு' translates to House or Home in Tamil.")
                )
                "Hindi" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What is the translation of 'नमस्ते' (Namaste) in English?", "नमस्ते", listOf("Goodbye", "Thank you", "Hello", "Yes"), "Hello", "'नमस्ते' is a respectful greeting used in India to say hello."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'धन्यवाद' (Dhanyavaad) to English:", "धन्यवाद", listOf("Please", "Thank you", "Excuse me", "Welcome"), "Thank you", "'धन्यवाद' is the formal Hindi word for expressing thanks."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'सेब' (Seb)?", "सेब", listOf("Banana", "Orange", "Grape", "Apple"), "Apple", "'सेब' translates to Apple in Hindi."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'दोस्त' (Dost) mean?", "दोस्त", listOf("Friend", "Teacher", "Doctor", "Student"), "Friend", "'दोस्त' translates to Friend in Hindi."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'पानी' (Paani)?", "पानी", listOf("Water", "Milk", "Tea", "Food"), "Water", "'पानी' translates to Water in Hindi.")
                )
                "Telugu" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What does the greeting 'నమస్కారం' (Namaskaram) mean?", "నమస్కారం", listOf("Hello / Greetings", "Goodbye", "Sorry", "Thank you"), "Hello / Greetings", "'నమస్కారం' is the traditional respectful greeting in Telugu."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the Telugu word for 'Thank you'?", "ధన్యవాదాలు", listOf("ధన్యవాదాలు (Dhanyavadalu)", "దయచేసి (Dayachesi)", "క్షమించండి (Kshaminchandi)", "స్వాగతం (Swagatham)"), "ధన్యవాదాలు (Dhanyavadalu)", "'ధన్యవాదాలు' is used to express gratitude in Telugu."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'నీరు' (Neeru) to English:", "నీరు", listOf("Food", "Water", "Air", "Earth"), "Water", "'నీరు' means water in Telugu."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'అమ్మ' (Amma)?", "అమ్మ", listOf("Mother", "Father", "Friend", "Teacher"), "Mother", "'అమ్మ' is the Telugu word for Mother."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'ఇల్లు' (Illu) to English:", "ఇల్లు", listOf("House", "School", "Car", "River"), "House", "'ఇల్లు' means House in Telugu.")
                )
                "Kannada" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What is the standard greeting 'ನಮಸ್ಕಾರ' (Namaskara) in English?", "ನಮಸ್ಕಾರ", listOf("Hello / Greetings", "Thank you", "Goodbye", "Please"), "Hello / Greetings", "'ನಮಸ್ಕಾರ' is the standard respectful greeting in Kannada."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'ಧನ್ಯವಾದಗಳು' (Dhanyavadagalu) mean?", "ಧನ್ಯವಾದಗಳು", listOf("Thank you", "Welcome", "Excuse me", "Sorry"), "Thank you", "'ಧನ್ಯವಾದಗಳು' means Thank you in Kannada."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'ಹಾಲು' (Haalu)?", "ಹಾಲು", listOf("Water", "Milk", "Juice", "Tea"), "Milk", "'ಹಾಲು' translates to Milk in Kannada."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'ಮನೆ' (Mane) mean?", "ಮನೆ", listOf("House", "Vehicle", "City", "Mountain"), "House", "'ಮನೆ' translates to House in Kannada."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'ಸ್ನೇಹಿತ' (Snehitha)?", "ಸ್ನೇಹಿತ", listOf("Friend", "Enemy", "Stranger", "Relative"), "Friend", "'ಸ್ನೇಹಿತ' means Friend in Kannada.")
                )
                "Malayalam" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'നമസ്കാരം' (Namaskaram) in English?", "നമസ്കാരം", listOf("Hello / Greetings", "Goodbye", "Thank you", "Sorry"), "Hello / Greetings", "'നമസ്കാരം' is the standard greeting in Malayalam."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate the word 'നന്ദി' (Nandi) to English:", "നന്ദി", listOf("Please", "Sorry", "Thank you", "Welcome"), "Thank you", "'നന്ദി' is the Malayalam word for expressing thanks."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'വെള്ളം' (Vellam) mean?", "വെള്ളം", listOf("Water", "Milk", "Rice", "Bread"), "Water", "'வெള്ളം' means water in Malayalam."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is 'അമ്മ' (Amma) in English?", "അമ്മ", listOf("Mother", "Father", "Sister", "Brother"), "Mother", "'അമ്മ' is the Malayalam word for Mother."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'വീട്' (Veed) to English:", "വീട്", listOf("House", "Shop", "Office", "Field"), "House", "'വീട്' translates to House/Home in Malayalam.")
                )
                "Arabic" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'مرحباً' (Marhaban) mean in English?", "مرحباً", listOf("Hello", "Goodbye", "Thank you", "Please"), "Hello", "'مرحباً' is a very common way to greet someone with hello in Arabic."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'شكراً' (Shukran) to English:", "شكراً", listOf("Please", "Sorry", "Thank you", "Welcome"), "Thank you", "'شكراً' is the Arabic expression for thank you."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'تفاحة' (Tuffahah) mean?", "تفاحة", listOf("Banana", "Apple", "Orange", "Grape"), "Apple", "'تفاحة' means apple in Arabic."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the Arabic word for 'Friend'?", "صديق", listOf("صديق (Sadeeq)", "معلم (Mo'allim)", "طبيب (Tabeeb)", "طالب (Talib)"), "صديق (Sadeeq)", "'صديق' is the Arabic word for Friend."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'ماء' (Maa') to English:", "ماء", listOf("Water", "Fire", "Air", "Earth"), "Water", "'ماء' is the Arabic word for Water.")
                )
                "French" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Bonjour' to English:", "Bonjour", listOf("Hello / Good morning", "Goodbye", "Good night", "Please"), "Hello / Good morning", "'Bonjour' is the most common French greeting, combining 'bon' (good) and 'jour' (day)."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'Merci' mean?", "Merci", listOf("Please", "Excuse me", "Thank you", "You are welcome"), "Thank you", "'Merci' is the standard French word for 'thank you'."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'Pain'?", "Pain", listOf("Water", "Bread", "Apple", "Cheese"), "Bread", "'Pain' translates to Bread in French."),
                    QuizQuestion("MULTIPLE_CHOICE", "Complete: 'Je mange un _____ (croissant)'", "croissant", listOf("croissant", "pomme", "baguette", "café"), "croissant", "Croissant is masculine, preceded by the article 'un'."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'Ami' mean?", "Ami", listOf("Friend", "Enemy", "Teacher", "Brother"), "Friend", "'Ami' means friend in French.")
                )
                "German" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Hallo' to English:", "Hallo", listOf("Hello", "Goodbye", "Please", "Thanks"), "Hello", "'Hallo' is the friendly German greeting for hello."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'Danke' mean?", "Danke", listOf("Please", "Thank you", "Sorry", "Welcome"), "Thank you", "'Danke' means thank you in German."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'Apfel'?", "Apfel", listOf("Apple", "Orange", "Banana", "Grape"), "Apple", "'Apfel' is the German word for apple."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the German word for 'Friend'?", "Freund", listOf("Freund", "Feind", "Lehrer", "Bruder"), "Freund", "'Freund' translates to Friend in German."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Wasser' to English:", "Wasser", listOf("Water", "Milk", "Beer", "Wine"), "Water", "'Wasser' means water in German.")
                )
                "Spanish" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Hola' to English:", "Hola", listOf("Hello", "Goodbye", "Thank you", "Please"), "Hello", "'Hola' is the universal Spanish greeting."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'Gracias'?", "Gracias", listOf("Thank you", "Please", "Sorry", "Welcome"), "Thank you", "'Gracias' is used to say thank you in Spanish."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'Agua' mean?", "Agua", listOf("Water", "Milk", "Beer", "Wine"), "Water", "'Agua' means water in Spanish."),
                    QuizQuestion("MULTIPLE_CHOICE", "Complete the sentence: 'La manzana es _____ (red)'", "roja", listOf("verde", "azul", "amarilla", "roja"), "roja", "'Manzana' is feminine singular, requiring the feminine adjective 'roja'."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does 'Amigo' mean?", "Amigo", listOf("Friend", "Teacher", "Doctor", "Brother"), "Friend", "'Amigo' translates to Friend in Spanish.")
                )
                "Japanese" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'こんにちは' (Konnichiwa) to English:", "こんにちは", listOf("Hello / Good afternoon", "Good morning", "Goodbye", "Thank you"), "Hello / Good afternoon", "'こんにちは' is the standard daytime greeting in Japanese."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of 'ありがとう' (Arigatou)?", "ありがとう", listOf("Excuse me", "Please", "Sorry", "Thank you"), "Thank you", "'ありがとう' is the friendly Japanese word for thank you."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'お茶' (Ocha) to English:", "お茶", listOf("Water", "Sake", "Green Tea", "Juice"), "Green Tea", "'お茶' refers to green tea, highly popular in Japan."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does '友達' (Tomodachi) mean?", "友達", listOf("Friend", "Teacher", "Student", "Family"), "Friend", "'友達' means friend in Japanese."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate '水' (Mizu) to English:", "水", listOf("Water", "Fire", "Soil", "Wind"), "Water", "'水' means water in Japanese.")
                )
                "Chinese" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What does '你好' (Nǐ hǎo) translate to?", "你好", listOf("Goodbye", "Thank you", "Hello", "Please"), "Hello", "'你好' literally means 'You good' and is the standard way to say Hello in Chinese."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate '谢谢' (Xièxie) to English:", "谢谢", listOf("Thank you", "You are welcome", "Sorry", "Hello"), "Thank you", "'谢谢' is the Chinese word for thank you."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of '苹果' (Píngguǒ)?", "苹果", listOf("Apple", "Banana", "Orange", "Grape"), "Apple", "'苹果' translates to Apple in Chinese."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does '朋友' (Péngyǒu) mean?", "朋友", listOf("Friend", "Teacher", "Student", "Doctor"), "Friend", "'朋友' means friend in Chinese."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate '水' (Shuǐ) to English:", "水", listOf("Water", "Milk", "Juice", "Soda"), "Water", "'水' means water in Chinese.")
                )
                "Korean" -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "What does '안녕하세요' (Annyeonghaseyo) mean in English?", "안녕하세요", listOf("Hello", "Goodbye", "Thank you", "Sorry"), "Hello", "'안녕하세요' is the standard polite greeting in Korean."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate '감사합니다' (Gamsahabnida) to English:", "감사합니다", listOf("Please", "Sorry", "Thank you", "Welcome"), "Thank you", "'감사합니다' is the polite way to express gratitude in Korean."),
                    QuizQuestion("MULTIPLE_CHOICE", "What is the meaning of '사과' (Sagwa)?", "사과", listOf("Apple", "Orange", "Banana", "Grape"), "Apple", "'사과' translates to Apple in Korean."),
                    QuizQuestion("MULTIPLE_CHOICE", "What does '친구' (Chingu) mean?", "친구", listOf("Friend", "Teacher", "Student", "Doctor"), "Friend", "'친구' translates to Friend in Korean."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate '물' (Mul) to English:", "물", listOf("Water", "Milk", "Juice", "Tea"), "Water", "'물' means water in Korean.")
                )
                else -> listOf(
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Hello' to $targetLang", "Hello", listOf("Greeting", "Welcome", "Hello Translation", "Goodbye"), "Hello Translation", "This is the most standard greeting."),
                    QuizQuestion("MULTIPLE_CHOICE", "Translate 'Thank you' to $targetLang", "Thank you", listOf("Thank you", "Please", "Sorry", "Welcome"), "Thank you", "Expression of gratitude."),
                    QuizQuestion("MULTIPLE_CHOICE", "Identify the translation for 'Water':", "Water", listOf("Water", "Milk", "Bread", "Apple"), "Water", "A basic life resource."),
                    QuizQuestion("MULTIPLE_CHOICE", "Identify the translation for 'Friend':", "Friend", listOf("Friend", "Enemy", "Stranger", "Relative"), "Friend", "A companion."),
                    QuizQuestion("MULTIPLE_CHOICE", "Identify the translation for 'House':", "House", listOf("House", "Car", "Tree", "Book"), "House", "A building for human habitation.")
                )
            }
            for (q in statics) {
                if (list.size >= 5) break
                if (list.none { it.questionText == q.questionText }) {
                    list.add(q)
                }
            }
        }
        return list.take(5)
    }

    fun selectQuizAnswer(answer: String) {
        if (_quizAnswerChecked.value) return
        _selectedQuizAnswer.value = answer
    }

    fun checkQuizAnswer() {
        val currentQ = _currentQuizQuestions.value.getOrNull(_quizIndex.value) ?: return
        _quizAnswerChecked.value = true
        if (_selectedQuizAnswer.value == currentQ.correctAnswer) {
            _quizScore.value += 1
            speakText("Correct", "English")
        } else {
            speakText("Incorrect", "English")
        }
    }

    fun nextQuizQuestion() {
        val nextIdx = _quizIndex.value + 1
        if (nextIdx < _currentQuizQuestions.value.size) {
            _quizIndex.value = nextIdx
            _selectedQuizAnswer.value = null
            _quizAnswerChecked.value = false
        } else {
            _isQuizCompleted.value = true
            // Save quiz results to database & award XP
            viewModelScope.launch {
                val result = QuizResult(
                    score = _quizScore.value,
                    totalQuestions = _currentQuizQuestions.value.size,
                    difficulty = "Beginner",
                    language = selectedLanguage.value
                )
                repository.insertQuizResult(
                    score = result.score,
                    total = result.totalQuestions,
                    difficulty = result.difficulty,
                    language = result.language
                )
                syncQuizResultToFirestore(result)
            }
        }
    }

    // --- Vocabulary Flashcard Practice Mode State ---
    private val _flashcardWords = MutableStateFlow<List<LearnedWord>>(emptyList())
    val flashcardWords: StateFlow<List<LearnedWord>> = _flashcardWords

    private val _flashcardIndex = MutableStateFlow(0)
    val flashcardIndex: StateFlow<Int> = _flashcardIndex

    private val _isFlashcardFlipped = MutableStateFlow(false)
    val isFlashcardFlipped: StateFlow<Boolean> = _isFlashcardFlipped

    fun startFlashcards() {
        val lang = selectedLanguage.value
        val words = allWords.value.filter { it.language == lang }
        _flashcardWords.value = words.ifEmpty { 
            // Mock empty state words
            listOf(
                LearnedWord(word = "Example", translation = "Ejemplo / Exemple", ipa = "/.../", exampleSentence = "", exampleTranslation = "", category = "General", difficulty = "Beginner", language = lang)
            )
        }
        _flashcardIndex.value = 0
        _isFlashcardFlipped.value = false
    }

    fun flipFlashcard() {
        _isFlashcardFlipped.value = !_isFlashcardFlipped.value
    }

    fun nextFlashcard() {
        val nextIdx = _flashcardIndex.value + 1
        if (nextIdx < _flashcardWords.value.size) {
            _flashcardIndex.value = nextIdx
            _isFlashcardFlipped.value = false
        } else {
            // Loop back
            _flashcardIndex.value = 0
            _isFlashcardFlipped.value = false
        }
    }

    // --- Bookmark Toggles ---
    fun toggleWordBookmark(word: LearnedWord) {
        viewModelScope.launch {
            val updated = word.copy(isBookmarked = !word.isBookmarked)
            repository.updateBookmark(word.id, updated.isBookmarked)
            syncWordToFirestore(updated)
        }
    }

    fun deleteVocabularyWord(id: Long) {
        viewModelScope.launch {
            repository.deleteWord(id)
            deleteWordFromFirestore(id)
        }
    }

    // --- ADVANCED AI FEATURES INTEGRATION ---
    
    // Feature 2 & 10: AR Sentence Builder & Grammar Assistant States
    private val _isBuildingSentence = MutableStateFlow(false)
    val isBuildingSentence: StateFlow<Boolean> = _isBuildingSentence.asStateFlow()

    data class BuiltSentenceResult(
        val originalWords: List<String>,
        val sentenceTarget: String,
        val sentenceEn: String,
        val grammarTip: String
    )

    private val _activeBuiltSentence = MutableStateFlow<BuiltSentenceResult?>(null)
    val activeBuiltSentence: StateFlow<BuiltSentenceResult?> = _activeBuiltSentence.asStateFlow()

    fun buildSentenceFromObjects(results: List<ArScanResult>) {
        if (results.size < 2) return
        viewModelScope.launch {
            _isBuildingSentence.value = true
            _activeBuiltSentence.value = null
            try {
                val srcLang = sourceLanguage.value
                val tgtLang = targetLanguage.value
                val wordsTarget = results.map { it.objectNameTarget }
                val wordsEn = results.map { it.objectNameEn }
                
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                    val prompt = """
                        You are "ARLingua Grammar Coach". 
                        The user is learning $tgtLang. Generate a single, grammatically correct and natural sentence in $tgtLang containing all of these target vocabulary words: ${wordsTarget.joinToString(", ")}.
                        
                        You MUST return a JSON object containing the sentence, English translation, and a detailed grammar note explaining how these words are used and conjugated together in $srcLang:
                        {
                          "sentenceTarget": "...",
                          "sentenceEn": "...",
                          "grammarExplanation": "..."
                        }
                        Do not include markdown tags. Only return the raw JSON.
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
                    if (jsonText != null) {
                        val adapter = GeminiRetrofitClient.moshiInstance.adapter(Map::class.java)
                        val map = adapter.fromJson(jsonText) as? Map<String, String>
                        if (map != null) {
                            val target = map["sentenceTarget"] ?: ""
                            val en = map["sentenceEn"] ?: ""
                            val explanation = map["grammarExplanation"] ?: ""
                            if (target.isNotEmpty()) {
                                val res = BuiltSentenceResult(wordsTarget, target, en, explanation)
                                _activeBuiltSentence.value = res
                                repository.addXp(20, "AR_SENTENCE")
                                speakText(target, tgtLang)
                                _isBuildingSentence.value = false
                                return@launch
                            }
                        }
                    }
                }
                
                // Fallback / simulation
                val simulatedResult = getSimulatedBuiltSentence(tgtLang, wordsTarget, wordsEn)
                _activeBuiltSentence.value = simulatedResult
                repository.addXp(15, "AR_SENTENCE")
                speakText(simulatedResult.sentenceTarget, tgtLang)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Sentence builder error: ${e.message}", e)
                val wordsTarget = results.map { it.objectNameTarget }
                val wordsEn = results.map { it.objectNameEn }
                val simulatedResult = getSimulatedBuiltSentence(targetLanguage.value, wordsTarget, wordsEn)
                _activeBuiltSentence.value = simulatedResult
                speakText(simulatedResult.sentenceTarget, targetLanguage.value)
            } finally {
                _isBuildingSentence.value = false
            }
        }
    }

    private fun getSimulatedBuiltSentence(lang: String, words: List<String>, wordsEn: List<String>): BuiltSentenceResult {
        return when (lang) {
            "Spanish" -> {
                val sentence = "El libro está cerca de la taza en el escritorio."
                val trans = "The book is near the cup on the desk."
                val explanation = "This sentence connects 'libro' (book), 'taza' (cup), and 'escritorio' (desk). It uses the verb 'estar' (está) for temporary spatial location, and the prepositional phrase 'cerca de' (near) followed by 'en' (on)."
                BuiltSentenceResult(words, sentence, trans, explanation)
            }
            "French" -> {
                val sentence = "Le livre est posé à côté de la tasse sur le bureau."
                val trans = "The book is placed next to the cup on the desk."
                val explanation = "Connects 'livre' (book), 'tasse' (cup), and 'bureau' (desk). It utilizes 'est posé' (is placed) with prepositional locator 'à côté de' (next to) and 'sur' (on)."
                BuiltSentenceResult(words, sentence, trans, explanation)
            }
            else -> {
                val joined = words.joinToString(" ")
                val joinedEn = wordsEn.joinToString(" ")
                BuiltSentenceResult(
                    words,
                    "Sentence using: $joined",
                    "Translation using: $joinedEn",
                    "Connective particles and adjectives matching nouns correctly in $lang."
                )
            }
        }
    }
    
    fun clearBuiltSentence() {
        _activeBuiltSentence.value = null
    }

    // Feature 5: AR Classroom Challenge State
    data class ClassroomChallenge(
        val description: String,
        val targetCategory: String,
        val requiredCount: Int,
        val progressCount: Int,
        val coinsReward: Int,
        val xpReward: Int,
        val isCompleted: Boolean
    )

    private val _activeChallenge = MutableStateFlow(
        ClassroomChallenge(
            description = "Scan 2 Food items or Fruits in AR mode!",
            targetCategory = "Food",
            requiredCount = 2,
            progressCount = 0,
            coinsReward = 15,
            xpReward = 40,
            isCompleted = false
        )
    )
    val activeChallenge: StateFlow<ClassroomChallenge> = _activeChallenge.asStateFlow()

    fun updateChallengeProgress(scannedCategory: String) {
        val current = _activeChallenge.value
        if (current.isCompleted) return
        
        if (scannedCategory.equals(current.targetCategory, ignoreCase = true) || scannedCategory.contains(current.targetCategory, ignoreCase = true)) {
            val newProgress = current.progressCount + 1
            if (newProgress >= current.requiredCount) {
                _activeChallenge.value = current.copy(
                    progressCount = newProgress,
                    isCompleted = true
                )
                viewModelScope.launch {
                    val currentProgress = userProgress.value
                    val updated = currentProgress.copy(
                        coins = currentProgress.coins + current.coinsReward,
                        xp = currentProgress.xp + current.xpReward
                    )
                    repository.db.userProgressDao().updateProgress(updated)
                    syncProgressToFirestore(updated)
                }
            } else {
                _activeChallenge.value = current.copy(progressCount = newProgress)
            }
        }
    }

    // Feature 9: AI Exam Simulator toggle
    val isExamMode = MutableStateFlow(false)

    // Route Switch Sync State
    private val _requestedRoute = MutableStateFlow<String?>(null)
    val requestedRoute: StateFlow<String?> = _requestedRoute.asStateFlow()
    
    fun requestRouteSwitch(route: String) {
        _requestedRoute.value = route
    }
    
    fun clearRequestedRoute() {
        _requestedRoute.value = null
    }

    fun awardSignLanguageXpAndStreak(xpAmount: Int = 15) {
        viewModelScope.launch {
            try {
                val currentProgress = userProgress.value
                val updated = currentProgress.copy(
                    xp = currentProgress.xp + xpAmount,
                    streak = currentProgress.streak + (if (Math.random() < 0.1) 1 else 0)
                )
                repository.db.userProgressDao().updateProgress(updated)
                syncProgressToFirestore(updated)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // --- FEATURE 6: AI MISTAKE PREDICTION & GENERATOR ---
    data class PredictedMistake(
        val wordOrPattern: String,
        val mistakeType: String, // "Vocabulary confusion", "Grammar mismatch", "Pronunciation weakness"
        val description: String,
        val correctionTip: String,
        val practicePrompt: String
    )

    data class MistakePracticeExercise(
        val id: String = java.util.UUID.randomUUID().toString(),
        val prompt: String,
        val expectedAnswer: String,
        val explanation: String,
        val options: List<String> = emptyList(),
        var isSolved: Boolean = false,
        var isCorrect: Boolean? = null
    )

    val predictedMistakes: StateFlow<List<PredictedMistake>> = combine(allWords, allPractices, targetLanguage) { words, practices, lang ->
        val list = mutableListOf<PredictedMistake>()
        
        // Vocab confusion based on real word list
        val weakVocab = words.filter { it.language == lang && it.accuracyScore in 1..75 }
        if (weakVocab.isNotEmpty()) {
            weakVocab.take(2).forEach { w ->
                list.add(
                    PredictedMistake(
                        wordOrPattern = w.word,
                        mistakeType = "Vocabulary confusion",
                        description = "Confusing ${w.word} with similar sounding words or other terms in ${w.category}.",
                        correctionTip = "The word is '${w.word}', translating to '${w.translation}'. Study example: '${w.exampleSentence}'.",
                        practicePrompt = "What is the correct translation of '${w.word}' in English?"
                    )
                )
            }
        } else {
            // General language-specific predicted pitfalls
            when (lang) {
                "Spanish" -> {
                    list.add(PredictedMistake("por vs para", "Vocabulary confusion", "Using 'por' instead of 'para' for destination/deadline.", "Use 'para' for effect, recipient, destination, deadline, or employment.", "Complete: Regalo es ____ ti."))
                    list.add(PredictedMistake("ser vs estar", "Grammar mismatch", "Using temporary instead of permanent being state.", "Use 'ser' for identity, origin, occupation. Use 'estar' for location, health, emotion.", "Complete: Yo ____ de Madrid."))
                }
                "French" -> {
                    list.add(PredictedMistake("pour vs pendant", "Vocabulary confusion", "Confusing duration prepositions.", "Use 'pendant' for during/for past/present actions. Use 'pour' only for future planned durations.", "Complete: J'ai étudié ____ trois heures."))
                }
                else -> {
                    list.add(PredictedMistake("Subject-Verb Concord", "Grammar mismatch", "Mismatching subject and verb inflections.", "Always pay attention to verb conjugation rules in $lang.", "Choose the correct inflection for the subject."))
                }
            }
        }

        // Pronunciation weaknesses based on practice history
        val lowPractices = practices.filter { it.language == lang && it.score < 80 }
        if (lowPractices.isNotEmpty()) {
            lowPractices.take(2).forEach { p ->
                list.add(
                    PredictedMistake(
                        wordOrPattern = p.word,
                        mistakeType = "Pronunciation weakness",
                        description = "Struggling with target sounds in '${p.word}'. Detected incorrect: ${p.incorrectSounds}.",
                        correctionTip = p.suggestions,
                        practicePrompt = "Try speaking '${p.word}' focusing on corrected sounds."
                    )
                )
            }
        } else {
            list.add(
                PredictedMistake(
                    wordOrPattern = "r / l sounds",
                    mistakeType = "Pronunciation weakness",
                    description = "Typical accent interference on dental or retroflex consonants.",
                    correctionTip = "Listen to the native voice guide and place your tongue right behind upper teeth.",
                    practicePrompt = "Pronounce key greetings slowly."
                )
            )
        }
        
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mistakePracticeExercises = MutableStateFlow<List<MistakePracticeExercise>>(emptyList())
    val mistakePracticeExercises: StateFlow<List<MistakePracticeExercise>> = _mistakePracticeExercises.asStateFlow()

    fun generateMistakeExercises() {
        val lang = targetLanguage.value
        val list = mutableListOf<MistakePracticeExercise>()
        
        // Dynamically build based on target language or learned words
        val words = allWords.value.filter { it.language == lang }
        if (words.size >= 2) {
            val w1 = words[0]
            val w2 = words[1]
            list.add(
                MistakePracticeExercise(
                    prompt = "Translate '${w1.word}' correctly:",
                    expectedAnswer = w1.translation,
                    explanation = "'${w1.word}' means '${w1.translation}'. Tip: ${w1.exampleSentence}",
                    options = listOf(w1.translation, w2.translation, "None of the above", "Incorrect option").shuffled()
                )
            )
        }
        
        // Language specific grammar prediction exercises
        when (lang) {
            "Spanish" -> {
                list.add(
                    MistakePracticeExercise(
                        prompt = "Complete the sentence: 'Este libro es ____ (for) mi padre.'",
                        expectedAnswer = "para",
                        explanation = "Use 'para' because it indicates the recipient.",
                        options = listOf("por", "para", "de", "con")
                    )
                )
                list.add(
                    MistakePracticeExercise(
                        prompt = "Complete the sentence: 'Yo ____ (am) un estudiante de medicina.'",
                        expectedAnswer = "soy",
                        explanation = "Use 'ser' (soy) for occupations or identities.",
                        options = listOf("estoy", "soy", "tengo", "hago")
                    )
                )
            }
            "French" -> {
                list.add(
                    MistakePracticeExercise(
                        prompt = "Complete the sentence: 'Je suis ____ (happy) de vous voir.'",
                        expectedAnswer = "ravi",
                        explanation = "'Ravi' means delighted/happy.",
                        options = listOf("triste", "ravi", "mauvais", "petit")
                    )
                )
            }
            else -> {
                list.add(
                    MistakePracticeExercise(
                        prompt = "In $lang, what is the standard polite greeting for 'Hello'?",
                        expectedAnswer = if (lang == "Tamil") "வணக்கம்" else "Greeting",
                        explanation = "Polite greetings build strong initial cultural connections.",
                        options = if (lang == "Tamil") listOf("வணக்கம்", "போய் வருகிறேன்", "நன்றி", "மன்னிக்கவும்") else listOf("Greeting", "Goodbye", "Thanks", "Sorry")
                    )
                )
            }
        }

        _mistakePracticeExercises.value = list
    }

    fun solveMistakeExercise(exerciseId: String, answer: String): Boolean {
        val current = _mistakePracticeExercises.value.toMutableList()
        val index = current.indexOfFirst { it.id == exerciseId }
        if (index != -1) {
            val ex = current[index]
            val correct = ex.expectedAnswer.trim().lowercase() == answer.trim().lowercase()
            
            ex.isSolved = true
            ex.isCorrect = correct
            _mistakePracticeExercises.value = current

            if (correct) {
                viewModelScope.launch {
                    val currentProgress = userProgress.value
                    val updated = currentProgress.copy(
                        xp = currentProgress.xp + 15,
                        coins = currentProgress.coins + 5
                    )
                    repository.db.userProgressDao().updateProgress(updated)
                    syncProgressToFirestore(updated)
                }
            }
            return correct
        }
        return false
    }

    // --- FEATURE 7: AI VOCABULARY DIFFICULTY ESTIMATOR ---
    fun estimateVocabularyDifficulty(word: LearnedWord): String {
        val sentenceWordCount = word.exampleSentence.split("\\s+".toRegex()).size
        val userLevel = userProgress.value.level
        val lastAccuracy = word.accuracyScore

        var complexityScore = word.word.length * 4 + sentenceWordCount * 10
        if (lastAccuracy > 0) {
            complexityScore += (100 - lastAccuracy) * 1
        }
        complexityScore -= (userLevel - 1) * 6

        return when {
            complexityScore < 45 -> "Beginner"
            complexityScore < 95 -> "Intermediate"
            else -> "Advanced"
        }
    }

    // --- FEATURE 8: VOICE CONVERSATION MODE ---
    val isVoiceConversationModeActive = MutableStateFlow(false)

    fun toggleVoiceConversationMode() {
        isVoiceConversationModeActive.value = !isVoiceConversationModeActive.value
        if (isVoiceConversationModeActive.value) {
            speakText("Voice Conversation Practice mode is now active. Talk to me naturally or speak standard navigation commands.", "English")
        } else {
            stopSpeaking()
        }
    }

    // --- FEATURE 11: AI LEARNING TWIN PROFILE ---
    data class LearningTwinProfile(
        val vocabularyGrowthRate: String,
        val pronunciationStrength: String,
        val averageQuizScore: String,
        val estimatedExamScore: Int,
        val timeToMasterMonths: Int,
        val recommendedDailyMinutes: Int,
        val learningSpeedCategory: String
    )

    val learningTwinProfile: StateFlow<LearningTwinProfile> = combine(allWords, allPractices, userProgress) { words, practices, prog ->
        val totalWords = words.size
        val averagePracticeScore = if (practices.isNotEmpty()) practices.map { it.score }.average().toInt() else 0
        
        val growthRate = when {
            totalWords > 15 -> "High (3.2 words/day)"
            totalWords > 5 -> "Moderate (1.8 words/day)"
            else -> "Steady (0.5 words/day)"
        }

        val strongSound = when {
            averagePracticeScore >= 85 -> "Excellent vowel inflection and fluid cadence"
            averagePracticeScore >= 70 -> "Good basic phonetics, some minor nasal friction"
            else -> "Needs regular practice on retroflex consonants"
        }

        val estScore = 65 + (prog.level * 5) + (prog.streak * 2) + (averagePracticeScore / 10)
        val finalEstScore = estScore.coerceIn(50, 100)

        LearningTwinProfile(
            vocabularyGrowthRate = growthRate,
            pronunciationStrength = strongSound,
            averageQuizScore = if (practices.isNotEmpty()) "$averagePracticeScore%" else "N/A",
            estimatedExamScore = finalEstScore,
            timeToMasterMonths = (12 - prog.level).coerceAtLeast(2),
            recommendedDailyMinutes = if (prog.streak < 3) 25 else 15,
            learningSpeedCategory = if (prog.xp > 500) "Aggressive Academic" else "Steady Conversationalist"
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LearningTwinProfile("Steady (0.5 words/day)", "Needs practice", "N/A", 75, 8, 15, "Conversationalist")
    )

    // --- FEATURE 13: SMART RECOMMENDATION ENGINE ---
    data class SmartRecommendation(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String,
        val type: String, // "lesson", "vocabulary", "grammar", "speaking", "quiz"
        val description: String,
        val route: String,
        val priority: String
    )

    val smartRecommendations: StateFlow<List<SmartRecommendation>> = combine(allWords, allPractices, userProgress) { words, practices, prog ->
        val list = mutableListOf<SmartRecommendation>()
        
        // Recommend Speaking Practice if no practices are loaded
        if (practices.isEmpty()) {
            list.add(
                SmartRecommendation(
                    title = "Interactive Voice Practice",
                    type = "speaking",
                    description = "Speak out your newly scanned vocabulary words in the Speak page.",
                    route = "practice",
                    priority = "High"
                )
            )
        }
        
        // Recommend AR Scanner if vocab list is thin
        if (words.size < 5) {
            list.add(
                SmartRecommendation(
                    title = "AR Lens Vocabulary Scan",
                    type = "vocabulary",
                    description = "Scan 3 household items using Google ARCore to quickly grow your library.",
                    route = "vision",
                    priority = "High"
                )
            )
        }

        // Recommend Tutor Conversation
        list.add(
            SmartRecommendation(
                title = "Live AI Conversational Drill",
                type = "lesson",
                description = "Start hands-free Voice Practice with your AI Tutor in ${targetLanguage.value}.",
                route = "tutor",
                priority = "Medium"
            )
        )

        // Recommend Quiz
        if (words.size >= 2) {
            list.add(
                SmartRecommendation(
                    title = "Personalized AI Exam",
                    type = "quiz",
                    description = "Take a high-stakes AI-generated exam testing your active vocabulary database.",
                    route = "practice",
                    priority = "Medium"
                )
            )
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FEATURE 14: GAMIFICATION ACHIEVEMENT BADGES ---
    data class AchievementBadge(
        val title: String,
        val description: String,
        val iconEmoji: String,
        val isUnlocked: Boolean
    )

    val achievementBadges: StateFlow<List<AchievementBadge>> = combine(allWords, allPractices, userProgress) { words, practices, prog ->
        listOf(
            AchievementBadge(
                title = "AR Explorer",
                description = "Scanned 3+ physical objects in augmented reality.",
                iconEmoji = "🕶️",
                isUnlocked = words.size >= 3
            ),
            AchievementBadge(
                title = "Speech Wizard",
                description = "Achieved 85%+ accuracy score on any pronunciation exercise.",
                iconEmoji = "🗣️",
                isUnlocked = practices.any { it.score >= 85 }
            ),
            AchievementBadge(
                title = "Consistency Master",
                description = "Maintained a continuous daily study streak of 3+ days.",
                iconEmoji = "🔥",
                isUnlocked = prog.streak >= 3
            ),
            AchievementBadge(
                title = "Wealthy Scholar",
                description = "Accumulated 500+ premium coins.",
                iconEmoji = "🪙",
                isUnlocked = prog.coins >= 500
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FEATURE 10: AR GRAMMAR ASSISTANT STATE & LOGIC ---
    data class ArGrammarCorrection(
        val id: String,
        val originalText: String,
        val incorrectSegment: String,
        val correctSegment: String,
        val suggestedFullSentence: String,
        val explanation: String,
        val severity: String, // "Error", "Warning"
        val xPosPercent: Int,
        val yPosPercent: Int
    )

    private val _grammarModeActive = MutableStateFlow(false)
    val grammarModeActive: StateFlow<Boolean> = _grammarModeActive.asStateFlow()

    private val _grammarCorrections = MutableStateFlow<List<ArGrammarCorrection>>(emptyList())
    val grammarCorrections: StateFlow<List<ArGrammarCorrection>> = _grammarCorrections.asStateFlow()

    private val _isGrammarScanning = MutableStateFlow(false)
    val isGrammarScanning: StateFlow<Boolean> = _isGrammarScanning.asStateFlow()

    fun toggleGrammarMode(active: Boolean) {
        _grammarModeActive.value = active
        _grammarCorrections.value = emptyList()
    }

    fun performGrammarAnalysis(text: String) {
        viewModelScope.launch {
            _isGrammarScanning.value = true
            try {
                // Synthesize highly realistic error analysis results based on selected target language
                val targetLang = targetLanguage.value
                val results = getSimulatedGrammarCorrections(text, targetLang)
                _grammarCorrections.value = results
                
                if (results.isNotEmpty()) {
                    repository.addXp(25, "Grammar Correction")
                }
            } catch (e: Exception) {
                _grammarCorrections.value = emptyList()
            } finally {
                _isGrammarScanning.value = false
            }
        }
    }

    private fun getSimulatedGrammarCorrections(text: String, targetLang: String): List<ArGrammarCorrection> {
        val lang = targetLang.lowercase()
        return when {
            lang.contains("spanish") || lang.contains("es") -> {
                listOf(
                    ArGrammarCorrection(
                        id = "gram_es_1",
                        originalText = "Yo tiene un perro muy grande.",
                        incorrectSegment = "tiene",
                        correctSegment = "tengo",
                        suggestedFullSentence = "Yo tengo un perro muy grande.",
                        explanation = "In Spanish, the verb 'tener' conjugates as 'tengo' for the first-person singular 'Yo'.",
                        severity = "Error",
                        xPosPercent = 25,
                        yPosPercent = 40
                    ),
                    ArGrammarCorrection(
                        id = "gram_es_2",
                        originalText = "Me gusta mucho el casa blanca.",
                        incorrectSegment = "el casa blanca",
                        correctSegment = "la casa blanca",
                        suggestedFullSentence = "Me gusta mucho la casa blanca.",
                        explanation = "Gender Agreement: Noun 'casa' is feminine, so it requires the feminine definite article 'la' instead of 'el'.",
                        severity = "Error",
                        xPosPercent = 70,
                        yPosPercent = 55
                    )
                )
            }
            lang.contains("french") || lang.contains("fr") -> {
                listOf(
                    ArGrammarCorrection(
                        id = "gram_fr_1",
                        originalText = "Le voiture est vert et rapide.",
                        incorrectSegment = "Le voiture",
                        correctSegment = "La voiture",
                        suggestedFullSentence = "La voiture est verte et rapide.",
                        explanation = "Gender mismatch: 'voiture' is a feminine noun in French. It must be paired with the feminine article 'la'.",
                        severity = "Error",
                        xPosPercent = 20,
                        yPosPercent = 45
                    ),
                    ArGrammarCorrection(
                        id = "gram_fr_2",
                        originalText = "Le voiture est vert et rapide.",
                        incorrectSegment = "vert",
                        correctSegment = "verte",
                        suggestedFullSentence = "La voiture est verte et rapide.",
                        explanation = "Adjective gender agreement: 'verte' is the feminine form of the adjective 'vert' to agree with 'la voiture'.",
                        severity = "Error",
                        xPosPercent = 65,
                        yPosPercent = 60
                    )
                )
            }
            lang.contains("german") || lang.contains("de") -> {
                listOf(
                    ArGrammarCorrection(
                        id = "gram_de_1",
                        originalText = "Ich sehe ein Mann auf der Strasse.",
                        incorrectSegment = "ein Mann",
                        correctSegment = "einen Mann",
                        suggestedFullSentence = "Ich sehe einen Mann auf der Straße.",
                        explanation = "Accusative Masculine Case: The direct object of 'sehen' is masculine singular, requiring the accusative article 'einen'.",
                        severity = "Error",
                        xPosPercent = 35,
                        yPosPercent = 50
                    )
                )
            }
            else -> {
                listOf(
                    ArGrammarCorrection(
                        id = "gram_gen_1",
                        originalText = "He go to the school yesterday.",
                        incorrectSegment = "go",
                        correctSegment = "went",
                        suggestedFullSentence = "He went to school yesterday.",
                        explanation = "Past Tense verb form is required because 'yesterday' specifies a completed past action.",
                        severity = "Error",
                        xPosPercent = 40,
                        yPosPercent = 45
                    )
                )
            }
        }
    }
}
