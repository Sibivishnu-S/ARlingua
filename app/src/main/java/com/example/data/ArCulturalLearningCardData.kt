package com.example.data

import androidx.compose.runtime.Stable

/**
 * Data model representing a complete 7-card AR Cultural Learning Card experience.
 */
@Stable
data class ArCulturalLearningCardData(
    val objectNameEn: String,
    val objectNameTarget: String,
    val pronunciationIpa: String,
    val targetLanguage: String,
    val category: String,
    val difficulty: String,
    val confidence: Float,
    val detectionEngine: String = "Detected by YOLO",

    // Card 2: Meaning & Details
    val meaning: String,
    val shortDescription: String,
    val usageContext: String,

    // Card 3: Sentence & Grammar
    val exampleSentenceTarget: String,
    val exampleSentenceEn: String,
    val grammarHighlight: String,
    val wordBreakdown: List<WordBreakdownItem> = emptyList(),

    // Card 4: Related Vocabulary (5-8 items)
    val relatedVocabulary: List<RelatedWordItem> = emptyList(),

    // Card 5: Cultural Learning Fact
    val culturalFact: String,
    val culturalContextRegion: String = "",

    // Card 6: Mini Quiz
    val miniQuizQuestion: String,
    val miniQuizOptions: List<String>,
    val miniQuizCorrectIndex: Int,
    val miniQuizExplanation: String,

    // Card 7: Learning Summary & Stats
    val xpReward: Int = 35,
    val masteryProgressPercent: Int = 85,

    val isFavorite: Boolean = false,
    val isSaved: Boolean = false
)

@Stable
data class WordBreakdownItem(
    val part: String,
    val partMeaning: String,
    val role: String // e.g. "Article", "Root", "Gender Marker", "Suffix"
)

@Stable
data class RelatedWordItem(
    val wordEn: String,
    val wordTarget: String,
    val pronunciationIpa: String,
    val category: String = "General"
)
