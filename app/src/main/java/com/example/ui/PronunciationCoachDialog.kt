package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@Composable
fun PronunciationCoachDialog(
    word: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val score by viewModel.pronunciationScore.collectAsStateWithLifecycle()
    val accuracy by viewModel.pronunciationAccuracy.collectAsStateWithLifecycle()
    val correctSounds by viewModel.correctSounds.collectAsStateWithLifecycle()
    val incorrectSounds by viewModel.incorrectSounds.collectAsStateWithLifecycle()
    val suggestions by viewModel.pronunciationSuggestions.collectAsStateWithLifecycle()
    val feedback by viewModel.pronunciationFeedback.collectAsStateWithLifecycle()
    val waveform by viewModel.speakingWaveform.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingFirebase.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = { if (!isRecording) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("pronunciation_coach_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with accent
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Pronunciation Coach",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = CharcoalTextDark
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRecording,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Divider(color = Color.LightGray.copy(alpha = 0.3f))

                // Target word section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SPEAK THIS WORD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = word,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = EmeraldDark,
                        textAlign = TextAlign.Center
                    )
                    
                    // TTS Listen Again Button
                    Button(
                        onClick = { viewModel.speakText(word, selectedLanguage) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Listen Aloud",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Listen Again",
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Waveform animation or score display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SlateBackgroundLight)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // Waveform animation
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.height(50.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (waveform.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(8.dp)
                                            .background(EmeraldGreen, CircleShape)
                                    )
                                } else {
                                    waveform.forEach { amplitude ->
                                        val height = (amplitude * 45).coerceAtLeast(8f).dp
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .width(4.dp)
                                                .height(height)
                                                .background(EmeraldGreen, CircleShape)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Recording...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    } else if (score != null) {
                        // Score Circle & Feedback Accent
                        val scoreVal = score ?: 0
                        val color = when {
                            scoreVal >= 90 -> EmeraldGreen
                            scoreVal >= 70 -> AmberGold
                            else -> Color.Red
                        }
                        val emoji = when {
                            scoreVal >= 90 -> "🟢"
                            scoreVal >= 70 -> "🟡"
                            else -> "🔴"
                        }
                        val label = when {
                            scoreVal >= 90 -> "Excellent"
                            scoreVal >= 70 -> "Good"
                            else -> "Needs Improvement"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(color.copy(alpha = 0.1f), CircleShape)
                                    .border(4.dp, color, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$scoreVal",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        color = color
                                    )
                                    Text(
                                        text = "SCORE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = emoji, fontSize = 16.sp)
                                    Text(
                                        text = label.uppercase(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = color,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "Accuracy: $accuracy%",
                                    fontWeight = FontWeight.Bold,
                                    color = CharcoalTextDark,
                                    fontSize = 13.sp
                                )
                                if (isSyncing) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = IndigoPurple)
                                        Text("Syncing to Firebase...", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(12.dp))
                                        Text("Synced with Firebase Firestore", fontSize = 9.sp, color = IndigoPurple, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty/Idle State
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Press 'RECORD' and pronounce the word clearly.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Sounds breakdown tags if score exists
                if (score != null && !isRecording) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PHONETIC ACCURACY ANALYSIS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Correct Sounds Column
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Correct Sounds 🟢", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    correctSounds.forEach { sound ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(EmeraldGreen.copy(alpha = 0.1f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(sound, color = EmeraldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Incorrect Sounds Column
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Needs Practice 🔴", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (incorrectSounds.isEmpty()) {
                                        Text("None! Perfect", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    } else {
                                        incorrectSounds.forEach { sound ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Red.copy(alpha = 0.1f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(sound, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AI Suggestions box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndigoPurple.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(14.dp))
                                    Text("AI Suggestion:", fontWeight = FontWeight.ExtraBold, color = IndigoPurple, fontSize = 11.sp)
                                }
                                Text(
                                    text = suggestions,
                                    fontSize = 11.sp,
                                    color = CharcoalTextDark,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Control Action Buttons
                if (isRecording) {
                    Button(
                        onClick = { viewModel.stopPronunciationPractice(word) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stop_recording_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                            Text("FINISH SPEAKING", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startPronunciationPractice() },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("start_recording_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic")
                                Text(if (score == null) "TAP TO SPEAK" else "RETRY PRACTICE", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (score != null) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("DONE", fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + 12
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var totalHeight = 0
        rows.forEachIndexed { index, row ->
            val rowHeight = row.maxOfOrNull { it.height } ?: 0
            totalHeight += rowHeight
            if (index < rows.size - 1) {
                totalHeight += 8
            }
        }

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + 12
                }
                y += rowHeight + 8
            }
        }
    }
}
