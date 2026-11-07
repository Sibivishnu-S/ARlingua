package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@Composable
fun TranslationScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val translationInput by viewModel.translationInput.collectAsStateWithLifecycle()
    val translatedOutput by viewModel.translatedOutput.collectAsStateWithLifecycle()
    val isTranslating by viewModel.isTranslating.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val pronunciationScore by viewModel.pronunciationScore.collectAsStateWithLifecycle()
    val pronunciationFeedback by viewModel.pronunciationFeedback.collectAsStateWithLifecycle()
    val speakingWaveform by viewModel.speakingWaveform.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Translator, 1 = Pronunciation

    val supportedLanguages = listOf(
        "English", "Spanish", "French", "German", "Japanese", "Korean",
        "Chinese", "Tamil", "Hindi", "Telugu", "Kannada", "Malayalam", "Arabic"
    )

    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }

    // Preset challenge speaking phrases depending on target language
    val challengePhrase = when (targetLang) {
        "Spanish" -> "Buenos días, me gustaría un café por favor."
        "French" -> "Bonjour, enchanté de faire votre connaissance."
        "German" -> "Guten Tag, wie geht es Ihnen heute?"
        "Japanese" -> "こんにちは、お元気ですか？"
        "Korean" -> "안녕하세요, 만나서 반갑습니다."
        "Chinese" -> "您好，很高兴认识您。"
        "Tamil" -> "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?"
        "Hindi" -> "नमस्ते, आप कैसे हैं आज?"
        "Telugu" -> "నమస్కారం, మీరు ఎలా ఉన్నారు?"
        "Kannada" -> "ನಮಸ್ಕಾರ, ನೀವು ಹೇಗಿದ್ದೀರಾ?"
        "Malayalam" -> "നമസ്കാരം, സുഖമാണോ?"
        "Arabic" -> "مرحباً، كيف حالك اليوم؟"
        else -> "Hello, nice to meet you today."
    }

    val challengeIpa = when (targetLang) {
        "Spanish" -> "/ˈbwenos ˈði.as me ɣustaˈɾi.a un kaˈfe poɾ faˈβoɾ/"
        "French" -> "/bɔ̃.ʒuʁ ɑ̃.ʃɑ̃.te də fɛʁ vɔtʁ kɔ.nɛ.sɑ̃s/"
        "German" -> "/ˈɡuːtn̩ ˈtaːk viː ɡeːt ʔɛs ˈiːnən ˈhɔʏtə/"
        "Japanese" -> "/kon.ni.tɕi.wa o.ɡeŋ.ki.de.su.ka/"
        "Korean" -> "/an.njʌŋ.ha.se.jo man.na.sʌ pan.kap.sɯm.ni.da/"
        "Chinese" -> "/nín hǎo hěn gāo xìng rèn shi nǐ/"
        "Tamil" -> "/ʋaɳakːam, niːŋɡaɭ epːaɖi iɾukːiɾiːɾɡaɭ/"
        "Hindi" -> "/nəməsteː, aːp kɛːseː ɦɛ̃ː aːdʒ/"
        "Telugu" -> "/namaskaːram, miːru elaː unnaːru/"
        "Kannada" -> "/namaskaːra, niːʋu heːɡidːiːraː/"
        "Malayalam" -> "/namaskaːram, sukamanoː/"
        "Arabic" -> "/marħaban, kajfa ħaːluka ljawm/"
        else -> "/hɛˈloʊ naɪs tu miːt ju təˈdeɪ/"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .testTag("translation_screen")
    ) {
        // --- 1. Custom Sliding Tab Selector ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTab == 0) EmeraldGreen else Color.Transparent)
                    .clickable { activeTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Text Translator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (activeTab == 0) Color.White else CharcoalTextDark
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeTab == 1) EmeraldGreen else Color.Transparent)
                    .clickable { activeTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Speaking Practice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (activeTab == 1) Color.White else CharcoalTextDark
                )
            }
        }

        // --- Tab 0: INSTANT TEXT TRANSLATOR ---
        if (activeTab == 0) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Source Language Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { sourceExpanded = true }
                                ) {
                                    Text(sourceLang, fontWeight = FontWeight.Bold, color = MutedTextLight, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select language", tint = MutedTextLight, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = sourceExpanded,
                                    onDismissRequest = { sourceExpanded = false }
                                ) {
                                    supportedLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang) },
                                            onClick = {
                                                viewModel.setSourceLanguage(lang)
                                                sourceExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()
                                IconButton(
                                    onClick = {
                                        if (isListening) {
                                            viewModel.stopVoiceRecognition()
                                        } else {
                                            viewModel.startVoiceRecognition(
                                                contextType = "TRANSLATOR",
                                                languageName = sourceLang,
                                                onTextUpdate = { recognized ->
                                                    viewModel.updateTranslationInput(recognized)
                                                },
                                                onComplete = { finalSpeech ->
                                                    viewModel.updateTranslationInput(finalSpeech)
                                                    viewModel.executeTranslate()
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(28.dp).testTag("translator_microphone_button")
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Voice input",
                                        tint = if (isListening) Color.Red else IndigoPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (translationInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.updateTranslationInput("") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Clear", tint = Color.LightGray)
                                    }
                                }
                            }
                        }
                        TextField(
                            value = translationInput,
                            onValueChange = { viewModel.updateTranslationInput(it) },
                            placeholder = { Text("Enter $sourceLang vocabulary or phrases here...", fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .testTag("translator_input"),
                            colors = AppTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 4
                        )
                    }
                }

                // Swap Languages Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { viewModel.swapTranslationLanguages() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap",
                            tint = EmeraldDark
                        )
                    }
                }

                // Target Language Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { targetExpanded = true }
                                ) {
                                    Text(targetLang, fontWeight = FontWeight.Bold, color = EmeraldDark, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select language", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = targetExpanded,
                                    onDismissRequest = { targetExpanded = false }
                                ) {
                                    supportedLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang) },
                                            onClick = {
                                                viewModel.setTargetLanguage(lang)
                                                targetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (translatedOutput.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.speakText(translatedOutput, targetLang) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(EmeraldGreen.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Speak translation", tint = EmeraldGreen)
                                }
                            }
                        }

                        if (isTranslating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = EmeraldGreen, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Text(
                                text = translatedOutput.ifEmpty { "Translation output in $targetLang will appear here..." },
                                fontSize = 15.sp,
                                color = if (translatedOutput.isEmpty()) Color.LightGray else CharcoalTextDark,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp)
                                    .padding(vertical = 8.dp),
                                fontWeight = if (translatedOutput.isEmpty()) FontWeight.Normal else FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Translate Action Button
                Button(
                    onClick = { viewModel.executeTranslate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(bottom = 8.dp)
                        .testTag("translate_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GTranslate, contentDescription = "Translate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRANSLATE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // --- Tab 1: PRONUNCIATION SPEAKING PRACTICE ---
        if (activeTab == 1) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Phrase Target Challenge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CHALLENGE PHRASE", fontWeight = FontWeight.ExtraBold, color = MutedTextLight, fontSize = 10.sp)
                            IconButton(
                                onClick = { viewModel.speakText(challengePhrase, targetLang) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(EmeraldGreen.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Listen guide", tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = challengePhrase,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldDark,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = challengeIpa,
                            fontSize = 12.sp,
                            color = MutedTextLight,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Live Audio Waveform animation when recording
                if (isRecording || speakingWaveform.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalTextDark)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                                val width = size.width
                                val height = size.height
                                val barWidth = 6.dp.toPx()
                                val gap = 4.dp.toPx()
                                
                                val totalBars = speakingWaveform.size
                                speakingWaveform.forEachIndexed { idx, amplitude ->
                                    val barHeight = height * amplitude * 0.7f
                                    val x = (width / totalBars) * idx
                                    val y = (height - barHeight) / 2
                                    
                                    drawRoundRect(
                                        color = EmeraldLight,
                                        topLeft = Offset(x, y),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                // Pronunciation Score and Breakdown Result
                if (pronunciationScore != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ACCENT METERS SCORE", fontWeight = FontWeight.Bold, color = MutedTextLight, fontSize = 10.sp)
                                
                                val color = when {
                                    pronunciationScore!! >= 90 -> EmeraldGreen
                                    pronunciationScore!! >= 80 -> AmberGold
                                    else -> Color.Red
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$pronunciationScore % ACCURATE",
                                        color = color,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Divider(color = Color.LightGray.copy(alpha = 0.4f))

                            Text(
                                text = pronunciationFeedback,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CharcoalTextDark
                            )

                            // Phonemes analysis highlights
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                challengePhrase.split(" ").take(4).forEach { word ->
                                    val isGood = (0..5).random() != 3 // Mock some vowel stress feedback
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isGood) EmeraldGreen.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = word.replace(",", "").replace(".", ""),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGood) EmeraldDark else Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Speech Capture Mic Button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (isRecording) 1.2f else 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "micScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .rotate(if (isRecording) scale * 5 else 0f)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.Red else EmeraldGreen)
                            .clickable {
                                if (isRecording) {
                                    viewModel.stopPronunciationPractice(challengePhrase)
                                } else {
                                    viewModel.startPronunciationPractice()
                                }
                            }
                            .testTag("microphone_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Microphone speak button",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = if (isRecording) "TAP TO STOP AND ANALYZE" else "TAP MICROPHONE TO BEGIN SPEAKING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isRecording) Color.Red else MutedTextLight
                    )
                }
            }
        }
    }
}
