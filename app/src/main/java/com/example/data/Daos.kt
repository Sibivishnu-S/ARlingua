package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 'current_user' LIMIT 1")
    fun getProgress(): Flow<UserProgress?>

    @Query("SELECT * FROM user_progress WHERE id = 'current_user' LIMIT 1")
    suspend fun getProgressDirect(): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: UserProgress)
}

@Dao
interface LearnedWordDao {
    @Query("SELECT * FROM learned_words ORDER BY lastReviewed DESC")
    fun getAllWords(): Flow<List<LearnedWord>>

    @Query("SELECT * FROM learned_words WHERE language = :language ORDER BY lastReviewed DESC")
    fun getWordsByLanguage(language: String): Flow<List<LearnedWord>>

    @Query("SELECT * FROM learned_words WHERE isBookmarked = 1 ORDER BY lastReviewed DESC")
    fun getBookmarkedWords(): Flow<List<LearnedWord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: LearnedWord)

    @Query("UPDATE learned_words SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM learned_words WHERE id = :id")
    suspend fun deleteWord(id: Long)
}

@Dao
interface OcrRecordDao {
    @Query("SELECT * FROM ocr_records ORDER BY scanTime DESC")
    fun getAllOcrRecords(): Flow<List<OcrRecord>>

    @Query("SELECT * FROM ocr_records WHERE language = :language ORDER BY scanTime DESC")
    fun getOcrRecordsByLanguage(language: String): Flow<List<OcrRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrRecord(record: OcrRecord)

    @Query("DELETE FROM ocr_records WHERE id = :id")
    suspend fun deleteOcrRecord(id: Long)

    @Query("DELETE FROM ocr_records")
    suspend fun clearOcrHistory()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog)
}

@Dao
interface QuizResultDao {
    @Query("SELECT * FROM quiz_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<QuizResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: QuizResult)
}

@Dao
interface TutorMessageDao {
    @Query("SELECT * FROM tutor_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<TutorMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TutorMessage)

    @Query("DELETE FROM tutor_messages")
    suspend fun clearChat()
}

@Dao
interface GrammarMistakeDao {
    @Query("SELECT * FROM grammar_mistakes ORDER BY timestamp DESC")
    fun getAllMistakes(): Flow<List<GrammarMistakeEntity>>

    @Query("SELECT * FROM grammar_mistakes WHERE targetLanguage = :language ORDER BY timestamp DESC")
    fun getMistakesByLanguage(language: String): Flow<List<GrammarMistakeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: GrammarMistakeEntity)

    @Query("SELECT * FROM grammar_mistakes WHERE LOWER(originalText) = LOWER(:originalText) AND targetLanguage = :language LIMIT 1")
    suspend fun findMistake(originalText: String, language: String): GrammarMistakeEntity?

    @Query("UPDATE grammar_mistakes SET timesRepeated = timesRepeated + 1, timestamp = :timestamp WHERE id = :id")
    suspend fun incrementRepeatCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE grammar_mistakes SET isMastered = 1 WHERE id = :id")
    suspend fun markAsMastered(id: Long)

    @Query("DELETE FROM grammar_mistakes")
    suspend fun clearAllMistakes()
}

@Dao
interface PronunciationPracticeDao {
    @Query("SELECT * FROM pronunciation_practices ORDER BY timestamp DESC")
    fun getAllPractices(): Flow<List<PronunciationPractice>>

    @Query("SELECT * FROM pronunciation_practices WHERE language = :language ORDER BY timestamp DESC")
    fun getPracticesByLanguage(language: String): Flow<List<PronunciationPractice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPractice(practice: PronunciationPractice)
}

@Dao
interface PlannerTaskDao {
    @Query("SELECT * FROM planner_tasks ORDER BY dueDate ASC, priority DESC")
    fun getAllTasks(): Flow<List<PlannerTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: PlannerTask)

    @Query("UPDATE planner_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM planner_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM planner_tasks")
    suspend fun clearAllTasks()
}

@Dao
interface DetectedObjectDao {
    @Query("SELECT * FROM detected_object_history ORDER BY detectedTimestamp DESC")
    fun getAllDetectedObjects(): Flow<List<DetectedObjectEntity>>

    @Query("SELECT * FROM detected_object_history WHERE language = :language ORDER BY detectedTimestamp DESC")
    fun getDetectedObjectsByLanguage(language: String): Flow<List<DetectedObjectEntity>>

    @Query("SELECT * FROM detected_object_history WHERE LOWER(objectNameEn) = LOWER(:objectNameEn) AND language = :language LIMIT 1")
    suspend fun findCachedObject(objectNameEn: String, language: String): DetectedObjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedObject(obj: DetectedObjectEntity)

    @Query("UPDATE detected_object_history SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM detected_object_history WHERE id = :id")
    suspend fun deleteDetectedObject(id: Long)

    @Query("DELETE FROM detected_object_history")
    suspend fun clearHistory()
}
