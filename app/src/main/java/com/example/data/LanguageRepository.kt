package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageRepository(val db: AppDatabase) {

    val userProgress: Flow<UserProgress?> = db.userProgressDao().getProgress()
    val allWords: Flow<List<LearnedWord>> = db.learnedWordDao().getAllWords()
    val bookmarkedWords: Flow<List<LearnedWord>> = db.learnedWordDao().getBookmarkedWords()
    val allOcrRecords: Flow<List<OcrRecord>> = db.ocrRecordDao().getAllOcrRecords()
    val activityLogs: Flow<List<ActivityLog>> = db.activityLogDao().getAllLogs()
    val quizResults: Flow<List<QuizResult>> = db.quizResultDao().getAllResults()
    val chatMessages: Flow<List<TutorMessage>> = db.tutorMessageDao().getAllMessages()
    val allGrammarMistakes: Flow<List<GrammarMistakeEntity>> = db.grammarMistakeDao().getAllMistakes()
    val allPractices: Flow<List<PronunciationPractice>> = db.pronunciationPracticeDao().getAllPractices()
    val allTasks: Flow<List<PlannerTask>> = db.plannerTaskDao().getAllTasks()
    val allDetectedObjects: Flow<List<DetectedObjectEntity>> = db.detectedObjectDao().getAllDetectedObjects()

    fun getDetectedObjectsByLanguage(language: String): Flow<List<DetectedObjectEntity>> {
        return db.detectedObjectDao().getDetectedObjectsByLanguage(language)
    }

    suspend fun findCachedDetectedObject(objectNameEn: String, language: String): DetectedObjectEntity? = withContext(Dispatchers.IO) {
        db.detectedObjectDao().findCachedObject(objectNameEn, language)
    }

    suspend fun insertDetectedObject(obj: DetectedObjectEntity) = withContext(Dispatchers.IO) {
        db.detectedObjectDao().insertDetectedObject(obj)
    }

    suspend fun updateDetectedObjectBookmark(id: Long, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        db.detectedObjectDao().updateBookmark(id, isBookmarked)
    }

    suspend fun deleteDetectedObject(id: Long) = withContext(Dispatchers.IO) {
        db.detectedObjectDao().deleteDetectedObject(id)
    }

    suspend fun clearDetectedObjectsHistory() = withContext(Dispatchers.IO) {
        db.detectedObjectDao().clearHistory()
    }

    fun getPracticesByLanguage(language: String): Flow<List<PronunciationPractice>> {
        return db.pronunciationPracticeDao().getPracticesByLanguage(language)
    }

    suspend fun insertPractice(practice: PronunciationPractice) = withContext(Dispatchers.IO) {
        db.pronunciationPracticeDao().insertPractice(practice)
    }

    suspend fun insertTask(task: PlannerTask) = withContext(Dispatchers.IO) {
        db.plannerTaskDao().insertTask(task)
    }

    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        db.plannerTaskDao().updateTaskStatus(id, isCompleted)
    }

    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        db.plannerTaskDao().deleteTask(id)
    }

    suspend fun clearAllTasks() = withContext(Dispatchers.IO) {
        db.plannerTaskDao().clearAllTasks()
    }

    fun getWordsByLanguage(language: String): Flow<List<LearnedWord>> {
        return db.learnedWordDao().getWordsByLanguage(language)
    }

    suspend fun updateBookmark(id: Long, isBookmarked: Boolean) {
        db.learnedWordDao().updateBookmark(id, isBookmarked)
    }

    suspend fun insertWord(word: LearnedWord) {
        db.learnedWordDao().insertWord(word)
    }

    suspend fun deleteWord(id: Long) {
        db.learnedWordDao().deleteWord(id)
    }

    fun getOcrRecordsByLanguage(language: String): Flow<List<OcrRecord>> {
        return db.ocrRecordDao().getOcrRecordsByLanguage(language)
    }

    suspend fun insertOcrRecord(record: OcrRecord) = withContext(Dispatchers.IO) {
        db.ocrRecordDao().insertOcrRecord(record)
    }

    suspend fun deleteOcrRecord(id: Long) = withContext(Dispatchers.IO) {
        db.ocrRecordDao().deleteOcrRecord(id)
    }

    suspend fun clearOcrHistory() = withContext(Dispatchers.IO) {
        db.ocrRecordDao().clearOcrHistory()
    }

    suspend fun addXp(amount: Int, activityType: String) = withContext(Dispatchers.IO) {
        val currentProgress = db.userProgressDao().getProgressDirect() ?: UserProgress()
        val newXp = currentProgress.xp + amount
        val xpNeededForNextLevel = currentProgress.level * 150
        val didLevelUp = newXp >= xpNeededForNextLevel
        
        val newLevel = if (didLevelUp) currentProgress.level + 1 else currentProgress.level
        val newCoins = if (didLevelUp) currentProgress.coins + 100 else currentProgress.coins + (amount / 10)

        // Calculate streak
        val lastPractice = currentProgress.lastPracticeTimestamp
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val currentDayMillis = System.currentTimeMillis()
        val difference = currentDayMillis - lastPractice

        val newStreak = if (difference in (oneDayMillis..oneDayMillis * 2)) {
            currentProgress.streak + 1
        } else if (difference > oneDayMillis * 2) {
            1 // Streak reset if missed more than 1 day
        } else {
            currentProgress.streak // Same day, keep current
        }

        val updatedProgress = currentProgress.copy(
            xp = if (didLevelUp) newXp - xpNeededForNextLevel else newXp,
            level = newLevel,
            coins = newCoins,
            streak = newStreak,
            lastPracticeTimestamp = currentDayMillis
        )
        db.userProgressDao().updateProgress(updatedProgress)

        // Log the activity
        db.activityLogDao().insertLog(
            ActivityLog(
                xpGained = amount,
                type = activityType
            )
        )
    }

    suspend fun insertQuizResult(score: Int, total: Int, difficulty: String, language: String) = withContext(Dispatchers.IO) {
        db.quizResultDao().insertResult(
            QuizResult(
                score = score,
                totalQuestions = total,
                difficulty = difficulty,
                language = language
            )
        )
        // Award XP: 10 XP per correct answer, + 20 XP bonus if 100% correct
        val baseScoreXp = score * 10
        val bonus = if (score == total) 20 else 0
        addXp(baseScoreXp + bonus, "QUIZ")
    }

    suspend fun insertTutorMessage(role: String, content: String, analysisJson: String? = null) = withContext(Dispatchers.IO) {
        db.tutorMessageDao().insertMessage(
            TutorMessage(role = role, content = content, analysisJson = analysisJson)
        )
    }

    fun getMistakesByLanguage(language: String): Flow<List<GrammarMistakeEntity>> {
        return db.grammarMistakeDao().getMistakesByLanguage(language)
    }

    suspend fun insertOrIncrementGrammarMistake(
        originalText: String,
        correctedText: String,
        mistakeCategory: String,
        explanation: String,
        targetLanguage: String,
        cefrLevel: String = "A1"
    ) = withContext(Dispatchers.IO) {
        val existing = db.grammarMistakeDao().findMistake(originalText.trim(), targetLanguage.trim())
        if (existing != null) {
            db.grammarMistakeDao().incrementRepeatCount(existing.id)
        } else {
            db.grammarMistakeDao().insertMistake(
                GrammarMistakeEntity(
                    originalText = originalText.trim(),
                    correctedText = correctedText.trim(),
                    mistakeCategory = mistakeCategory,
                    explanation = explanation,
                    targetLanguage = targetLanguage.trim(),
                    cefrLevel = cefrLevel
                )
            )
        }
    }

    suspend fun markMistakeMastered(id: Long) = withContext(Dispatchers.IO) {
        db.grammarMistakeDao().markAsMastered(id)
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        db.tutorMessageDao().clearChat()
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val existingProgress = db.userProgressDao().getProgressDirect()
        if (existingProgress == null) {
            // Seed User Progress
            db.userProgressDao().updateProgress(UserProgress(
                xp = 180,
                level = 2,
                streak = 5,
                coins = 320,
                lastPracticeTimestamp = System.currentTimeMillis() - 4 * 60 * 60 * 1000L // practiced 4 hours ago
            ))

            // Seed some default vocabulary words so the database starts nicely populated
            val seedWords = listOf(
                LearnedWord(
                    word = "Bonjour",
                    translation = "Hello",
                    ipa = "/bɔ̃.ʒuʁ/",
                    exampleSentence = "Bonjour, comment ça va ?",
                    exampleTranslation = "Hello, how is it going?",
                    category = "Greetings",
                    difficulty = "Beginner",
                    language = "French",
                    isBookmarked = true,
                    accuracyScore = 92
                ),
                LearnedWord(
                    word = "Croissant",
                    translation = "Croissant / Crescent roll",
                    ipa = "/kʁwa.sɑ̃/",
                    exampleSentence = "Je mange un croissant chaud.",
                    exampleTranslation = "I am eating a warm croissant.",
                    category = "Food",
                    difficulty = "Beginner",
                    language = "French",
                    isBookmarked = false,
                    accuracyScore = 85
                ),
                LearnedWord(
                    word = "Manzana",
                    translation = "Apple",
                    ipa = "/manˈθana/",
                    exampleSentence = "La manzana es roja y dulce.",
                    exampleTranslation = "The apple is red and sweet.",
                    category = "Food",
                    difficulty = "Beginner",
                    language = "Spanish",
                    isBookmarked = true,
                    accuracyScore = 88
                ),
                LearnedWord(
                    word = "Gracias",
                    translation = "Thank you",
                    ipa = "/ˈɡɾaθjas/",
                    exampleSentence = "Muchas gracias por la comida.",
                    exampleTranslation = "Thank you very much for the meal.",
                    category = "Greetings",
                    difficulty = "Beginner",
                    language = "Spanish",
                    isBookmarked = false,
                    accuracyScore = 95
                ),
                LearnedWord(
                    word = "Konnichiwa (こんにちは)",
                    translation = "Hello / Good afternoon",
                    ipa = "/kon.ni.tɕi.wa/",
                    exampleSentence = "こんにちは、お元気ですか？",
                    exampleTranslation = "Hello, how are you?",
                    category = "Greetings",
                    difficulty = "Beginner",
                    language = "Japanese",
                    isBookmarked = true,
                    accuracyScore = 90
                ),
                LearnedWord(
                    word = "Neko (猫)",
                    translation = "Cat",
                    ipa = "/ne.ko/",
                    exampleSentence = "黒い猫が庭にいます。",
                    exampleTranslation = "A black cat is in the garden.",
                    category = "Animals",
                    difficulty = "Beginner",
                    language = "Japanese",
                    isBookmarked = false,
                    accuracyScore = 82
                )
            )
            for (word in seedWords) {
                db.learnedWordDao().insertWord(word)
            }

            // Seed initial detected object history into Room database
            val seedDetectedObjects = listOf(
                DetectedObjectEntity(
                    objectNameEn = "Laptop",
                    objectNameTarget = "Portátil",
                    pronunciationIpa = "/poɾˈtatil/",
                    exampleSentenceTarget = "El ordenador portátil está en la mesa.",
                    exampleSentenceEn = "The laptop is on the table.",
                    category = "Electronics",
                    difficulty = "Beginner",
                    language = "Spanish",
                    confidence = 0.98f,
                    detectedTimestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
                ),
                DetectedObjectEntity(
                    objectNameEn = "Coffee Cup",
                    objectNameTarget = "Taza de café",
                    pronunciationIpa = "/ˈta.θa/",
                    exampleSentenceTarget = "Tomo una taza de café caliente.",
                    exampleSentenceEn = "I drink a cup of hot coffee.",
                    category = "Kitchen",
                    difficulty = "Beginner",
                    language = "Spanish",
                    confidence = 0.96f,
                    detectedTimestamp = System.currentTimeMillis() - 5 * 60 * 60 * 1000L
                ),
                DetectedObjectEntity(
                    objectNameEn = "Water Bottle",
                    objectNameTarget = "Bouteille d'eau",
                    pronunciationIpa = "/bu.tɛj do/",
                    exampleSentenceTarget = "La bouteille d'eau est fraîche.",
                    exampleSentenceEn = "The water bottle is fresh.",
                    category = "Kitchen",
                    difficulty = "Beginner",
                    language = "French",
                    confidence = 0.95f,
                    detectedTimestamp = System.currentTimeMillis() - 10 * 60 * 60 * 1000L
                )
            )
            for (detectedObj in seedDetectedObjects) {
                db.detectedObjectDao().insertDetectedObject(detectedObj)
            }

            // Seed some past activity logs so progress charts and heatmaps display data immediately
            val now = System.currentTimeMillis()
            val dayMillis = 24 * 60 * 60 * 1000L
            val initialLogs = listOf(
                ActivityLog(timestamp = now - 4 * dayMillis, xpGained = 40, type = "QUIZ", durationSeconds = 120),
                ActivityLog(timestamp = now - 3 * dayMillis, xpGained = 30, type = "AR_SCAN", durationSeconds = 80),
                ActivityLog(timestamp = now - 2 * dayMillis, xpGained = 50, type = "SPEAKING", durationSeconds = 150),
                ActivityLog(timestamp = now - 1 * dayMillis, xpGained = 20, type = "TUTOR", durationSeconds = 90),
                ActivityLog(timestamp = now, xpGained = 45, type = "OCR", durationSeconds = 60)
            )
            for (log in initialLogs) {
                db.activityLogDao().insertLog(log)
            }

            // Seed some quiz results
            val initialQuizzes = listOf(
                QuizResult(timestamp = now - 4 * dayMillis, score = 8, totalQuestions = 10, difficulty = "Beginner", language = "Spanish"),
                QuizResult(timestamp = now - 2 * dayMillis, score = 5, totalQuestions = 5, difficulty = "Beginner", language = "French")
            )
            for (quiz in initialQuizzes) {
                db.quizResultDao().insertResult(quiz)
            }

            // Seed some pronunciation practices
            val initialPractices = listOf(
                PronunciationPractice(
                    word = "Bonjour",
                    score = 92,
                    accuracyPercent = 92,
                    correctSounds = "b, o, n, j, ou",
                    incorrectSounds = "r",
                    suggestions = "Your pronunciation is very clear. Soften the final 'r' slightly to sound even more native.",
                    language = "French",
                    timestamp = now - 3 * dayMillis
                ),
                PronunciationPractice(
                    word = "Gracias",
                    score = 95,
                    accuracyPercent = 95,
                    correctSounds = "g, r, a, c, i, a, s",
                    incorrectSounds = "",
                    suggestions = "Excellent! Native level pronunciation and cadence.",
                    language = "Spanish",
                    timestamp = now - dayMillis
                )
            )
            for (practice in initialPractices) {
                db.pronunciationPracticeDao().insertPractice(practice)
            }

            // Seed study tasks
            val initialTasks = listOf(
                PlannerTask(
                    title = "Review 'Bonjour' Pronunciation",
                    category = "Pronunciation",
                    priority = "High",
                    isCompleted = false,
                    dueDate = now,
                    recommendedLesson = "Greetings Pronunciation Coach",
                    estimatedMinutes = 10
                ),
                PlannerTask(
                    title = "Learn 3 new Food terms",
                    category = "Vocabulary",
                    priority = "Medium",
                    isCompleted = false,
                    dueDate = now + dayMillis,
                    recommendedLesson = "Marketplace AR Scanner",
                    estimatedMinutes = 15
                ),
                PlannerTask(
                    title = "Complete Beginner Quiz",
                    category = "Quiz",
                    priority = "High",
                    isCompleted = true,
                    dueDate = now - dayMillis,
                    recommendedLesson = "Quiz Arena: Beginner Grammar",
                    estimatedMinutes = 15
                )
            )
            for (task in initialTasks) {
                db.plannerTaskDao().insertTask(task)
            }
        }
    }
}
