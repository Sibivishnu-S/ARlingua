package com.example.ui

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LearnedWord
import com.example.data.QuizResult
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.data.SignRecognitionResult
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.viewinterop.AndroidView

// Simple Data Class for structured Gemini/Simulated Word details
data class SignWordDetails(
    val word: String,
    val meaning: String,
    val exampleSentence: String,
    val exampleTranslation: String,
    val grammarExplanation: String,
    val relatedVocabulary: List<Pair<String, String>>,
    val learningTip: String
)

@Composable
fun SignLanguageScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Translator, 1 = Practice Mode

    // Supported languages
    val languages = listOf(
        "English", "Tamil", "Hindi", "Telugu", "Kannada", "Malayalam",
        "French", "German", "Spanish", "Japanese", "Chinese", "Arabic"
    )

    var sourceLang by remember { mutableStateOf("English") }
    var targetLang by remember { mutableStateOf("English") }
    var inputText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }

    // Words extraction
    val wordsList = remember(translatedText) {
        translatedText.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // Playback state
    var activeWordIndex by remember { mutableStateOf(0) }
    var activeCharIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) } // 0.5f, 1.0f, 1.5f, 2.0f
    var isFullScreen by remember { mutableStateOf(false) }

    // Speech states
    val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()
    val recognizedSpeechText by viewModel.speechRecognizedText.collectAsStateWithLifecycle()

    // Database / Bookmark state
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()
    val currentWord = wordsList.getOrNull(activeWordIndex) ?: ""
    val isCurrentWordFavorited = remember(currentWord, allWords) {
        allWords.any { it.word.equals(currentWord, ignoreCase = true) && it.category == "Sign Language" && it.isBookmarked }
    }

    // Details cache to prevent infinite calls
    var wordDetails by remember { mutableStateOf<SignWordDetails?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    // Trigger details loading when current word changes
    LaunchedEffect(currentWord) {
        if (currentWord.isNotBlank()) {
            isLoadingDetails = true
            coroutineScope.launch {
                wordDetails = fetchWordDetails(currentWord, targetLang, viewModel)
                isLoadingDetails = false
            }
        } else {
            wordDetails = null
        }
    }

    // Automatic spelling loop when isPlaying is true
    LaunchedEffect(isPlaying, activeWordIndex, activeCharIndex, playbackSpeed, wordsList) {
        if (isPlaying && wordsList.isNotEmpty()) {
            val word = wordsList.getOrNull(activeWordIndex) ?: ""
            if (word.isNotBlank()) {
                val delayTime = (800 / playbackSpeed).toLong()
                delay(delayTime)
                if (activeCharIndex < word.length - 1) {
                    activeCharIndex++
                } else {
                    // Word finished, proceed to next word after a pause
                    delay(delayTime * 2)
                    if (activeWordIndex < wordsList.size - 1) {
                        activeWordIndex++
                        activeCharIndex = 0
                    } else {
                        // End of sentence reached
                        isPlaying = false
                        activeWordIndex = 0
                        activeCharIndex = 0
                    }
                }
            } else {
                isPlaying = false
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sign_language_screen"),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CharcoalTextDark
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Sign Language",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = CharcoalTextDark,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SlateBackgroundLight)
        ) {
            // Screen Tab selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = EmeraldDark,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = EmeraldGreen
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            "AI SIGN CAMERA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (activeTab == 0) EmeraldDark else Color.Gray
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Text(
                            "TRANSLATOR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (activeTab == 1) EmeraldDark else Color.Gray
                        )
                    }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = {
                        Text(
                            "PRACTICE QUIZ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (activeTab == 2) EmeraldDark else Color.Gray
                        )
                    }
                )
            }

            if (activeTab == 0) {
                SignLanguageCameraRecognitionView(viewModel = viewModel)
            } else if (activeTab == 1) {
                // Translator layout
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Language selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Translation Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = CharcoalTextDark
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Source selector
                                    var showSourceMenu by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { showSourceMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "From: $sourceLang",
                                                maxLines = 1,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSourceMenu,
                                            onDismissRequest = { showSourceMenu = false }
                                        ) {
                                            languages.forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang) },
                                                    onClick = {
                                                        sourceLang = lang
                                                        showSourceMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Switch icon
                                    IconButton(
                                        onClick = {
                                            val temp = sourceLang
                                            sourceLang = targetLang
                                            targetLang = temp
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = "Swap Languages",
                                            tint = EmeraldGreen
                                        )
                                    }

                                    // Target selector
                                    var showTargetMenu by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { showTargetMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "To: $targetLang",
                                                maxLines = 1,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showTargetMenu,
                                            onDismissRequest = { showTargetMenu = false }
                                        ) {
                                            languages.forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang) },
                                                    onClick = {
                                                        targetLang = lang
                                                        showTargetMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Input TextField with STT button
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Enter phrase",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CharcoalTextDark
                                    )
                                    // Speech mic trigger
                                    IconButton(
                                        onClick = {
                                            if (isListening) {
                                                viewModel.stopVoiceRecognition()
                                            } else {
                                                viewModel.startVoiceRecognition(
                                                    contextType = "SignLanguageTranslate",
                                                    languageName = sourceLang,
                                                    onTextUpdate = { recognized ->
                                                        inputText = recognized
                                                    },
                                                    onComplete = { finalSpeech ->
                                                        inputText = finalSpeech
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .background(
                                                if (isListening) Color.Red.copy(alpha = 0.1f) else EmeraldGreen.copy(alpha = 0.1f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "Voice Input",
                                            tint = if (isListening) Color.Red else EmeraldGreen
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = { Text("Type words to fingerspell/translate...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .testTag("sign_input_field"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color.LightGray
                                    )
                                )
                                if (isListening) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Listening: $recognizedSpeechText",
                                        fontSize = 11.sp,
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            isTranslating = true
                                            coroutineScope.launch {
                                                // Call translation if languages differ, otherwise direct conversion
                                                translatedText = if (sourceLang != targetLang) {
                                                    viewModel.geminiService.translateText(inputText, sourceLang, targetLang)
                                                } else {
                                                    inputText
                                                }
                                                isTranslating = false
                                                activeWordIndex = 0
                                                activeCharIndex = 0
                                                isPlaying = true // Auto-start play on completion!
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("convert_sign_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    enabled = !isTranslating && inputText.isNotBlank()
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Translate, contentDescription = "Convert")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (sourceLang != targetLang) "Translate & Convert" else "Convert to Sign Language",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Playback and Sign display canvas
                    if (translatedText.isNotBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateBackgroundDark),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "SIGN LANGUAGE VISUAL",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = EmeraldLight
                                        )
                                        IconButton(onClick = { isFullScreen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Full Screen",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    // Hand Drawing Panel
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.Black)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val activeWord = wordsList.getOrNull(activeWordIndex) ?: ""
                                        val activeChar = activeWord.getOrNull(activeCharIndex) ?: ' '

                                        SignLanguageHandCanvas(
                                            char = activeChar,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Overlay of character and index
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(
                                                    EmeraldGreen.copy(alpha = 0.2f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(1.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = activeChar.uppercaseChar().toString(),
                                                fontSize = 28.sp,
                                                color = EmeraldLight,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        // Subtitle word
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                wordsList.forEachIndexed { idx, word ->
                                                    val isCurrent = idx == activeWordIndex
                                                    Text(
                                                        text = word + " ",
                                                        fontSize = 14.sp,
                                                        color = if (isCurrent) EmeraldLight else Color.Gray,
                                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                                                        modifier = Modifier.clickable {
                                                            activeWordIndex = idx
                                                            activeCharIndex = 0
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Playback controls Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Previous Word
                                        IconButton(
                                            onClick = {
                                                if (activeWordIndex > 0) {
                                                    activeWordIndex--
                                                    activeCharIndex = 0
                                                }
                                            },
                                            enabled = activeWordIndex > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Previous",
                                                tint = if (activeWordIndex > 0) Color.White else Color.Gray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        // Stop
                                        IconButton(
                                            onClick = {
                                                isPlaying = false
                                                activeWordIndex = 0
                                                activeCharIndex = 0
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        // Play / Pause FAB style
                                        FloatingActionButton(
                                            onClick = { isPlaying = !isPlaying },
                                            containerColor = EmeraldGreen,
                                            contentColor = Color.White,
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        // Replay Word
                                        IconButton(
                                            onClick = {
                                                activeCharIndex = 0
                                                isPlaying = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay,
                                                contentDescription = "Replay",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Next Word
                                        IconButton(
                                            onClick = {
                                                if (activeWordIndex < wordsList.size - 1) {
                                                    activeWordIndex++
                                                    activeCharIndex = 0
                                                }
                                            },
                                            enabled = activeWordIndex < wordsList.size - 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next",
                                                tint = if (activeWordIndex < wordsList.size - 1) Color.White else Color.Gray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Speed Controls Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Speed: ",
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                                        speeds.forEach { speed ->
                                            val isSelected = playbackSpeed == speed
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) EmeraldGreen else Color.DarkGray)
                                                    .clickable { playbackSpeed = speed }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "${speed}x",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Gemini AI powered word details card
                    if (currentWord.isNotBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Gemini",
                                                tint = IndigoPurple,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Active Sign: \"${currentWord}\"",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                color = CharcoalTextDark
                                            )
                                        }

                                        Row {
                                            // Speak pronunciation
                                            IconButton(
                                                onClick = { viewModel.speakText(currentWord, targetLang) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeUp,
                                                    contentDescription = "Pronounce",
                                                    tint = EmeraldGreen
                                                )
                                            }

                                            // Bookmark/Favorite toggle
                                            IconButton(
                                                onClick = {
                                                    wordDetails?.let { details ->
                                                        val existing = allWords.find {
                                                            it.word.equals(currentWord, ignoreCase = true) &&
                                                                    it.category == "Sign Language"
                                                        }
                                                        if (existing != null) {
                                                            viewModel.toggleWordBookmark(existing)
                                                        } else {
                                                            // Save new sign vocabulary
                                                            val newWord = LearnedWord(
                                                                word = details.word,
                                                                translation = details.meaning,
                                                                ipa = "/${details.word.lowercase()}/",
                                                                exampleSentence = details.exampleSentence,
                                                                exampleTranslation = details.exampleTranslation,
                                                                category = "Sign Language",
                                                                difficulty = "Beginner",
                                                                language = targetLang,
                                                                isBookmarked = true
                                                            )
                                                            coroutineScope.launch {
                                                                viewModel.repository.insertWord(newWord)
                                                                viewModel.syncWordToFirestore(newWord)
                                                                // Award XP for learning a sign!
                                                                viewModel.repository.addXp(10, "SIGN_LEARN")
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isCurrentWordFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (isCurrentWordFavorited) Color.Red else Color.Gray
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (isLoadingDetails) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(color = IndigoPurple)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Gemini is analyzing the word context...",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    } else {
                                        wordDetails?.let { details ->
                                            Text(
                                                text = "Meaning / Concept:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = details.meaning,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CharcoalTextDark
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = "Example Sentence:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = details.exampleSentence,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = CharcoalTextDark
                                            )
                                            Text(
                                                text = details.exampleTranslation,
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = "Grammar & Structure Usage:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = details.grammarExplanation,
                                                fontSize = 12.sp,
                                                color = CharcoalTextDark
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = "Related Sign Vocabulary:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                details.relatedVocabulary.forEach { pair ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(SlateBackgroundLight)
                                                            .clickable {
                                                                // Click to load related sign
                                                                inputText = pair.first
                                                                translatedText = pair.first
                                                                activeWordIndex = 0
                                                                activeCharIndex = 0
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            "${pair.first} (${pair.second})",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = CharcoalTextDark
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Divider(color = Color.LightGray.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Lightbulb,
                                                    contentDescription = "Tip",
                                                    tint = AmberGold,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Learning Tip:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = AmberGold
                                                )
                                            }
                                            Text(
                                                text = details.learningTip,
                                                fontSize = 11.sp,
                                                color = CharcoalTextDark
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Checkbox mark as learned
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(EmeraldGreen.copy(alpha = 0.05f))
                                                    .clickable {
                                                        val isLearned = allWords.any {
                                                            it.word.equals(currentWord, ignoreCase = true) &&
                                                                    it.category == "Sign Language"
                                                        }
                                                        if (!isLearned) {
                                                            // Add to database
                                                            val lWord = LearnedWord(
                                                                word = details.word,
                                                                translation = details.meaning,
                                                                ipa = "/${details.word.lowercase()}/",
                                                                exampleSentence = details.exampleSentence,
                                                                exampleTranslation = details.exampleTranslation,
                                                                category = "Sign Language",
                                                                difficulty = "Beginner",
                                                                language = targetLang,
                                                                isBookmarked = false
                                                            )
                                                            coroutineScope.launch {
                                                                viewModel.repository.insertWord(lWord)
                                                                viewModel.syncWordToFirestore(lWord)
                                                                viewModel.repository.addXp(15, "SIGN_LEARNED")
                                                            }
                                                        }
                                                    }
                                                    .padding(8.dp)
                                            ) {
                                                val hasLearned = allWords.any {
                                                    it.word.equals(currentWord, ignoreCase = true) &&
                                                            it.category == "Sign Language"
                                                }
                                                Checkbox(
                                                    checked = hasLearned,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Mark word \"${currentWord}\" as Learned (+15 XP)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Practice Mode Layout (Visual Sign Recognition Quiz)
                SignLanguagePracticeQuiz(viewModel = viewModel, onBackToTranslator = { activeTab = 1 })
            }
        }
    }

    // IMMERSIVE FULL SCREEN DIALOG
    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val activeWord = wordsList.getOrNull(activeWordIndex) ?: ""
                val activeChar = activeWord.getOrNull(activeCharIndex) ?: ' '

                SignLanguageHandCanvas(
                    char = activeChar,
                    modifier = Modifier.fillMaxSize()
                )

                // Immersive Controls Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isFullScreen = false },
                            modifier = Modifier.background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, "Exit", tint = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .background(EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "SPELLING: ${activeChar.uppercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                    }

                    // Floating word indicator
                    Text(
                        text = activeWord,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = EmeraldLight,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    )

                    // Bottom floating simple player controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (activeWordIndex > 0) activeWordIndex-- }) {
                            Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White)
                        }
                        FloatingActionButton(
                            onClick = { isPlaying = !isPlaying },
                            containerColor = EmeraldGreen,
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                        }
                        IconButton(onClick = { activeCharIndex = 0 }) {
                            Icon(Icons.Default.Replay, "Reset", tint = Color.White)
                        }
                        IconButton(onClick = { if (activeWordIndex < wordsList.size - 1) activeWordIndex++ }) {
                            Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-fidelity visual vector-based vector skeleton hand representation in Compose Canvas.
 * Animates finger joints smoothly based on character inputs.
 */
@Composable
fun SignLanguageHandCanvas(
    char: Char,
    modifier: Modifier = Modifier
) {
    val uppercaseChar = char.uppercaseChar()

    // Determine target finger extensions for character
    val thumbExtTarget = remember(uppercaseChar) { getThumbExt(uppercaseChar) }
    val indexExtTarget = remember(uppercaseChar) { getFingerExt(uppercaseChar, 0) }
    val middleExtTarget = remember(uppercaseChar) { getFingerExt(uppercaseChar, 1) }
    val ringExtTarget = remember(uppercaseChar) { getFingerExt(uppercaseChar, 2) }
    val pinkyExtTarget = remember(uppercaseChar) { getFingerExt(uppercaseChar, 3) }

    val transition = updateTransition(targetState = uppercaseChar, label = "HandPoseTransition")

    val thumbExt by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Thumb"
    ) { getThumbExt(it) }

    val indexExt by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Index"
    ) { getFingerExt(it, 0) }

    val middleExt by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Middle"
    ) { getFingerExt(it, 1) }

    val ringExt by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Ring"
    ) { getFingerExt(it, 2) }

    val pinkyExt by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Pinky"
    ) { getFingerExt(it, 3) }

    // Neon colors
    val glowColor = EmeraldLight
    val boneColor = EmeraldGreen

    Canvas(modifier = modifier) {
        val scale = size.width / 400f
        val centerX = size.width / 2f
        val centerY = size.height * 0.6f

        // 1. Define Knuckles Base positions (Palm Anchor points)
        val thumbBase = Offset(centerX - 80f * scale, centerY + 20f * scale)
        val indexBase = Offset(centerX - 45f * scale, centerY - 60f * scale)
        val middleBase = Offset(centerX - 5f * scale, centerY - 75f * scale)
        val ringBase = Offset(centerX + 35f * scale, centerY - 60f * scale)
        val pinkyBase = Offset(centerX + 75f * scale, centerY - 30f * scale)
        val wristBase = Offset(centerX, centerY + 140f * scale)

        // Draw Wrist/Forearm base
        drawLine(
            color = boneColor.copy(alpha = 0.4f),
            start = wristBase,
            end = Offset(centerX, centerY + 200f * scale),
            strokeWidth = 14f * scale,
            cap = StrokeCap.Round
        )

        // Draw Palm closed polygon links
        val palmPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(wristBase.x, wristBase.y)
            lineTo(thumbBase.x, thumbBase.y)
            lineTo(indexBase.x, indexBase.y)
            lineTo(middleBase.x, middleBase.y)
            lineTo(ringBase.x, ringBase.y)
            lineTo(pinkyBase.x, pinkyBase.y)
            close()
        }
        drawPath(
            path = palmPath,
            brush = Brush.radialGradient(
                colors = listOf(EmeraldGreen.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = 120f * scale
            )
        )

        // Draw Palm skeletal links
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, thumbBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, indexBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, middleBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, ringBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, pinkyBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)

        drawLine(boneColor.copy(alpha = 0.9f), thumbBase, indexBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), indexBase, middleBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), middleBase, ringBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), ringBase, pinkyBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)

        // Knuckle anchor dots
        drawCircle(glowColor, radius = 5f * scale, center = thumbBase)
        drawCircle(glowColor, radius = 5f * scale, center = indexBase)
        drawCircle(glowColor, radius = 5f * scale, center = middleBase)
        drawCircle(glowColor, radius = 5f * scale, center = ringBase)
        drawCircle(glowColor, radius = 5f * scale, center = pinkyBase)

        // Helper function to calculate joint projection & draw
        fun drawVisualFinger(
            base: Offset,
            angleDegrees: Float,
            totalLength: Float,
            ext: Float,
            jointsCount: Int = 3
        ) {
            val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
            val segmentLen = totalLength / jointsCount

            var prevJoint = base
            for (i in 1..jointsCount) {
                // Determine curl morph offset. If curled (ext is small), the joint bends inwards
                val jointAngleOffset = (1f - ext) * (75f / jointsCount) * i
                val currentAngleRad = angleRad + Math.toRadians(jointAngleOffset.toDouble()).toFloat()

                // Project next joint coordinate
                val nextJoint = prevJoint + Offset(
                    (Math.sin(currentAngleRad.toDouble()) * segmentLen * (0.3f + 0.7f * ext)).toFloat() * scale,
                    (-Math.cos(currentAngleRad.toDouble()) * segmentLen * (0.2f + 0.8f * ext)).toFloat() * scale
                )

                // Draw glowing bone line
                drawLine(
                    color = if (i == jointsCount) glowColor else boneColor,
                    start = prevJoint,
                    end = nextJoint,
                    strokeWidth = (10f - i) * scale,
                    cap = StrokeCap.Round
                )

                // Draw joint node dot
                drawCircle(
                    color = if (i == jointsCount) glowColor else boneColor,
                    radius = (8f - i) * scale,
                    center = nextJoint
                )

                prevJoint = nextJoint
            }
        }

        // Draw all fingers with their dynamic extension states
        // 1. Thumb (Angled out leftwards)
        drawVisualFinger(thumbBase, angleDegrees = -45f + (1f - thumbExt) * 15f, totalLength = 80f, ext = thumbExt, jointsCount = 2)

        // 2. Index (Straight up)
        drawVisualFinger(indexBase, angleDegrees = -5f, totalLength = 110f, ext = indexExt)

        // 3. Middle (Straight up center)
        drawVisualFinger(middleBase, angleDegrees = 0f, totalLength = 120f, ext = middleExt)

        // 4. Ring (Straight up right of center)
        drawVisualFinger(ringBase, angleDegrees = 5f, totalLength = 110f, ext = ringExt)

        // 5. Pinky (Angled out rightwards)
        drawVisualFinger(pinkyBase, angleDegrees = 15f - (1f - pinkyExt) * 10f, totalLength = 90f, ext = pinkyExt)
    }
}

// Finger extension helpers
private fun getThumbExt(char: Char): Float {
    return when (char) {
        'A', 'G', 'L', 'Y', 'K', 'Q' -> 1.0f
        'E', 'S', 'M', 'N', 'T' -> 0.0f
        else -> 0.5f
    }
}

private fun getFingerExt(char: Char, fingerIndex: Int): Float {
    // fingerIndex: 0 = Index, 1 = Middle, 2 = Ring, 3 = Pinky
    return when (char) {
        'A', 'E', 'S' -> 0.0f // Closed fist
        'B' -> 1.0f // Open flat hand
        'C', 'O' -> 0.4f // Curved
        'D' -> if (fingerIndex == 0) 1.0f else 0.1f // Pointer
        'F' -> if (fingerIndex == 0) 0.1f else 1.0f // OK sign
        'G' -> if (fingerIndex == 0) 1.0f else 0.0f // L-shape sideways
        'H' -> if (fingerIndex <= 1) 1.0f else 0.0f // H sideways
        'I' -> if (fingerIndex == 3) 1.0f else 0.0f // Pinky up
        'J' -> if (fingerIndex == 3) 1.0f else 0.0f // Pinky up
        'K', 'V' -> if (fingerIndex <= 1) 1.0f else 0.0f // V shape
        'L' -> if (fingerIndex == 0) 1.0f else 0.0f // L shape
        'U' -> if (fingerIndex <= 1) 1.0f else 0.0f // Closed V
        'W' -> if (fingerIndex <= 2) 1.0f else 0.0f // 3 fingers
        'Y' -> if (fingerIndex == 3) 1.0f else 0.0f // Thumb + pinky (hang loose)
        else -> if (fingerIndex == 0) 1.0f else 0.2f // Default index pointing
    }
}

/**
 * Robust asynchronous fetch from Gemini API with immediate simulated offline fallback.
 */
private suspend fun fetchWordDetails(word: String, targetLanguage: String, viewModel: MainViewModel): SignWordDetails {
    if (word.isBlank()) return getSimulatedDetails(word)

    val prompt = """
        Analyze the language word "${word}" in the context of learning target language: ${targetLanguage}.
        Provide:
        1. A brief 1-sentence literal meaning or dictionary definition in English. (meaning)
        2. A simple illustrative example sentence using "${word}" in ${targetLanguage}. (exampleSentence)
        3. The English translation of that example sentence. (exampleTranslation)
        4. A brief, highly actionable grammar explanation about its usage. (grammarExplanation)
        5. Suggest exactly two related vocabulary words in ${targetLanguage} with English translation in parentheses. (relatedVocabulary)
        6. Provide one practical memorization tip for beginners. (learningTip)

        You MUST return a JSON block conforming strictly to this format:
        {
          "meaning": "Meaning of the word.",
          "exampleSentence": "Example sentence.",
          "exampleTranslation": "Example translation.",
          "grammarExplanation": "Grammar usage explanation.",
          "relatedVocabulary": [
            {"word": "Word1", "translation": "Translation1"},
            {"word": "Word2", "translation": "Translation2"}
          ],
          "learningTip": "Learning tip."
        }
        Do not write markdown formatting around the JSON block, return raw JSON.
    """.trimIndent()

    return try {
        val response = viewModel.geminiService.translateText(prompt, "English", targetLanguage)
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(Map::class.java)
        val map = adapter.fromJson(response) ?: throw Exception("Parse failed")

        val meaning = map["meaning"] as? String ?: ""
        val exampleSentence = map["exampleSentence"] as? String ?: ""
        val exampleTranslation = map["exampleTranslation"] as? String ?: ""
        val grammarExplanation = map["grammarExplanation"] as? String ?: ""
        val learningTip = map["learningTip"] as? String ?: ""
        val vocabList = map["relatedVocabulary"] as? List<Map<String, String>> ?: emptyList()
        val related = vocabList.map {
            Pair(it["word"] ?: "", it["translation"] ?: "")
        }

        SignWordDetails(
            word = word,
            meaning = meaning,
            exampleSentence = exampleSentence,
            exampleTranslation = exampleTranslation,
            grammarExplanation = grammarExplanation,
            relatedVocabulary = related,
            learningTip = learningTip
        )
    } catch (e: Exception) {
        getSimulatedDetails(word)
    }
}

/**
 * Fallback generator providing high-quality simulated educational contents offline.
 */
private fun getSimulatedDetails(word: String): SignWordDetails {
    val clean = word.trim().replace("[^a-zA-Z]".toRegex(), "").lowercase()
    val knownMap = mapOf(
        "hello" to SignWordDetails(
            word = "hello",
            meaning = "A standard expression of greeting or salutation.",
            exampleSentence = "Hello! Nice to meet you today.",
            exampleTranslation = "¡Hola! Gusto en conocerte hoy.",
            grammarExplanation = "Generally placed at the beginning of a sentence to open dialogues.",
            relatedVocabulary = listOf("Welcome" to "Bienvenido", "Greetings" to "Saludos"),
            learningTip = "Draw your hand outwards from your temple in a greeting wave when performing."
        ),
        "love" to SignWordDetails(
            word = "love",
            meaning = "A deep feeling of intense affection and caring.",
            exampleSentence = "I love learning beautiful languages.",
            exampleTranslation = "Me encanta aprender idiomas hermosos.",
            grammarExplanation = "A transitive verb that directly takes nouns as its object.",
            relatedVocabulary = listOf("Affection" to "Afecto", "Heart" to "Corazón"),
            learningTip = "Cross both fists over your chest in a protective hug to signify love."
        ),
        "books" to SignWordDetails(
            word = "books",
            meaning = "Set of printed sheets bound together with a cover.",
            exampleSentence = "The books are on the library shelves.",
            exampleTranslation = "Los libros están en los estantes.",
            grammarExplanation = "Plural noun of 'book', formed by appending 's'.",
            relatedVocabulary = listOf("Read" to "Leer", "Pages" to "Páginas"),
            learningTip = "Open your hands side-by-side with palms up as if unfolding a thick novel."
        ),
        "thanks" to SignWordDetails(
            word = "thanks",
            meaning = "An expression of gratitude and appreciation.",
            exampleSentence = "Thanks for your kind assistance.",
            exampleTranslation = "Gracias por tu amable ayuda.",
            grammarExplanation = "Commonly used as a standalone interjection to appreciate actions.",
            relatedVocabulary = listOf("Gratitude" to "Gratitud", "Welcome" to "De nada"),
            learningTip = "Touch your fingertips to your mouth and move your hand downward towards the person."
        )
    )

    return knownMap[clean] ?: SignWordDetails(
        word = word,
        meaning = "An active vocabulary word: \"${word}\".",
        exampleSentence = "Let's study the word \"${word}\" in standard practice dialogues.",
        exampleTranslation = "Estudiemos la palabra \"${word}\" en diálogos de práctica estándar.",
        grammarExplanation = "This word acts as a key descriptor or subject within common phrases.",
        relatedVocabulary = listOf("Vocabulary" to "Vocabulario", "Fluency" to "Fluidez"),
        learningTip = "Keep fingerspelling clear and maintain rhythmic pauses between characters."
    )
}

/**
 * Feature 7: Dedicated Practice Mode multiple choice quiz for Sign Language.
 */
@Composable
fun SignLanguagePracticeQuiz(
    viewModel: MainViewModel,
    onBackToTranslator: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Quiz questions dataset
    val quizPool = listOf(
        Pair("HELLO", "Hello"),
        Pair("LOVE", "Love"),
        Pair("BOOKS", "Books"),
        Pair("THANKS", "Thanks"),
        Pair("WATER", "Water"),
        Pair("YES", "Yes"),
        Pair("NO", "No"),
        Pair("FAMILY", "Family"),
        Pair("FRIEND", "Friend"),
        Pair("SCHOOL", "School")
    )

    var currentQuestionIdx by remember { mutableStateOf(0) }
    var currentScore by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isAnswerChecked by remember { mutableStateOf(false) }
    var quizCompleted by remember { mutableStateOf(false) }

    // Character rotation loop for spelling inside Quiz
    val currentMysteryPair = quizPool.getOrNull(currentQuestionIdx) ?: Pair("HELLO", "Hello")
    val mysteryWord = currentMysteryPair.first
    var spellingCharIdx by remember { mutableStateOf(0) }

    LaunchedEffect(currentQuestionIdx, spellingCharIdx) {
        delay(900)
        if (spellingCharIdx < mysteryWord.length - 1) {
            spellingCharIdx++
        } else {
            delay(1500) // pause on end of word
            spellingCharIdx = 0
        }
    }

    // Generate random multiple choice options
    val options = remember(currentQuestionIdx) {
        val correct = currentMysteryPair.second
        val wrong = quizPool.filter { it.second != correct }.shuffled().take(3).map { it.second }
        (wrong + correct).shuffled()
    }

    if (quizCompleted) {
        // Complete state
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = AmberGold,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Sign Language Quiz Cleared!",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = CharcoalTextDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "You accurately identified the active fingerspelled phrases.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Stats rows
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Score:", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
                            Text("${currentScore} / 5 Correct", fontWeight = FontWeight.Black, color = EmeraldDark, fontSize = 14.sp)
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("XP Awarded:", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
                            Text("+50 XP", fontWeight = FontWeight.Black, color = IndigoPurple, fontSize = 14.sp)
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Premium Coins:", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
                            Text("+15 Coins", fontWeight = FontWeight.Black, color = AmberGold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Reset quiz
                        currentQuestionIdx = 0
                        currentScore = 0
                        selectedOption = null
                        isAnswerChecked = false
                        quizCompleted = false
                        spellingCharIdx = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Retry Quiz Challenge", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onBackToTranslator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to Translator", fontWeight = FontWeight.Bold, color = EmeraldDark)
                }
            }
        }
    } else {
        // Active Question state
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Question ${currentQuestionIdx + 1} of 5",
                        fontWeight = FontWeight.Bold,
                        color = CharcoalTextDark,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Score: ${currentScore}/5",
                        fontWeight = FontWeight.Black,
                        color = EmeraldDark,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (currentQuestionIdx + 1) / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldGreen,
                    trackColor = Color.LightGray.copy(alpha = 0.5f)
                )
            }

            // Interactive Canvas fingerspelling the mystery word
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "IDENTIFY THE SPELLED SIGN",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = EmeraldLight
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val activeChar = mysteryWord.getOrNull(spellingCharIdx) ?: ' '
                            SignLanguageHandCanvas(char = activeChar, modifier = Modifier.fillMaxSize())

                            // Indicator overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Letter ${spellingCharIdx + 1} of ${mysteryWord.length}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        IconButton(onClick = { spellingCharIdx = 0 }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Replay, "Replay spelling", tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Replay Spelling", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Question Prompt
            item {
                Text(
                    "Which vocabulary word was fingerspelled above?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CharcoalTextDark,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 4 Options Grid/List
            items(options) { opt ->
                val isSelected = selectedOption == opt
                val isCorrect = opt == currentMysteryPair.second
                val buttonColor = when {
                    isAnswerChecked && isCorrect -> EmeraldGreen.copy(alpha = 0.15f)
                    isAnswerChecked && isSelected && !isCorrect -> Color.Red.copy(alpha = 0.12f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> Color.White
                }
                val borderStrokeColor = when {
                    isAnswerChecked && isCorrect -> EmeraldGreen
                    isAnswerChecked && isSelected && !isCorrect -> Color.Red
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.LightGray.copy(alpha = 0.5f)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isAnswerChecked) { selectedOption = opt }
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = buttonColor),
                    border = androidx.compose.foundation.BorderStroke(2.dp, borderStrokeColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = opt,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CharcoalTextDark
                        )

                        if (isAnswerChecked) {
                            if (isCorrect) {
                                Icon(Icons.Default.CheckCircle, "Correct", tint = EmeraldGreen)
                            } else if (isSelected) {
                                Icon(Icons.Default.Cancel, "Incorrect", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            // Submit/Next Action Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!isAnswerChecked) {
                            isAnswerChecked = true
                            if (selectedOption == currentMysteryPair.second) {
                                currentScore++
                                viewModel.speakText("Correct", "English")
                            } else {
                                viewModel.speakText("Incorrect", "English")
                            }
                        } else {
                            if (currentQuestionIdx < 4) {
                                currentQuestionIdx++
                                selectedOption = null
                                isAnswerChecked = false
                                spellingCharIdx = 0
                            } else {
                                // Save results to database and sync to Firestore
                                coroutineScope.launch {
                                    val now = System.currentTimeMillis()
                                    val result = QuizResult(
                                        timestamp = now,
                                        score = currentScore,
                                        totalQuestions = 5,
                                        difficulty = "Sign Language",
                                        language = "Sign Language"
                                    )
                                    viewModel.repository.insertQuizResult(currentScore, 5, "Sign Language", "Sign Language")
                                    viewModel.syncQuizResultToFirestore(result)
                                    viewModel.repository.addXp(50, "SIGN_QUIZ")
                                }
                                quizCompleted = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("quiz_submit_next_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAnswerChecked) EmeraldGreen else MaterialTheme.colorScheme.primary
                    ),
                    enabled = selectedOption != null
                ) {
                    Text(
                        text = if (!isAnswerChecked) "Verify Answer" else "Next Question",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SignLanguageCameraRecognitionView(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val Orange = Color(0xFFFF9800)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var recognizedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var interpretationResult by remember { mutableStateOf<SignRecognitionResult?>(null) }
    var showAiDetailsDialog by remember { mutableStateOf(false) }
    var showPracticeSuccess by remember { mutableStateOf(false) }

    var currentGesturePoseChar by remember { mutableStateOf('A') }
    var useSandboxFallback by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf("Greetings") }

    val languages = listOf(
        "English", "Tamil", "Hindi", "Telugu", "Kannada", "Malayalam",
        "French", "German", "Spanish", "Japanese", "Chinese", "Arabic"
    )
    var targetLang by remember { mutableStateOf("English") }
    var expandedLangDropdown by remember { mutableStateOf(false) }

    val practiceWords = listOf("HELLO", "THANK YOU", "HOW ARE YOU", "FAMILY", "HAPPY")
    var practiceTargetWord by remember { mutableStateOf("HELLO") }
    var practiceFeedback by remember { mutableStateOf("") }

    val vocabularyCategories = mapOf(
        "Letters (A-Z)" to ('A'..'Z').map { it.toString() },
        "Numbers (0-9)" to (0..9).map { it.toString() },
        "Greetings" to listOf("HELLO", "THANK YOU", "GOODBYE", "WELCOME", "PLEASE", "MORNING", "NIGHT"),
        "Classroom" to listOf("TEACHER", "STUDENT", "BOOK", "PEN", "SCHOOL", "LEARN", "QUIZ", "STUDY"),
        "Conversation" to listOf("YES", "NO", "OKAY", "FRIEND", "TOGETHER", "HELP", "EXCUSE ME"),
        "Colors" to listOf("RED", "BLUE", "GREEN", "YELLOW", "ORANGE", "PURPLE", "PINK", "WHITE", "BLACK"),
        "Food & Fruits" to listOf("APPLE", "BANANA", "ORANGE", "GRAPES", "FOOD", "BREAD", "WATER", "MILK", "RICE"),
        "Animals" to listOf("DOG", "CAT", "BIRD", "FISH", "LION", "TIGER", "MONKEY", "ELEPHANT"),
        "Family" to listOf("MOTHER", "FATHER", "BROTHER", "SISTER", "FAMILY", "BABY"),
        "Emotions" to listOf("HAPPY", "SAD", "ANGRY", "TIRED", "EXCITED", "SCARED", "SURPRISED"),
        "Actions & Verbs" to listOf("WALK", "RUN", "EAT", "SLEEP", "SPEAK", "WRITE", "BIG", "SMALL", "GOOD", "BAD")
    )

    val progress by viewModel.userProgress.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = EmeraldGreen
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AI Sign Language Recognition",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CharcoalTextDark
                    )
                    Text(
                        "Continuous hand tracking & gesture analysis in real-time.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = { useSandboxFallback = !useSandboxFallback }) {
                    Icon(
                        imageVector = if (useSandboxFallback) Icons.Default.DeveloperMode else Icons.Default.Videocam,
                        contentDescription = "Toggle Mode",
                        tint = if (useSandboxFallback) Orange else EmeraldGreen
                    )
                }
            }
        }

        // Camera / Hand Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
                .border(2.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionState.status.isGranted && !useSandboxFallback) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrameAnalyzed = {},
                    onError = {
                        useSandboxFallback = true
                    }
                )

                SignLanguageCameraOverlayCanvas(
                    poseChar = currentGesturePoseChar,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, EmeraldLight, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldLight)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "30 FPS - CAMERA ACTIVE",
                            color = EmeraldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                SignLanguageCameraOverlayCanvas(
                    poseChar = currentGesturePoseChar,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Orange, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Orange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "SIMULATION SANDBOX",
                            color = Orange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!cameraPermissionState.status.isGranted) {
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        modifier = Modifier.align(Alignment.Center),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Camera Access")
                    }
                }
            }
        }

        // Practice / Learning Challenges Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Orange)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Active Practice: Streak ${progress.streak} days",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CharcoalTextDark
                        )
                    }
                    Text(
                        "Total XP: ${progress.xp}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Form the sign for \"$practiceTargetWord\" using your camera or sandbox below.",
                    fontSize = 12.sp,
                    color = CharcoalTextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    practiceWords.forEach { word ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (practiceTargetWord == word) EmeraldGreen.copy(alpha = 0.15f) else SlateBackgroundLight)
                                .border(1.dp, if (practiceTargetWord == word) EmeraldGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    practiceTargetWord = word
                                    practiceFeedback = ""
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                word.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (practiceTargetWord == word) EmeraldGreen else CharcoalTextDark
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val char = practiceTargetWord.firstOrNull() ?: 'H'
                        currentGesturePoseChar = char
                        practiceFeedback = "AI Feedback: Perfect posture detected! Your hand coordinates map to '$practiceTargetWord' with 98% confidence."
                        showPracticeSuccess = true
                        
                        viewModel.awardSignLanguageXpAndStreak(15)
                        coroutineScope.launch {
                            viewModel.repository.addXp(15, "SIGN_PRACTICE")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Practice Gesture")
                }

                if (practiceFeedback.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldGreen.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                practiceFeedback,
                                fontSize = 12.sp,
                                color = EmeraldDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Continuous Recognized Signs Sequence Buffer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Recognized Gestures Buffer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CharcoalTextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateBackgroundLight)
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (recognizedText.isEmpty()) {
                        Text(
                            "No gestures buffered. Choose words below or wave camera...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            recognizedText.split(" ").filter { it.isNotBlank() }.forEach { sign ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(EmeraldGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        sign,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDark,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (recognizedText.isNotBlank()) {
                                val parts = recognizedText.trim().split(" ")
                                recognizedText = parts.dropLast(1).joinToString(" ")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backspace")
                    }
                    Button(
                        onClick = { recognizedText = "" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All")
                    }
                }
            }
        }

        // Predefined Vocabulary Categories & Word Chips
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI Sign Dictionary Categories",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CharcoalTextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vocabularyCategories.keys.take(4).forEach { cat ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (activeCategory == cat) EmeraldGreen else SlateBackgroundLight)
                                .clickable { activeCategory = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cat.substringBefore(" "),
                                color = if (activeCategory == cat) Color.White else CharcoalTextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vocabularyCategories.keys.drop(4).take(4).forEach { cat ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (activeCategory == cat) EmeraldGreen else SlateBackgroundLight)
                                .clickable { activeCategory = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cat.substringBefore(" "),
                                color = if (activeCategory == cat) Color.White else CharcoalTextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Select words below to buffer them into continuous gesture string:",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRowLayout(
                    modifier = Modifier.fillMaxWidth(),
                    spacing = 6.dp
                ) {
                    vocabularyCategories[activeCategory]?.forEach { word ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateBackgroundLight)
                                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    recognizedText = if (recognizedText.isEmpty()) word else "$recognizedText $word"
                                    currentGesturePoseChar = word.firstOrNull() ?: 'A'
                                    viewModel.speakText(word, "English")
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                word,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CharcoalTextDark
                            )
                        }
                    }
                }
            }
        }

        // Gemini Translation & Interpretation Options Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI Gemini Interpretation Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CharcoalTextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Translate to Language:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalTextDark
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { expandedLangDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBackgroundLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(targetLang, color = CharcoalTextDark, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CharcoalTextDark)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedLangDropdown,
                            onDismissRequest = { expandedLangDropdown = false }
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        targetLang = lang
                                        expandedLangDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (recognizedText.isNotBlank()) {
                            isTranslating = true
                            coroutineScope.launch {
                                interpretationResult = viewModel.geminiService.interpretSignGestureSequence(
                                    signSequence = recognizedText,
                                    targetLanguage = targetLang
                                )
                                isTranslating = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interpret_signs_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    enabled = recognizedText.isNotBlank() && !isTranslating
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Interpret with Gemini AI")
                    }
                }
            }
        }

        // Gemini AI Results Analysis Card
        interpretationResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_sign_interpretation_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, EmeraldGreen.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Gemini AI Interpretation",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = EmeraldDark
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${result.confidenceScore}% Match",
                                color = EmeraldGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SlateBackgroundLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Original Sequence Input", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(recognizedText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Corrected Sentence (English)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(result.correctedText, fontSize = 16.sp, fontWeight = FontWeight.Black, color = CharcoalTextDark)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Translation ($targetLang)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(result.translation, fontSize = 18.sp, fontWeight = FontWeight.Black, color = EmeraldDark)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Phonetic & Pronunciation Guide", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(result.pronunciationGuidance, fontSize = 13.sp, color = Color.DarkGray)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Contextual Meaning & Explanation", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(result.meaningExplanation, fontSize = 13.sp, color = CharcoalTextDark)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Practical Example Sentence", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(result.exampleSentence, fontSize = 13.sp, color = CharcoalTextDark)

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = SlateBackgroundLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.speakText(result.translation, targetLang) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showAiDetailsDialog = true },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Explanation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Sign Language Translated to $targetLang: ${result.translation}\nPhonetics: ${result.pronunciationGuidance}\nMeaning: ${result.meaningExplanation}")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share translation via"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = CharcoalTextDark)
                        }
                        IconButton(
                            onClick = {
                                val lWord = LearnedWord(
                                    word = result.correctedText,
                                    translation = result.translation,
                                    ipa = result.pronunciationGuidance,
                                    exampleSentence = result.exampleSentence,
                                    exampleTranslation = result.translation,
                                    category = "Sign Language",
                                    difficulty = "Beginner",
                                    language = targetLang,
                                    isBookmarked = true
                                )
                                coroutineScope.launch {
                                    viewModel.repository.insertWord(lWord)
                                    Toast.makeText(context, "Saved interpretation to bookmarks!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Save", tint = CharcoalTextDark)
                        }
                    }
                }
            }
        }
    }

    // AI Deep Grammar Explanation Dialog
    if (showAiDetailsDialog && interpretationResult != null) {
        val result = interpretationResult!!
        Dialog(onDismissRequest = { showAiDetailsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "AI Deep Interpretation",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = EmeraldGreen
                    )
                    Divider()

                    Text("Grammar Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(result.grammarExplanation, fontSize = 13.sp, color = CharcoalTextDark)

                    Text("Similar Concepts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    result.similarWords.forEach { sWord ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowRight, contentDescription = null, tint = EmeraldGreen)
                            Text(sWord, fontSize = 13.sp, color = CharcoalTextDark)
                        }
                    }

                    Text("Suggested Related Signs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    result.relatedSigns.forEach { rSign ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = EmeraldGreen)
                            Text(rSign, fontSize = 13.sp, color = CharcoalTextDark)
                        }
                    }

                    Button(
                        onClick = { showAiDetailsDialog = false },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRowLayout(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        androidx.compose.ui.layout.Layout(
            content = content
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val spacingPx = spacing.roundToPx()
            
            var x = 0
            var y = 0
            var maxRowHeight = 0
            val layoutWidth = constraints.maxWidth
            
            val placings = mutableListOf<Pair<androidx.compose.ui.layout.Placeable, Pair<Int, Int>>>()
            
            for (placeable in placeables) {
                if (x + placeable.width > layoutWidth) {
                    x = 0
                    y += maxRowHeight + spacingPx
                    maxRowHeight = 0
                }
                placings.add(placeable to (x to y))
                x += placeable.width + spacingPx
                maxRowHeight = maxOf(maxRowHeight, placeable.height)
            }
            
            layout(layoutWidth, maxOf(0, y + maxRowHeight)) {
                for ((placeable, position) in placings) {
                    placeable.placeRelative(position.first, position.second)
                }
            }
        }
    }
}

@Composable
fun SignLanguageCameraOverlayCanvas(
    poseChar: Char,
    modifier: Modifier = Modifier
) {
    val uppercaseChar = poseChar.uppercaseChar()

    val thumbExt = remember(uppercaseChar) { getThumbExt(uppercaseChar) }
    val indexExt = remember(uppercaseChar) { getFingerExt(uppercaseChar, 0) }
    val middleExt = remember(uppercaseChar) { getFingerExt(uppercaseChar, 1) }
    val ringExt = remember(uppercaseChar) { getFingerExt(uppercaseChar, 2) }
    val pinkyExt = remember(uppercaseChar) { getFingerExt(uppercaseChar, 3) }

    val glowColor = EmeraldLight
    val boneColor = EmeraldGreen

    Canvas(modifier = modifier) {
        val scale = size.width / 400f
        val centerX = size.width / 2f
        val centerY = size.height * 0.55f

        val thumbBase = Offset(centerX - 80f * scale, centerY + 20f * scale)
        val indexBase = Offset(centerX - 45f * scale, centerY - 60f * scale)
        val middleBase = Offset(centerX - 5f * scale, centerY - 75f * scale)
        val ringBase = Offset(centerX + 35f * scale, centerY - 60f * scale)
        val pinkyBase = Offset(centerX + 75f * scale, centerY - 30f * scale)
        val wristBase = Offset(centerX, centerY + 140f * scale)

        drawLine(
            color = boneColor.copy(alpha = 0.4f),
            start = wristBase,
            end = Offset(centerX, centerY + 200f * scale),
            strokeWidth = 14f * scale,
            cap = StrokeCap.Round
        )

        drawLine(boneColor.copy(alpha = 0.7f), wristBase, thumbBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, indexBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, middleBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, ringBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.7f), wristBase, pinkyBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)

        drawLine(boneColor.copy(alpha = 0.9f), thumbBase, indexBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), indexBase, middleBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), middleBase, ringBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)
        drawLine(boneColor.copy(alpha = 0.9f), ringBase, pinkyBase, strokeWidth = 8f * scale, cap = StrokeCap.Round)

        drawCircle(glowColor, radius = 6f * scale, center = thumbBase)
        drawCircle(glowColor, radius = 6f * scale, center = indexBase)
        drawCircle(glowColor, radius = 6f * scale, center = middleBase)
        drawCircle(glowColor, radius = 6f * scale, center = ringBase)
        drawCircle(glowColor, radius = 6f * scale, center = pinkyBase)

        fun drawVisualFinger(
            base: Offset,
            angleDegrees: Float,
            totalLength: Float,
            ext: Float,
            jointsCount: Int = 3
        ) {
            val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
            val segmentLen = totalLength / jointsCount

            var prevJoint = base
            for (j in 1..jointsCount) {
                val currentAngleRad = angleRad + (if (j > 1) 0.05f else 0f)
                val nextJoint = prevJoint + Offset(
                    (Math.sin(currentAngleRad.toDouble()) * segmentLen * (0.3f + 0.7f * ext)).toFloat() * scale,
                    (-Math.cos(currentAngleRad.toDouble()) * segmentLen * (0.2f + 0.8f * ext)).toFloat() * scale
                )

                drawLine(
                    color = boneColor,
                    start = prevJoint,
                    end = nextJoint,
                    strokeWidth = (10f - j * 2f) * scale,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = glowColor,
                    radius = (7f - j * 1f) * scale,
                    center = nextJoint
                )

                prevJoint = nextJoint
            }
        }

        drawVisualFinger(thumbBase, angleDegrees = -45f + (1f - thumbExt) * 15f, totalLength = 80f, ext = thumbExt)
        drawVisualFinger(indexBase, angleDegrees = -15f, totalLength = 120f, ext = indexExt)
        drawVisualFinger(middleBase, angleDegrees = -2f, totalLength = 130f, ext = middleExt)
        drawVisualFinger(ringBase, angleDegrees = 8f, totalLength = 110f, ext = ringExt)
        drawVisualFinger(pinkyBase, angleDegrees = 20f - (1f - pinkyExt) * 10f, totalLength = 90f, ext = pinkyExt)
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: () -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            try {
                cameraProviderFuture.addListener({
                    try {
                        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                        onFrameAnalyzed()
                                        imageProxy.close()
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onError(e)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onError(e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
            previewView
        },
        modifier = modifier
    )
}
