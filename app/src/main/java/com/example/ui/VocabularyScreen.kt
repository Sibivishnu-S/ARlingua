package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LearnedWord
import com.example.data.OcrRecord
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VocabularyScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()
    val allOcrRecords by viewModel.allOcrRecords.collectAsStateWithLifecycle()
    val flashcardWords by viewModel.flashcardWords.collectAsStateWithLifecycle()
    val flashIndex by viewModel.flashcardIndex.collectAsStateWithLifecycle()
    val isFlipped by viewModel.isFlashcardFlipped.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Object Vocabulary, 1: OCR History
    var searchQuery by remember { mutableStateOf("") }
    var showFlashcardMode by remember { mutableStateOf(false) }
    var activePronunciationWord by remember { mutableStateOf<String?>(null) }

    // Word of the Day
    val wordOfTheDay = when (selectedLang) {
        "Spanish" -> LearnedWord(
            word = "Laptop",
            translation = "Portátil",
            ipa = "/poɾˈtatil/",
            exampleSentence = "Uso mi portátil para trabajar y estudiar.",
            exampleTranslation = "I use my laptop to work and study.",
            category = "Electronics",
            difficulty = "Beginner",
            language = "Spanish",
            confidence = 0.98f
        )
        "French" -> LearnedWord(
            word = "Laptop",
            translation = "Ordinateur portable",
            ipa = "/ɔʁ.di.na.tœʁ pɔʁ.tabl/",
            exampleSentence = "J'utilise mon ordinateur portable pour travailler.",
            exampleTranslation = "I use my laptop to work.",
            category = "Electronics",
            difficulty = "Beginner",
            language = "French",
            confidence = 0.97f
        )
        "Japanese" -> LearnedWord(
            word = "Laptop",
            translation = "Nōtopasokon (ノートパソコン)",
            ipa = "/noː.to.pa.so.koɴ/",
            exampleSentence = "毎日ノートパソコンで勉強します。",
            exampleTranslation = "I study with my laptop every day.",
            category = "Electronics",
            difficulty = "Intermediate",
            language = "Japanese",
            confidence = 0.96f
        )
        else -> LearnedWord(
            word = "Laptop",
            translation = "Laptop / Computer",
            ipa = "/ˈlæp.tɒp/",
            exampleSentence = "The laptop is on the desk.",
            exampleTranslation = "The laptop is on the desk.",
            category = "Electronics",
            difficulty = "Beginner",
            language = selectedLang,
            confidence = 0.99f
        )
    }

    val filteredWords = allWords.filter {
        it.language == selectedLang &&
        (it.word.contains(searchQuery, ignoreCase = true) || it.translation.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true))
    }

    val filteredOcrRecords = allOcrRecords.filter {
        it.language == selectedLang &&
        (it.recognizedText.contains(searchQuery, ignoreCase = true) || it.translation.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .testTag("vocabulary_screen")
    ) {
        if (showFlashcardMode) {
            // --- 1. SLIDING FLASHCARD PRACTICE VIEW ---
            val activeCard = flashcardWords.getOrNull(flashIndex) ?: wordOfTheDay

            val flipRotation by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "flip"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showFlashcardMode = false },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = CharcoalTextDark)
                    }
                    Text(
                        text = "Flashcard Game (${flashIndex + 1}/${flashcardWords.size.coerceAtLeast(1)})",
                        fontWeight = FontWeight.ExtraBold,
                        color = CharcoalTextDark,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .graphicsLayer {
                            rotationY = flipRotation
                            cameraDistance = 8 * density
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isFlipped) IndigoPurple else Color.White)
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { viewModel.flipFlashcard() }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (flipRotation <= 90f) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = activeCard.category.uppercase(),
                                    color = EmeraldDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = activeCard.word,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldDark,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "TAP CARD TO REVEAL MEANING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = activeCard.translation,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Pronunciation: ${activeCard.ipa}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SilverTextLight.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )

                            if (activeCard.exampleSentence.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = activeCard.exampleSentence,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = activeCard.exampleTranslation,
                                        fontSize = 12.sp,
                                        color = SilverTextLight.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.speakText(activeCard.translation, selectedLang) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Audio Speak", tint = Color.White)
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.nextFlashcard() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("NEXT FLASHCARD", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // --- 2. MAIN VOCABULARY & OCR MODULE ---
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Tabs: Mode 1 (Object Vocabulary) vs Mode 2 (OCR History)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateBackgroundLight, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Tab 1: Object Vocabulary
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (activeTab == 0) EmeraldGreen else Color.Transparent)
                                        .clickable { activeTab = 0 }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewInAr,
                                            contentDescription = null,
                                            tint = if (activeTab == 0) Color.White else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "My Words (${filteredWords.size})",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (activeTab == 0) Color.White else CharcoalTextDark
                                        )
                                    }
                                }

                                // Tab 2: Document OCR History
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (activeTab == 1) IndigoPurple else Color.Transparent)
                                        .clickable { activeTab = 1 }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DocumentScanner,
                                            contentDescription = null,
                                            tint = if (activeTab == 1) Color.White else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "OCR History (${filteredOcrRecords.size})",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (activeTab == 1) Color.White else CharcoalTextDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Word of the Day Banner (Shown on Vocabulary tab)
                if (activeTab == 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "WOTD", tint = AmberGold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("FEATURED VOCABULARY", fontWeight = FontWeight.ExtraBold, color = AmberGold, fontSize = 12.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AmberGold.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(wordOfTheDay.category.uppercase(), color = AmberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = wordOfTheDay.word,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldDark
                                        )
                                        Text(
                                            text = "${selectedLang}: ${wordOfTheDay.translation}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CharcoalTextDark
                                        )
                                        Text(wordOfTheDay.ipa, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { viewModel.speakText(wordOfTheDay.translation, selectedLang) },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(EmeraldGreen.copy(alpha = 0.1f), CircleShape)
                                        ) {
                                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Speak", tint = EmeraldGreen)
                                        }
                                        
                                        IconButton(
                                            onClick = { activePronunciationWord = wordOfTheDay.word },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(IndigoPurple.copy(alpha = 0.1f), CircleShape)
                                                .testTag("wotd_pronounce_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Practice", tint = IndigoPurple)
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateBackgroundLight, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(wordOfTheDay.exampleSentence, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                                    Text(wordOfTheDay.exampleTranslation, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // Search & Practice Flashcards Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeTab == 0) "Detected Objects ($selectedLang)" else "OCR Text Scans ($selectedLang)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CharcoalTextDark
                        )

                        if (activeTab == 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IndigoPurple.copy(alpha = 0.1f))
                                    .clickable {
                                        viewModel.startFlashcards()
                                        showFlashcardMode = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Flashcards", tint = IndigoPurple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PRACTICE FLASHCARDS", color = IndigoPurple, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        } else if (filteredOcrRecords.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearOcrHistory() }
                            ) {
                                Text("Clear History", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Search Bar Input Textfield
                item {
                    val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isListening) "Listening..." else "Search detected objects or words...", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("vocabulary_search_input"),
                        colors = AppTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        viewModel.stopVoiceRecognition()
                                    } else {
                                        viewModel.startVoiceRecognition(
                                            contextType = "SEARCH",
                                            languageName = selectedLang,
                                            onTextUpdate = { recognized -> searchQuery = recognized },
                                            onComplete = { finalSpeech -> searchQuery = finalSpeech }
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("vocabulary_search_microphone")
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Voice Search",
                                    tint = if (isListening) Color.Red else IndigoPurple
                                )
                            }
                        },
                        maxLines = 1
                    )
                }

                // Tab 0: OBJECT VOCABULARY LIST
                if (activeTab == 0) {
                    if (filteredWords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ViewInAr, contentDescription = "Empty", tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                    Text("No detected objects in $selectedLang yet", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Scan objects (Laptop, Chair, Bottle, Cup, Book, Phone) with AR Camera!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(filteredWords, key = { it.id }) { item ->
                            RedesignedVocabularyCard(
                                item = item,
                                selectedLang = selectedLang,
                                onSpeak = { viewModel.speakText(item.translation, selectedLang) },
                                onPractice = { activePronunciationWord = item.word },
                                onFavorite = { viewModel.toggleWordBookmark(item) },
                                onDelete = { viewModel.deleteVocabularyWord(item.id) }
                            )
                        }
                    }
                } else {
                    // Tab 1: OCR HISTORY LIST
                    if (filteredOcrRecords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = "Empty OCR", tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                    Text("No OCR Document History yet", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Use Document OCR Mode to scan books, signs, menus, or printed text!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(filteredOcrRecords, key = { it.id }) { record ->
                            OcrHistoryCard(
                                record = record,
                                selectedLang = selectedLang,
                                onSpeak = { viewModel.speakText(record.translation, selectedLang) },
                                onAskTutor = {
                                    viewModel.sendTutorMessage("Explain this scanned text: \"${record.recognizedText}\" (Translated: \"${record.translation}\" in $selectedLang).")
                                    viewModel.requestRouteSwitch("tutor")
                                },
                                onDelete = { viewModel.deleteOcrRecord(record.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (activePronunciationWord != null) {
        PronunciationCoachDialog(
            word = activePronunciationWord!!,
            viewModel = viewModel,
            onDismiss = { activePronunciationWord = null }
        )
    }
}

/**
 * REDESIGNED VOCABULARY CARD
 * Standardized typography: Title 24px Bold, Subtitle 16px, Description 14px.
 * Proper constraints: No vertical wrapping bugs, clean category thumbnail, confidence chip, example sentence, date scanned.
 */
@Composable
fun RedesignedVocabularyCard(
    item: LearnedWord,
    selectedLang: String,
    onSpeak: () -> Unit,
    onPractice: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val confidencePercent = (item.confidence * 100).toInt().coerceIn(75, 99)
    val categoryIcon = getCategoryVectorIcon(item.category)
    val formattedDate = remember(item.lastReviewed) { formatDateString(item.lastReviewed) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Badges & Favorite Star Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            color = EmeraldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Difficulty Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (item.difficulty.lowercase()) {
                                    "beginner" -> EmeraldGreen.copy(alpha = 0.15f)
                                    "intermediate" -> AmberGold.copy(alpha = 0.15f)
                                    else -> Color.Red.copy(alpha = 0.12f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.difficulty.uppercase(),
                            color = when (item.difficulty.lowercase()) {
                                "beginner" -> EmeraldDark
                                "intermediate" -> AmberGold
                                else -> Color.Red
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Confidence Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(IndigoPurple.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$confidencePercent% CONFIDENCE",
                            color = IndigoPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (item.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isBookmarked) AmberGold else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Object Thumbnail & Name Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Visual Object Category Thumbnail Box
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldGreen.copy(alpha = 0.2f), IndigoPurple.copy(alpha = 0.2f))
                            )
                        )
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = item.category,
                        tint = EmeraldDark,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Name & Translation Block
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Title: 24px Bold
                    Text(
                        text = item.word,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Subtitle: 16px
                    Text(
                        text = "$selectedLang: ${item.translation}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CharcoalTextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Pronunciation IPA
                    Text(
                        text = "IPA: ${item.ipa}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Example Sentence Box (Description: 14px)
            if (item.exampleSentence.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBackgroundLight, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Example Sentence:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldDark
                    )
                    Text(
                        text = item.exampleSentence,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalTextDark
                    )
                    if (item.exampleTranslation.isNotBlank()) {
                        Text(
                            text = item.exampleTranslation,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

            // Footer Row: Date Scanned & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speak Button
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier
                            .size(36.dp)
                            .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Speak", tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                    }

                    // Pronunciation Practice Button
                    IconButton(
                        onClick = onPractice,
                        modifier = Modifier
                            .size(36.dp)
                            .background(IndigoPurple.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Practice", tint = IndigoPurple, modifier = Modifier.size(18.dp))
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * OCR HISTORY CARD
 * Separated Document OCR Scans History Item.
 */
@Composable
fun OcrHistoryCard(
    record: OcrRecord,
    selectedLang: String,
    onSpeak: () -> Unit,
    onAskTutor: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedDate = remember(record.scanTime) { formatDateString(record.scanTime) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(IndigoPurple.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "DOCUMENT OCR TEXT",
                        color = IndigoPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // Recognized Text
            Text(
                text = record.recognizedText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CharcoalTextDark
            )

            // Translation Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateBackgroundLight, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("Translation ($selectedLang):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                Text(record.translation, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CharcoalTextDark)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier
                            .size(34.dp)
                            .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Speak", tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onAskTutor,
                        modifier = Modifier
                            .size(34.dp)
                            .background(IndigoPurple.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Ask AI Tutor", tint = IndigoPurple, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun getCategoryVectorIcon(category: String): ImageVector {
    val clean = category.lowercase()
    return when {
        clean.contains("elec") || clean.contains("laptop") || clean.contains("computer") -> Icons.Default.Laptop
        clean.contains("furn") || clean.contains("chair") || clean.contains("table") -> Icons.Default.SingleBed
        clean.contains("kitch") || clean.contains("food") || clean.contains("cup") || clean.contains("bottle") -> Icons.Default.LocalDining
        clean.contains("book") || clean.contains("document") -> Icons.Default.MenuBook
        clean.contains("phone") || clean.contains("mobile") -> Icons.Default.Smartphone
        else -> Icons.Default.Category
    }
}

private fun formatDateString(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
