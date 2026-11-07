package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@Composable
fun QuizScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val questions by viewModel.currentQuizQuestions.collectAsStateWithLifecycle()
    val index by viewModel.quizIndex.collectAsStateWithLifecycle()
    val score by viewModel.quizScore.collectAsStateWithLifecycle()
    val isCompleted by viewModel.isQuizCompleted.collectAsStateWithLifecycle()
    val selectedAnswer by viewModel.selectedQuizAnswer.collectAsStateWithLifecycle()
    val checked by viewModel.quizAnswerChecked.collectAsStateWithLifecycle()

    // Trigger quiz creation when screen loads
    LaunchedEffect(Unit) {
        viewModel.generateQuiz()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .testTag("quiz_screen")
    ) {
        // --- Header Section ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = "Quiz Logo",
                        tint = AmberGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quiz Arena",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = CharcoalTextDark
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AmberGold.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = selectedLang,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        val activeSource by viewModel.quizSourceType.collectAsStateWithLifecycle()

        // --- Quiz Mode / Source Selection Bar ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "QUIZ SOURCE & MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MutedTextLight,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chat History Option
                    Button(
                        onClick = { viewModel.generateQuiz("CHAT") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeSource == "CHAT") EmeraldDark else SlateBackgroundLight
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            "💬 From AI Chat",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSource == "CHAT") Color.White else CharcoalTextDark
                        )
                    }

                    // New Topics Option
                    Button(
                        onClick = { viewModel.generateQuiz("NEW") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeSource == "NEW") MaterialTheme.colorScheme.primary else SlateBackgroundLight
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            "⚡ New Topics",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSource == "NEW") Color.White else CharcoalTextDark
                        )
                    }

                    // AI Exam Option
                    Button(
                        onClick = { viewModel.generateQuiz("EXAM") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeSource == "EXAM") IndigoPurple else SlateBackgroundLight
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            "🎓 AI Exam",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSource == "EXAM") Color.White else CharcoalTextDark
                        )
                    }
                }
            }
        }

        if (isCompleted) {
            // --- QUIZ COMPLETED SCREEN STATE ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Trophy Badge Icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(AmberGold.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = AmberGold,
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Congratulations!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = CharcoalTextDark
                )
                
                Text(
                    text = "You completed the adaptive $selectedLang Challenge!",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Results Info Panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "YOUR PERFORMANCE SUMMARY",
                            fontWeight = FontWeight.ExtraBold,
                            color = MutedTextLight,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$score / ${questions.size}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    color = EmeraldDark
                                )
                                Text("Correct Answers", fontSize = 11.sp, color = Color.Gray)
                            }
                            
                            val xpGained = score * 10 + (if (score == questions.size) 20 else 0)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "+$xpGained",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    color = AmberGold
                                )
                                Text("XP Points Earned", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Button(
                    onClick = { viewModel.generateQuiz() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("PRACTICE AGAIN", fontWeight = FontWeight.Bold)
                }
            }
        } else if (questions.isNotEmpty()) {
            // --- ACTIVE QUIZ PLAY STATE ---
            val currentQuestion = questions.getOrNull(index)
            if (currentQuestion != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Question index counter + Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Question ${index + 1} of ${questions.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedTextLight
                            )
                            Text(
                                text = "Score: $score",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark
                            )
                        }
                        
                        val progressRatio = (index + 1).toFloat() / questions.size
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldGreen,
                            trackColor = EmeraldGreen.copy(alpha = 0.15f)
                        )
                    }

                    // Question Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentQuestion.questionText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CharcoalTextDark,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Answer Options Column
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        currentQuestion.options.forEach { option ->
                            val isSelected = selectedAnswer == option
                            val isCorrect = option == currentQuestion.correctAnswer
                            
                            val containerColor = when {
                                checked && isCorrect -> EmeraldGreen.copy(alpha = 0.12f)
                                checked && isSelected && !isCorrect -> Color.Red.copy(alpha = 0.1f)
                                isSelected -> IndigoPurple.copy(alpha = 0.08f)
                                else -> Color.White
                            }
                            
                            val borderColor = when {
                                checked && isCorrect -> EmeraldGreen
                                checked && isSelected && !isCorrect -> Color.Red
                                isSelected -> IndigoPurple
                                else -> Color.Transparent
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectQuizAnswer(option) },
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CharcoalTextDark
                                    )
                                    
                                    if (checked) {
                                        if (isCorrect) {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Correct", tint = EmeraldGreen)
                                        } else if (isSelected) {
                                            Icon(imageVector = Icons.Default.Cancel, contentDescription = "Incorrect", tint = Color.Red)
                                        }
                                    } else {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectQuizAnswer(option) },
                                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPurple)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Grammar Tip overlay on incorrect checking
                    if (checked) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedAnswer == currentQuestion.correctAnswer) EmeraldGreen.copy(alpha = 0.05f) else AmberGold.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedAnswer == currentQuestion.correctAnswer) Icons.Default.CheckCircle else Icons.Default.Lightbulb,
                                    contentDescription = "Feedback",
                                    tint = if (selectedAnswer == currentQuestion.correctAnswer) EmeraldGreen else AmberGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (selectedAnswer == currentQuestion.correctAnswer) "Excellent! Correct Answer." else "Grammar & Learning Tip:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedAnswer == currentQuestion.correctAnswer) EmeraldDark else AmberGold
                                    )
                                    Text(
                                        text = currentQuestion.grammarTip,
                                        fontSize = 12.sp,
                                        color = CharcoalTextDark,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Row
                    val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!checked) {
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        viewModel.stopVoiceRecognition()
                                    } else {
                                        viewModel.startVoiceRecognition(
                                            contextType = "QUIZ",
                                            languageName = selectedLang,
                                            onTextUpdate = { recognized ->
                                                val bestOption = currentQuestion.options.find { option ->
                                                    option.equals(recognized, ignoreCase = true) ||
                                                    option.lowercase().contains(recognized.lowercase()) ||
                                                    recognized.lowercase().contains(option.lowercase())
                                                }
                                                if (bestOption != null) {
                                                    viewModel.selectQuizAnswer(bestOption)
                                                }
                                            },
                                            onComplete = { finalSpeech ->
                                                val bestOption = currentQuestion.options.find { option ->
                                                    option.equals(finalSpeech, ignoreCase = true) ||
                                                    option.lowercase().contains(finalSpeech.lowercase()) ||
                                                    finalSpeech.lowercase().contains(option.lowercase())
                                                } ?: currentQuestion.options.firstOrNull { option ->
                                                    option.lowercase().replace(" ", "").contains(finalSpeech.lowercase().replace(" ", ""))
                                                }
                                                if (bestOption != null) {
                                                    viewModel.selectQuizAnswer(bestOption)
                                                    viewModel.checkQuizAnswer()
                                                }
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(if (isListening) Color.Red else EmeraldGreen, RoundedCornerShape(12.dp))
                                    .testTag("quiz_microphone_button")
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Answer using Voice",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (!checked) {
                                    viewModel.checkQuizAnswer()
                                } else {
                                    viewModel.nextQuizQuestion()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("quiz_action_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedAnswer != null || isListening
                        ) {
                            Text(
                                text = if (isListening) "LISTENING..." else if (!checked) "CHECK ANSWER" else "NEXT QUESTION",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            // Loading state while questions are compiled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(color = EmeraldGreen)
                    Text(
                        text = when (activeSource) {
                            "CHAT" -> "Compiling adaptive quiz dynamically from your recent AI Tutor chat..."
                            "EXAM" -> "Generating 10-Question Comprehensive AI Exam..."
                            else -> "Generating new level-appropriate $selectedLang quiz..."
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = CharcoalTextDark.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
