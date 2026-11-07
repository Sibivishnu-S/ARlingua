package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArCulturalLearningCardData
import com.example.data.RelatedWordItem
import com.example.data.WordBreakdownItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Floating Material 3 Bottom Sheet displaying the 7-Card AR Cultural Learning Experience.
 * 
 * Features:
 * - Keeps live camera preview visible behind translucent glassmorphic sheet.
 * - Collapsed State: Displays Object Name, Translation, IPA, and Expand button.
 * - Expanded State: Displays 7 swipeable educational cards:
 *   1. Overview & Audio
 *   2. Meaning & Details
 *   3. Example Sentence & Grammar
 *   4. Related Vocabulary (Clickable items load new cards)
 *   5. Cultural Learning Fact
 *   6. Adaptive Mini Quiz with Immediate Feedback
 *   7. Learning Summary & Save Progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArCulturalLearningCardBottomSheet(
    cardData: ArCulturalLearningCardData?,
    isLoading: Boolean,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSpeakText: (String) -> Unit,
    onToggleFavorite: (ArCulturalLearningCardData) -> Unit,
    onSaveToVocabulary: (ArCulturalLearningCardData) -> Unit,
    onSelectRelatedWord: (String) -> Unit,
    onCompleteQuiz: (isCorrect: Boolean, xpEarned: Int) -> Unit
) {
    if (!isVisible || (cardData == null && !isLoading)) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var isFavoriteLocal by remember(cardData) { mutableStateOf(cardData?.isFavorite == true) }
    var isSavedLocal by remember(cardData) { mutableStateOf(cardData?.isSaved == true) }

    // Pager state for the 7 swipeable cards
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 7 })

    val cardTabs = listOf(
        CardTabInfo("Overview", Icons.Default.Style, "Card 1"),
        CardTabInfo("Meaning", Icons.Default.MenuBook, "Card 2"),
        CardTabInfo("Grammar", Icons.Default.ChatBubbleOutline, "Card 3"),
        CardTabInfo("Related", Icons.Default.GridView, "Card 4"),
        CardTabInfo("Culture", Icons.Default.Public, "Card 5"),
        CardTabInfo("Quiz", Icons.Default.Quiz, "Card 6"),
        CardTabInfo("Summary", Icons.Default.MilitaryTech, "Card 7")
    )

    // Animated sheet height
    val targetMaxHeight = if (isExpanded) 0.85f else 0.28f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ar_cultural_learning_sheet_container"),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Translucent Background Dimmer when expanded
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { isExpanded = false }
            )
        }

        // Floating Bottom Sheet Container
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(targetMaxHeight)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 20.dp, bottomEnd = 20.dp)),
            color = SlateSurfaceDark.copy(alpha = 0.94f),
            border = BorderStroke(1.5.dp, EmeraldLight),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Drag Handle & Expand Toggle Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                            .align(Alignment.CenterVertically)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldGreen.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, EmeraldLight)
                        ) {
                            Text(
                                text = cardData?.targetLanguage ?: "AR Learning",
                                color = EmeraldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (isLoading) {
                    // Loading State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = EmeraldLight, strokeWidth = 3.dp)
                            Text(
                                text = "Generating AR Cultural Learning Card...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (cardData != null) {
                    // --- COLLAPSED VIEW HEADER ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = cardData.objectNameTarget,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (cardData.pronunciationIpa.isNotBlank()) {
                                    Text(
                                        text = cardData.pronunciationIpa,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )
                                }
                            }

                            Text(
                                text = "English: ${cardData.objectNameEn} • Category: ${cardData.category}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSpeakText(cardData.objectNameTarget) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(EmeraldGreen.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Speak", tint = EmeraldLight, modifier = Modifier.size(20.dp))
                            }

                            Button(
                                onClick = { isExpanded = !isExpanded },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isExpanded) IndigoPurple else EmeraldGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("expand_card_toggle_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.OpenInFull,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isExpanded) "Collapse" else "Cards ↗",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // --- EXPANDED CONTENT: 7 SWIPEABLE CARDS ---
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Scrollable Tab Selector Bar
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(cardTabs) { index, tab ->
                                    val isSelected = pagerState.currentPage == index
                                    Surface(
                                        onClick = {
                                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                        border = if (isSelected) BorderStroke(1.dp, EmeraldLight) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tab.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }

                            // Horizontal Swipe Pager
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { page ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp)
                                    ) {
                                        when (page) {
                                            0 -> Card1OverviewPage(cardData, isFavoriteLocal, isSavedLocal, onSpeakText, onToggleFavorite = {
                                                isFavoriteLocal = !isFavoriteLocal
                                                onToggleFavorite(cardData.copy(isFavorite = isFavoriteLocal))
                                            }, onSaveToVocabulary = {
                                                isSavedLocal = true
                                                onSaveToVocabulary(cardData.copy(isSaved = true))
                                            })
                                            1 -> Card2MeaningPage(cardData)
                                            2 -> Card3SentenceGrammarPage(cardData, onSpeakText)
                                            3 -> Card4RelatedVocabularyPage(cardData, onSelectRelatedWord)
                                            4 -> Card5CulturalLearningPage(cardData, onSpeakText)
                                            5 -> Card6MiniQuizPage(cardData, onCompleteQuiz)
                                            6 -> Card7LearningSummaryPage(cardData, onSaveToVocabulary)
                                        }
                                    }
                                }
                            }

                            // Page Indicator Dots
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(7) { index ->
                                    val isSelected = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (isSelected) 10.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) EmeraldLight else Color.Gray.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CardTabInfo(val title: String, val icon: ImageVector, val tag: String)

// --- CARD 1: OVERVIEW & AUDIO ---
@Composable
private fun Card1OverviewPage(
    cardData: ArCulturalLearningCardData,
    isFavorite: Boolean,
    isSaved: Boolean,
    onSpeakText: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveToVocabulary: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = getIconForCategory(cardData.category), contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Card 1 • Overview & Audio", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                        Text(cardData.objectNameTarget, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberGold.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AmberGold)
                ) {
                    Text(
                        text = "${(cardData.confidence * 100).toInt()}% ${cardData.detectionEngine}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("English Translation: ${cardData.objectNameEn}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Phonetic IPA: ${cardData.pronunciationIpa}", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Level: ${cardData.difficulty} • Category: ${cardData.category}", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSpeakText(cardData.objectNameTarget) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PRONOUNCE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            IconButton(
                onClick = {
                    onToggleFavorite()
                    Toast.makeText(context, if (!isFavorite) "Added to Favorites ❤️" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isFavorite) Color.Red.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red else Color.White
                )
            }

            IconButton(
                onClick = {
                    onSaveToVocabulary()
                    Toast.makeText(context, "Saved to Vocabulary Database 📚", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isSaved) EmeraldGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isSaved) EmeraldLight else Color.White
                )
            }
        }
    }
}

// --- CARD 2: MEANING & DETAILS ---
@Composable
private fun Card2MeaningPage(cardData: ArCulturalLearningCardData) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Card 2 • Meaning & Details", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
        Text("Definition & Context", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                Text("Meaning:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AmberGold)
            }
            Text(text = cardData.meaning, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                Text("Usage Scenario:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldLight)
            }
            Text(text = cardData.usageContext, color = Color.LightGray, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndigoPurple.copy(alpha = 0.2f))
                    .border(1.dp, IndigoLight, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text("DIFFICULTY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndigoLight)
                    Text(cardData.difficulty, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldGreen.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldLight, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text("CATEGORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                    Text(cardData.category, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

// --- CARD 3: EXAMPLE SENTENCE & GRAMMAR ---
@Composable
private fun Card3SentenceGrammarPage(
    cardData: ArCulturalLearningCardData,
    onSpeakText: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Card 3 • Sentence & Grammar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)

        // Target Sentence Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IndigoPurple.copy(alpha = 0.25f))
                .border(1.dp, IndigoLight, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💬 Target Sentence:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoLight)
                IconButton(
                    onClick = { onSpeakText(cardData.exampleSentenceTarget) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Text(cardData.exampleSentenceTarget, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(cardData.exampleSentenceEn, fontSize = 11.sp, color = Color.LightGray)
        }

        // Grammar Note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("📌 Grammar Highlight:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                Text(cardData.grammarHighlight, fontSize = 12.sp, color = Color.White)
            }
        }

        // Word Breakdown Chips
        if (cardData.wordBreakdown.isNotEmpty()) {
            Text("Word Breakdown:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cardData.wordBreakdown) { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(item.part, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldLight)
                            Text("${item.role}: ${item.partMeaning}", fontSize = 9.sp, color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

// --- CARD 4: RELATED VOCABULARY ---
@Composable
private fun Card4RelatedVocabularyPage(
    cardData: ArCulturalLearningCardData,
    onSelectRelatedWord: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Card 4 • Related Vocabulary", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Text("Tap word to view card", fontSize = 10.sp, color = Color.Gray)
        }

        Text("Vocabulary Expansion", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            cardData.relatedVocabulary.take(5).forEach { related ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRelatedWord(related.wordEn) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(related.wordTarget, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = EmeraldLight)
                                Text(related.pronunciationIpa, fontSize = 10.sp, color = AmberGold)
                            }
                            Text("English: ${related.wordEn}", fontSize = 10.sp, color = Color.LightGray)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Explore", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- CARD 5: CULTURAL LEARNING ---
@Composable
private fun Card5CulturalLearningPage(
    cardData: ArCulturalLearningCardData,
    onSpeakText: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Card 5 • Cultural Insights", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
        }

        Text("Cultural Context", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            IndigoPurple.copy(alpha = 0.3f),
                            SlateSurfaceDark
                        )
                    )
                )
                .border(1.5.dp, AmberGold, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (cardData.culturalContextRegion.isNotBlank()) cardData.culturalContextRegion else "Cultural Fact",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = AmberGold
                        )
                    }

                    Text(
                        text = cardData.culturalFact,
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }

                Button(
                    onClick = { onSpeakText(cardData.culturalFact) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = CharcoalTextDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LISTEN TO CULTURAL FACT", fontWeight = FontWeight.Black, color = CharcoalTextDark, fontSize = 11.sp)
                }
            }
        }
    }
}

// --- CARD 6: MINI QUIZ ---
@Composable
private fun Card6MiniQuizPage(
    cardData: ArCulturalLearningCardData,
    onCompleteQuiz: (isCorrect: Boolean, xpEarned: Int) -> Unit
) {
    val context = LocalContext.current
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Card 6 • Adaptive Mini Quiz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Text("+15 XP", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = AmberGold)
        }

        Text(cardData.miniQuizQuestion, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            cardData.miniQuizOptions.forEachIndexed { idx, option ->
                val isSelected = selectedOptionIndex == idx
                val isCorrect = idx == cardData.miniQuizCorrectIndex

                val cardBg = when {
                    isSubmitted && isCorrect -> EmeraldGreen.copy(alpha = 0.3f)
                    isSubmitted && isSelected && !isCorrect -> Color.Red.copy(alpha = 0.3f)
                    isSelected -> IndigoPurple.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.08f)
                }

                val borderCol = when {
                    isSubmitted && isCorrect -> EmeraldLight
                    isSubmitted && isSelected && !isCorrect -> Color.Red
                    isSelected -> IndigoLight
                    else -> Color.White.copy(alpha = 0.15f)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitted) {
                            selectedOptionIndex = idx
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, borderCol)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        if (isSubmitted) {
                            if (isCorrect) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Correct", tint = EmeraldLight, modifier = Modifier.size(16.dp))
                            } else if (isSelected) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Incorrect", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        if (isSubmitted) {
            val correct = selectedOptionIndex == cardData.miniQuizCorrectIndex
            Text(
                text = if (correct) "✓ Correct! +15 XP Awarded. ${cardData.miniQuizExplanation}" else "✗ Incorrect. ${cardData.miniQuizExplanation}",
                color = if (correct) EmeraldLight else AmberGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Button(
                onClick = {
                    if (selectedOptionIndex != null) {
                        isSubmitted = true
                        val correct = selectedOptionIndex == cardData.miniQuizCorrectIndex
                        onCompleteQuiz(correct, if (correct) 15 else 5)
                    } else {
                        Toast.makeText(context, "Select an answer first!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SUBMIT QUIZ ANSWER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

// --- CARD 7: LEARNING SUMMARY ---
@Composable
private fun Card7LearningSummaryPage(
    cardData: ArCulturalLearningCardData,
    onSaveToVocabulary: (ArCulturalLearningCardData) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Card 7 • Mastery Summary", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Text("Learning Complete! 🎉", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, EmeraldLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("+${cardData.xpReward} XP", fontSize = 18.sp, fontWeight = FontWeight.Black, color = EmeraldLight)
                        Text("XP Earned", fontSize = 10.sp, color = Color.White)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AmberGold.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, AmberGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${cardData.masteryProgressPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AmberGold)
                        Text("Mastery Level", fontSize = 10.sp, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Summary Checklist:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldLight)
                    Text("✓ Scanned object '${cardData.objectNameEn}'", fontSize = 11.sp, color = Color.White)
                    Text("✓ Learned pronunciation: ${cardData.pronunciationIpa}", fontSize = 11.sp, color = Color.White)
                    Text("✓ Unlocked cultural insight & grammar rules", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        Button(
            onClick = {
                onSaveToVocabulary(cardData.copy(isSaved = true))
                Toast.makeText(context, "Progress Saved to Room Database! 💾", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_progress_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("SAVE PROGRESS & CLOSE", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}

private fun getIconForCategory(category: String): ImageVector {
    return when {
        category.contains("Tech", ignoreCase = true) || category.contains("Electr", ignoreCase = true) -> Icons.Default.Laptop
        category.contains("Furnit", ignoreCase = true) || category.contains("Chair", ignoreCase = true) -> Icons.Default.Chair
        category.contains("Kitch", ignoreCase = true) || category.contains("Food", ignoreCase = true) || category.contains("Drink", ignoreCase = true) -> Icons.Default.LocalCafe
        category.contains("Work", ignoreCase = true) || category.contains("Office", ignoreCase = true) -> Icons.Default.Work
        category.contains("Home", ignoreCase = true) -> Icons.Default.Home
        else -> Icons.Default.Category
    }
}
