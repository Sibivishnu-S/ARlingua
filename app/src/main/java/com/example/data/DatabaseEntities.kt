package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey val id: String = "current_user",
    val xp: Int = 120, // Start with some initial XP for polish
    val level: Int = 1,
    val streak: Int = 3, // Start with some active streak
    val lastPracticeTimestamp: Long = System.currentTimeMillis(),
    val coins: Int = 450
)

@Entity(tableName = "learned_words")
data class LearnedWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val translation: String,
    val ipa: String, // Phonetic spelling, e.g., /bəˈnænə/
    val exampleSentence: String,
    val exampleTranslation: String,
    val category: String, // e.g. "Food", "Street", "Kitchen"
    val difficulty: String, // e.g. "Beginner", "Intermediate", "Advanced"
    val language: String, // Target language e.g. "French", "Japanese"
    val isBookmarked: Boolean = false,
    val lastReviewed: Long = System.currentTimeMillis(),
    val accuracyScore: Int = 0, // Pronunciation score out of 100
    val confidence: Float = 0.95f, // Model detection confidence e.g. 0.97f
    val imagePath: String? = null // Optional local thumbnail path or image identifier
)

@Entity(tableName = "ocr_records")
data class OcrRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recognizedText: String,
    val translation: String,
    val language: String,
    val scanTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val xpGained: Int,
    val type: String, // "QUIZ", "AR_SCAN", "OCR", "TUTOR", "SPEAKING"
    val durationSeconds: Int = 60
)

@Entity(tableName = "quiz_results")
data class QuizResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val score: Int,
    val totalQuestions: Int,
    val difficulty: String,
    val language: String
)

@Entity(tableName = "tutor_messages")
data class TutorMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val analysisJson: String? = null
)

@Entity(tableName = "grammar_mistakes")
data class GrammarMistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val correctedText: String,
    val mistakeCategory: String, // e.g., "Verb Tense", "Article", "Word Order", "Preposition"
    val explanation: String,
    val targetLanguage: String,
    val cefrLevel: String = "A1",
    val timestamp: Long = System.currentTimeMillis(),
    val timesRepeated: Int = 1,
    val isMastered: Boolean = false
)

@Entity(tableName = "pronunciation_practices")
data class PronunciationPractice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val score: Int,
    val accuracyPercent: Int,
    val correctSounds: String, // Comma separated
    val incorrectSounds: String, // Comma separated
    val suggestions: String,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String
)

@Entity(tableName = "planner_tasks")
data class PlannerTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // "Vocabulary", "Quiz", "Pronunciation", "Revision"
    val priority: String, // "High", "Medium", "Low"
    val isCompleted: Boolean = false,
    val dueDate: Long = System.currentTimeMillis(),
    val recommendedLesson: String,
    val estimatedMinutes: Int = 15
)

@Entity(tableName = "detected_object_history")
data class DetectedObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectNameEn: String,
    val objectNameTarget: String,
    val pronunciationIpa: String,
    val exampleSentenceTarget: String,
    val exampleSentenceEn: String,
    val category: String,
    val difficulty: String,
    val language: String,
    val confidence: Float = 0.95f,
    val detectionEngine: String = "Detected by YOLO",
    val detectedTimestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
)


