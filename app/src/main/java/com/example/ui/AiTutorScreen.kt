package com.example.ui

import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiTutorScreen(viewModel: MainViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isTutorTyping by viewModel.isTutorTyping.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsPlaybackState.collectAsStateWithLifecycle()
    val currentlySpeakingText by viewModel.currentlySpeakingText.collectAsStateWithLifecycle()
    val cefrLevel by viewModel.userCefrLevel.collectAsStateWithLifecycle()
    val toneStyle by viewModel.tutorToneStyle.collectAsStateWithLifecycle()
    val grammarMistakes by viewModel.allGrammarMistakes.collectAsStateWithLifecycle()
    val inspectedWord by viewModel.inspectedWordResult.collectAsStateWithLifecycle()
    val isInspectingWord by viewModel.isInspectingWord.collectAsStateWithLifecycle()
    val miniExercises by viewModel.miniExercises.collectAsStateWithLifecycle()
    val isLoadingExercises by viewModel.isLoadingMiniExercises.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Chat, 1: Mistakes Bank, 2: Practice Quiz
    var showSettingsSheet by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val supportedLanguages = listOf(
        "English", "Spanish", "French", "German", "Italian", "Portuguese",
        "Japanese", "Korean", "Chinese", "Hindi", "Tamil", "Arabic"
    )

    val cefrLevels = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, isTutorTyping) {
        if (chatMessages.isNotEmpty() && activeTab == 0) {
            scrollState.animateScrollToItem(chatMessages.size + (if (isTutorTyping) 1 else 0) - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .testTag("ai_tutor_screen")
    ) {
        // --- 1. Top HUD & Mode Header ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(IndigoPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Tutor AI Logo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Multilingual AI Tutor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CharcoalTextDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(IndigoPurple.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cefrLevel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = IndigoPurple
                                    )
                                }
                            }
                            Text(
                                text = "Learning $targetLang ($toneStyle Tone)",
                                fontSize = 11.sp,
                                color = EmeraldDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(IndigoPurple.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tutor Settings",
                                tint = IndigoPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Red.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Reset Session",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs (Chat, Grammar Mistakes Bank, Mini Practice)
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = IndigoPurple,
                    divider = {}
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tutor Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mistakes (${grammarMistakes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = {
                            activeTab = 2
                            viewModel.loadMiniExercises()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exercises", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

        // --- Voice Conversation Toggle Bar ---
        val voiceModeActive by viewModel.isVoiceConversationModeActive.collectAsStateWithLifecycle()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (voiceModeActive) EmeraldGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (voiceModeActive) Icons.Default.Hearing else Icons.Default.VolumeUp,
                        contentDescription = "Voice Mode",
                        tint = if (voiceModeActive) EmeraldGreen else IndigoPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (voiceModeActive) "Auto Spoken Responses Active" else "Voice Conversation Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalTextDark
                    )
                }

                Switch(
                    checked = voiceModeActive,
                    onCheckedChange = { viewModel.toggleVoiceConversationMode() },
                    modifier = Modifier
                        .scale(0.8f)
                        .testTag("voice_conversation_toggle")
                )
            }
        }

        // --- 2. Main Tab Body Content ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                0 -> TutorChatTab(
                    viewModel = viewModel,
                    chatMessages = chatMessages,
                    isTutorTyping = isTutorTyping,
                    scrollState = scrollState,
                    targetLang = targetLang,
                    ttsState = ttsState,
                    currentlySpeakingText = currentlySpeakingText
                )
                1 -> MistakesBankTab(
                    mistakes = grammarMistakes,
                    viewModel = viewModel,
                    onPracticeClick = { activeTab = 2; viewModel.loadMiniExercises() }
                )
                2 -> MiniExercisesTab(
                    exercises = miniExercises,
                    isLoading = isLoadingExercises,
                    viewModel = viewModel
                )
            }
        }

        // --- 3. Word Inspection Dialog ---
        if (isInspectingWord) {
            Dialog(onDismissRequest = { viewModel.closeWordInspector() }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = IndigoPurple)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Inspecting vocabulary details...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (inspectedWord != null) {
            WordInspectionDialog(
                result = inspectedWord!!,
                onDismiss = { viewModel.closeWordInspector() },
                onSpeak = { text -> viewModel.speakText(text, targetLang) }
            )
        }

        // --- 4. Tutor Configuration Sheet ---
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "AI Tutor Customization",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = CharcoalTextDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Target Language to Practice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(supportedLanguages) { lang ->
                            FilterChip(
                                selected = targetLang == lang,
                                onClick = { viewModel.setTargetLanguage(lang) },
                                label = { Text(lang) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("CEFR Learner Proficiency Level", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cefrLevels.forEach { lvl ->
                            FilterChip(
                                selected = cefrLevel == lvl,
                                onClick = { viewModel.setUserCefrLevel(lvl) },
                                label = { Text(lvl) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tutor Persona Tone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Casual", "Formal").forEach { tone ->
                            FilterChip(
                                selected = toneStyle == tone,
                                onClick = { viewModel.setTutorToneStyle(tone) },
                                label = { Text(tone) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple)
                    ) {
                        Text("Apply Settings")
                    }
                }
            }
        }

        // --- 5. Bottom Message Input Bar (Shown only in Chat Tab) ---
        if (activeTab == 0) {
            BottomMessageInputBar(
                textInput = textInput,
                onTextInputChange = { textInput = it },
                onSend = {
                    if (textInput.trim().isNotEmpty()) {
                        viewModel.sendTutorMessage(textInput)
                        textInput = ""
                    }
                },
                viewModel = viewModel,
                audioPermissionState = audioPermissionState,
                targetLang = targetLang
            )
        }
    }
}

@Composable
private fun TutorChatTab(
    viewModel: MainViewModel,
    chatMessages: List<TutorMessage>,
    isTutorTyping: Boolean,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    targetLang: String,
    ttsState: TtsPlaybackState,
    currentlySpeakingText: String
) {
    val suggestionChips = listOf(
        "I goes to school yesterday",
        "Yo va a la escuela ayer",
        "Je va à l'école hier",
        "How do you say 'Thank you' in $targetLang?"
    )

    if (chatMessages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(IndigoPurple.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = IndigoPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Start Speaking in $targetLang",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = CharcoalTextDark
                )
                Text(
                    text = "Type or speak any sentence. Your AI Tutor will detect language, correct grammar errors, explain mistakes, and provide native phrasing!",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MutedTextLight
                )

                Text("Try one of these examples:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestionChips.forEach { prompt ->
                        OutlinedCard(
                            onClick = { viewModel.sendTutorMessage(prompt) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(prompt, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { message ->
                if (message.role == "user") {
                    UserBubble(message = message)
                } else {
                    StructuredTutorBubble(
                        message = message,
                        viewModel = viewModel,
                        targetLang = targetLang,
                        isSpeaking = ttsState == TtsPlaybackState.SPEAKING && currentlySpeakingText == message.content
                    )
                }
            }

            if (isTutorTyping) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = IndigoPurple,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tutor is analyzing grammar and context...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPurple
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(message: TutorMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldGreen),
            modifier = Modifier.widthIn(max = 280.dp).shadow(1.dp, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun StructuredTutorBubble(
    message: TutorMessage,
    viewModel: MainViewModel,
    targetLang: String,
    isSpeaking: Boolean
) {
    val adapter = remember { GeminiRetrofitClient.moshiInstance.adapter(TutorAnalysisResult::class.java) }
    val analysis = remember(message.analysisJson) {
        message.analysisJson?.let {
            try {
                adapter.fromJson(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(IndigoPurple.copy(alpha = 0.15f))
                .align(Alignment.Top),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = "Tutor Bot", tint = IndigoPurple, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (analysis != null) {
                    // Header Badges (Detected Lang + XP)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IndigoPurple.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Input: ${analysis.detectedInputLanguage}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPurple
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            if (analysis.confidenceScore > 0f) {
                                Text(
                                    text = "${(analysis.confidenceScore * 100).toInt()}% match",
                                    fontSize = 10.sp,
                                    color = MutedTextLight
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AmberGold.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${analysis.xpAwarded} XP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Conversational Response
                    if (analysis.conversationalReply.isNotEmpty()) {
                        Text(
                            text = analysis.conversationalReply,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CharcoalTextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Correction Box
                    if (analysis.hasGrammarErrors && analysis.correctedSentence.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Corrected Sentence:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldDark)
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Tap to inspect words in corrected sentence
                                InteractiveWordSentence(
                                    sentence = analysis.correctedSentence,
                                    onWordClick = { word -> viewModel.inspectWord(word) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Grammar Mistakes Breakdown
                    if (analysis.grammarMistakes.isNotEmpty()) {
                        Text("Grammar Mistakes & Explanations:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        analysis.grammarMistakes.forEach { error ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = error.originalSegment,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                textDecoration = TextDecoration.LineThrough,
                                                color = Color.Red,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(" ➔ ", fontSize = 12.sp, color = Color.Gray)
                                        Text(
                                            text = error.correctedSegment,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = EmeraldDark,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Red.copy(alpha = 0.1f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(error.mistakeType, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(error.explanation, fontSize = 11.sp, color = CharcoalTextDark)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Phrasing Variants (Formal / Informal / Native)
                    if (analysis.naturalNativeVersion.isNotEmpty() || analysis.formalVersion.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (analysis.naturalNativeVersion.isNotEmpty()) {
                                ChipBadge("Native: ${analysis.naturalNativeVersion}", IndigoPurple)
                            }
                            if (analysis.formalVersion.isNotEmpty()) {
                                ChipBadge("Formal: ${analysis.formalVersion}", CharcoalTextDark)
                            }
                            if (analysis.informalVersion.isNotEmpty()) {
                                ChipBadge("Informal: ${analysis.informalVersion}", EmeraldDark)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Pronunciation IPA
                    if (analysis.pronunciationTipsIpa.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(analysis.pronunciationTipsIpa, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = MutedTextDark)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Follow-Up Question
                    if (analysis.followUpQuestion.isNotEmpty()) {
                        Text(
                            text = "💡 ${analysis.followUpQuestion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPurple
                        )
                    }

                } else {
                    // Fallback Plain Text Display
                    Text(
                        text = message.content,
                        color = CharcoalTextDark,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }

                // Audio TTS Player Button
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tap word to inspect details", fontSize = 10.sp, color = MutedTextLight)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (isSpeaking) {
                                viewModel.stopSpeaking()
                            } else {
                                val speakable = analysis?.correctedSentence?.ifEmpty { message.content } ?: message.content
                                viewModel.speakText(speakable, targetLang)
                            }
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (isSpeaking) Color.Red.copy(alpha = 0.1f) else IndigoPurple.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Speak Sentence",
                            tint = if (isSpeaking) Color.Red else IndigoPurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveWordSentence(
    sentence: String,
    onWordClick: (String) -> Unit
) {
    val words = sentence.split(" ")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        words.forEach { rawWord ->
            val clean = rawWord.trim().trim { !it.isLetterOrDigit() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .clickable { onWordClick(clean) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = rawWord,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldDark
                )
            }
        }
    }
}

@Composable
private fun ChipBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MistakesBankTab(
    mistakes: List<GrammarMistakeEntity>,
    viewModel: MainViewModel,
    onPracticeClick: () -> Unit
) {
    if (mistakes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(48.dp))
                Text("No Grammar Mistakes Found!", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = CharcoalTextDark)
                Text(
                    text = "As you chat with the AI Tutor, any grammar or syntax mistakes will be tracked here for personalized review.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MutedTextLight
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personal Grammar Mistake Bank",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = CharcoalTextDark
                )

                Button(
                    onClick = onPracticeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Practice Quiz", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mistakes) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${item.timesRepeated}x Repeated",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Red
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.mistakeCategory,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPurple
                                )
                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(
                                    onClick = {
                                        viewModel.markMistakeMastered(item.id)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Mastered", tint = EmeraldGreen)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.originalText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = TextDecoration.LineThrough,
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(" ➔ ", fontSize = 14.sp)
                                Text(
                                    text = item.correctedText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }

                            if (item.explanation.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.explanation, fontSize = 11.sp, color = CharcoalTextDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniExercisesTab(
    exercises: List<MiniExerciseItem>,
    isLoading: Boolean,
    viewModel: MainViewModel
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = IndigoPurple)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Generating custom exercises from your mistake history...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else if (exercises.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Quiz, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(48.dp))
                Text("Ready for Practice Exercises", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Button(
                    onClick = { viewModel.loadMiniExercises() },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple)
                ) {
                    Text("Generate Practice Exercises")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Custom Targeted Exercises",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = CharcoalTextDark
                )
            }

            items(exercises) { exercise ->
                ExerciseCard(exercise = exercise, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: MiniExerciseItem, viewModel: MainViewModel) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(IndigoPurple.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(exercise.targetedMistakeType, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(exercise.questionPrompt, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = exercise.sentenceWithBlank,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CharcoalTextDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exercise.options.forEach { option ->
                    val isSelected = selectedOption == option
                    val isCorrect = option == exercise.correctAnswer
                    val containerColor = when {
                        !isAnswerSubmitted -> if (isSelected) IndigoPurple.copy(alpha = 0.15f) else SlateBackgroundLight
                        isCorrect -> EmeraldGreen.copy(alpha = 0.2f)
                        isSelected && !isCorrect -> Color.Red.copy(alpha = 0.15f)
                        else -> SlateBackgroundLight
                    }

                    OutlinedCard(
                        onClick = {
                            if (!isAnswerSubmitted) {
                                selectedOption = option
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isAnswerSubmitted) {
                                if (isCorrect) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = EmeraldGreen)
                                } else if (isSelected) {
                                    Icon(Icons.Default.Cancel, contentDescription = "Wrong", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isAnswerSubmitted) {
                Button(
                    onClick = {
                        if (selectedOption != null) {
                            isAnswerSubmitted = true
                            if (selectedOption == exercise.correctAnswer) {
                                viewModel.addXp(20, "EXERCISE")
                            }
                        }
                    },
                    enabled = selectedOption != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple)
                ) {
                    Text("Submit Answer")
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedOption == exercise.correctAnswer) EmeraldGreen.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (selectedOption == exercise.correctAnswer) "🎉 Correct! +20 XP" else "❌ Incorrect",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (selectedOption == exercise.correctAnswer) EmeraldDark else Color.Red
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(exercise.explanation, fontSize = 11.sp, color = CharcoalTextDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordInspectionDialog(
    result: WordInspectionResult,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(result.word, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = IndigoPurple)
                        if (result.pronunciationIpa.isNotEmpty()) {
                            Text(result.pronunciationIpa, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MutedTextDark)
                        }
                    }

                    IconButton(onClick = { onSpeak(result.word) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Pronounce", tint = IndigoPurple)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ChipBadge(result.partOfSpeech, CharcoalTextDark)

                Spacer(modifier = Modifier.height(12.dp))
                Text("Meaning & Definition:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MutedTextLight)
                Text(result.meaning, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CharcoalTextDark)

                if (result.synonyms.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Synonyms:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MutedTextLight)
                    Text(result.synonyms.joinToString(", "), fontSize = 13.sp, color = CharcoalTextDark)
                }

                if (result.exampleSentences.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Example Sentences:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MutedTextLight)
                    result.exampleSentences.forEach { sentence ->
                        Text("• $sentence", fontSize = 12.sp, color = CharcoalTextDark, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun BottomMessageInputBar(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    onSend: () -> Unit,
    viewModel: MainViewModel,
    audioPermissionState: com.google.accompanist.permissions.PermissionState,
    targetLang: String
) {
    val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = textInput,
                onValueChange = onTextInputChange,
                placeholder = { Text("Speak or type in any language...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("tutor_input_field"),
                colors = AppTextFieldDefaults.colors(
                    focusedContainerColor = SlateBackgroundLight,
                    unfocusedContainerColor = SlateBackgroundLight,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 3,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (isListening) {
                                viewModel.stopVoiceRecognition()
                            } else if (!audioPermissionState.status.isGranted) {
                                audioPermissionState.launchPermissionRequest()
                            } else {
                                viewModel.startVoiceRecognition(
                                    contextType = "TUTOR",
                                    languageName = targetLang,
                                    onTextUpdate = { txt -> if (txt != "Listening...") onTextInputChange(txt) },
                                    onComplete = { finalSpeech ->
                                        val cleaned = finalSpeech.replace("Listening...", "").trim()
                                        if (cleaned.isNotEmpty()) {
                                            onTextInputChange(cleaned)
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("ai_tutor_microphone_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) Color.Red else IndigoPurple
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(46.dp)
                    .background(EmeraldGreen, CircleShape)
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
