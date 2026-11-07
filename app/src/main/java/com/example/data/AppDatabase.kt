package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProgress::class,
        LearnedWord::class,
        OcrRecord::class,
        ActivityLog::class,
        QuizResult::class,
        TutorMessage::class,
        PronunciationPractice::class,
        PlannerTask::class,
        DetectedObjectEntity::class,
        GrammarMistakeEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProgressDao(): UserProgressDao
    abstract fun learnedWordDao(): LearnedWordDao
    abstract fun ocrRecordDao(): OcrRecordDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun tutorMessageDao(): TutorMessageDao
    abstract fun pronunciationPracticeDao(): PronunciationPracticeDao
    abstract fun plannerTaskDao(): PlannerTaskDao
    abstract fun detectedObjectDao(): DetectedObjectDao
    abstract fun grammarMistakeDao(): GrammarMistakeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "arlingua_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
