package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToQuiz: () -> Unit,
    onNavigateToAr: () -> Unit,
    onNavigateToTutor: () -> Unit,
    onNavigateToPlanner: () -> Unit,
    onNavigateToSignLanguage: () -> Unit
) {
    val progress by viewModel.userProgress.collectAsStateWithLifecycle()
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val loggedInUserName by viewModel.loggedInUserName.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Top Greeting Header ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, $loggedInUserName! 👋",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Your $selectedLang Quest awaits today 🚀",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                // Language Selection Indicator Pill
                XPBadge(
                    text = selectedLang,
                    gradient = EmeraldGradient
                )
            }
        }

        // --- 2. Streaks, Level & Coins Gamified Banner ---
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level Info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Level",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Level ${progress.level}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Explorer Rank",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Streak Info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberGold.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = AmberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${progress.streak} Days",
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberGold,
                                fontSize = 13.sp
                            )
                        }

                        // Coins Info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coins",
                                tint = AmberLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${progress.coins}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // XP Progress bar
                    val xpNeeded = progress.level * 150
                    val ratio = progress.xp.toFloat() / xpNeeded.coerceAtLeast(1)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Level Progress",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${progress.xp} / ${xpNeeded} XP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        // --- 3. Weekly Practice Activity Chart (Compose Canvas) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Weekly XP Gains",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Draw a gorgeous Line Graph
                    val chartLogs = activityLogs.take(5).reversed()
                    val xpValues = if (chartLogs.isEmpty()) listOf(0f, 20f, 10f, 35f, 45f) else chartLogs.map { it.xpGained.toFloat() }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val maxVal = xpValues.maxOrNull()?.coerceAtLeast(50f) ?: 50f
                            
                            val points = xpValues.mapIndexed { idx, valXp ->
                                val x = (width / (xpValues.size - 1)) * idx
                                val y = height - (height * (valXp / maxVal))
                                Offset(x, y)
                            }

                            // Draw baseline grid
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.2f),
                                start = Offset(0f, height),
                                end = Offset(width, height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.2f),
                                start = Offset(0f, height / 2),
                                end = Offset(width, height / 2),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Draw gradient under curve
                            if (points.isNotEmpty()) {
                                val fillPath = Path().apply {
                                    moveTo(points.first().x, height)
                                    points.forEach { lineTo(it.x, it.y) }
                                    lineTo(points.last().x, height)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            EmeraldGreen.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )

                                // Draw connecting lines
                                val strokePath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        lineTo(points[i].x, points[i].y)
                                    }
                                }
                                drawPath(
                                    path = strokePath,
                                    color = EmeraldGreen,
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                // Draw point dots
                                points.forEach { pt ->
                                    drawCircle(
                                        color = AmberGold,
                                        radius = 5.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = EmeraldGreen,
                                        radius = 3.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }
                    }

                    // X-Axis labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val labels = if (chartLogs.isEmpty()) listOf("Mon", "Tue", "Wed", "Thu", "Fri") else chartLogs.map {
                            when (it.type) {
                                "QUIZ" -> "Quiz"
                                "AR_SCAN" -> "AR Scan"
                                "OCR" -> "OCR"
                                "TUTOR" -> "Tutor"
                                "SPEAKING" -> "Speech"
                                else -> "Study"
                            }
                        }
                        labels.forEach { label ->
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedTextDark,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Learning Consistency Heatmap (GitHub Grid) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Language Heatmap ( study streaks )",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Consistency builds compounding memory retention.",
                        fontSize = 11.sp,
                        color = MutedTextDark
                    )

                    // Render 7 columns x 4 rows block of grid cells representing past days
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until 7) {
                                    // Generate mock intensities or map based on actual log count
                                    val cellIndex = row * 7 + col
                                    val cellColor = when {
                                        cellIndex == 27 -> EmeraldGreen // today
                                        cellIndex % 5 == 0 -> EmeraldGreen.copy(alpha = 0.7f)
                                        cellIndex % 3 == 0 -> EmeraldGreen.copy(alpha = 0.3f)
                                        cellIndex % 7 == 0 -> AmberGold.copy(alpha = 0.5f)
                                        else -> Color.Gray.copy(alpha = 0.15f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 38.dp, height = 18.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(cellColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4b. AI Pronunciation Progress Over Time ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Pronunciation Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = IndigoPurple
                        )
                    }

                    val practices by viewModel.allPractices.collectAsStateWithLifecycle()
                    if (practices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateBackgroundLight, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("No pronunciation exercises yet.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("Speak words in 'My Words' to unlock stats!", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val averageScore = practices.map { it.score }.average().toInt()
                        val levelColor = when {
                            averageScore >= 90 -> EmeraldGreen
                            averageScore >= 70 -> AmberGold
                            else -> Color.Red
                        }
                        val levelLabel = when {
                            averageScore >= 90 -> "Excellent"
                            averageScore >= 70 -> "Good"
                            else -> "Needs Work"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateBackgroundLight, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("AVERAGE AI ACCURACY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("$averageScore / 100", fontSize = 20.sp, fontWeight = FontWeight.Black, color = levelColor)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(levelColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(levelLabel.uppercase(), color = levelColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Text("Recent Pronunciation History", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            practices.take(3).forEach { practice ->
                                val scoreColor = when {
                                    practice.score >= 90 -> EmeraldGreen
                                    practice.score >= 70 -> AmberGold
                                    else -> Color.Red
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(practice.word, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CharcoalTextDark)
                                        val dateStr = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(practice.timestamp))
                                        Text(dateStr, fontSize = 9.sp, color = Color.Gray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${practice.score}%", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = scoreColor)
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(scoreColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4c. AI Learning Twin Card ---
        item {
            val progressVal = progress
            val twinProfile by viewModel.learningTwinProfile.collectAsStateWithLifecycle()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    IndigoPurple.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    .background(IndigoPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "AI Twin",
                                    tint = IndigoPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${loggedInUserName}'s AI Learning Twin",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Progress Mirroring: Active 👥",
                                    fontSize = 11.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Sync pulse
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "SYNCED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = EmeraldGreen
                            )
                        }
                    }

                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                    Text(
                        text = "\"Bonjour $loggedInUserName! I've analyzed our learning speed and pronunciation accuracy. We have a streak of ${progressVal.streak} days. I've compiled our digital profile predictions below!\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )

                    // --- Digital Learner Profile Dashboard (Feature 11) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI DIGITAL LEARNER METRICS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = IndigoPurple,
                            letterSpacing = 1.sp
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Col 1
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("EST. EXAM SCORE", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${twinProfile.estimatedExamScore}%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldGreen)
                            }
                            // Col 2
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("TIME TO MASTER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${twinProfile.timeToMasterMonths} Months", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CharcoalTextDark)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Col 1
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("VOCAB GROWTH", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(twinProfile.vocabularyGrowthRate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                            }
                            // Col 2
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("LEARNING SPEED", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(twinProfile.learningSpeedCategory, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Col 1
                            Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("PRONUNCIATION HIGHLIGHT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(twinProfile.pronunciationStrength, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                            }
                            // Col 2
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("REC. STUDY TIME", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${twinProfile.recommendedDailyMinutes} mins/day", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                onNavigateToTutor()
                                viewModel.sendTutorMessage("Hi! I want to chat with my AI Learning Twin companion. Give me a challenge!")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = IndigoPurple)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chitchat with Twin", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- FEATURE 13: AI SMART RECOMMENDATIONS CARD ---
        item {
            val recommendations by viewModel.smartRecommendations.collectAsStateWithLifecycle()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, "Recommendations", tint = IndigoPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "AI Smart Recommendations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Next logical study actions dynamically scheduled by your performance curves.",
                        fontSize = 11.sp,
                        color = MutedTextDark
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recommendations.forEach { rec ->
                            val badgeColor = if (rec.priority == "High") Color.Red else IndigoPurple
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SlateBackgroundLight)
                                    .clickable {
                                        when (rec.route) {
                                            "vision" -> onNavigateToAr()
                                            "practice" -> onNavigateToQuiz()
                                            "tutor" -> onNavigateToTutor()
                                            "planner" -> onNavigateToPlanner()
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(rec.priority, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                        Text(rec.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CharcoalTextDark)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(rec.description, fontSize = 11.sp, color = MutedTextDark)
                                }
                                Icon(Icons.Default.ArrowForwardIos, "Go", tint = IndigoPurple, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- FEATURE 6 & 7: AI MISTAKE PREDICTION & PREVENTIVE PRACTICE HUB ---
        item {
            val mistakes by viewModel.predictedMistakes.collectAsStateWithLifecycle()
            val exercises by viewModel.mistakePracticeExercises.collectAsStateWithLifecycle()
            
            var selectedExerciseIndex by remember { mutableStateOf(-1) }
            var selectedOption by remember { mutableStateOf("") }
            var isFeedbackCorrect by remember { mutableStateOf<Boolean?>(null) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingDown, "Mistakes Prediction", tint = Color.Red, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "AI Mistake Predictions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Frequently predicted errors based on diagnostic patterns and historical performance.",
                        fontSize = 11.sp,
                        color = MutedTextDark
                    )

                    // Predicted Mistakes List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        mistakes.forEach { mist ->
                            val badgeColor = when (mist.mistakeType) {
                                "Vocabulary confusion" -> AmberGold
                                "Grammar mismatch" -> IndigoPurple
                                else -> Color.Red
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = mist.wordOrPattern,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = CharcoalTextDark
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(mist.mistakeType.uppercase(), color = badgeColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(mist.description, fontSize = 11.sp, color = MutedTextDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Correction Advice: ${mist.correctionTip}",
                                    fontSize = 10.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                    // Dynamic Preventive Exercise Hub
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Preventive Practice Hub",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CharcoalTextDark
                            )
                            Button(
                                onClick = {
                                    viewModel.generateMistakeExercises()
                                    selectedExerciseIndex = 0
                                    selectedOption = ""
                                    isFeedbackCorrect = null
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate ⚡", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (exercises.isNotEmpty() && selectedExerciseIndex in exercises.indices) {
                            val activeEx = exercises[selectedExerciseIndex]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "EXERCISE ${selectedExerciseIndex + 1}/${exercises.size}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = IndigoPurple
                                    )
                                    
                                    Text(
                                        text = activeEx.prompt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CharcoalTextDark
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        activeEx.options.forEach { opt ->
                                            val isSelected = selectedOption == opt
                                            val btnBg = if (isSelected) IndigoPurple.copy(alpha = 0.15f) else Color.White
                                            val btnBorder = if (isSelected) IndigoPurple else Color.LightGray.copy(alpha = 0.4f)
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(btnBg)
                                                    .border(1.dp, btnBorder, RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !activeEx.isSolved) {
                                                        selectedOption = opt
                                                    }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { if (!activeEx.isSolved) selectedOption = opt },
                                                    colors = RadioButtonDefaults.colors(selectedColor = IndigoPurple),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(opt, fontSize = 12.sp, color = CharcoalTextDark)
                                            }
                                        }
                                    }

                                    if (!activeEx.isSolved) {
                                        Button(
                                            onClick = {
                                                if (selectedOption.isNotEmpty()) {
                                                    val res = viewModel.solveMistakeExercise(activeEx.id, selectedOption)
                                                    isFeedbackCorrect = res
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple),
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = selectedOption.isNotEmpty()
                                        ) {
                                            Text("Submit Answer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        val isCorrect = activeEx.isCorrect == true
                                        val fbColor = if (isCorrect) EmeraldGreen else Color.Red
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(fbColor.copy(alpha = 0.08f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (isCorrect) "Correct! +15 XP 👑" else "Incorrect!",
                                                fontWeight = FontWeight.Bold,
                                                color = fbColor,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = activeEx.explanation,
                                                fontSize = 11.sp,
                                                color = CharcoalTextDark
                                            )

                                            if (selectedExerciseIndex < exercises.size - 1) {
                                                TextButton(
                                                    onClick = {
                                                        selectedExerciseIndex++
                                                        selectedOption = ""
                                                        isFeedbackCorrect = null
                                                    },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) {
                                                    Text("Next Question", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                                                }
                                            } else {
                                                Text(
                                                    "All preventive exercises completed!",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldGreen,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateBackgroundLight, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Generate custom exercises to train against your predicted errors.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Quick Actions Section ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Today's Missions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Continue Learning Row Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAr() }
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Scan Objects",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Launch AR Object Scan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Scan real-world items in $selectedLang",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Daily Challenge (Start Quiz)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToQuiz() }
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberGold.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = "Quiz Challenge",
                                tint = AmberGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily Adaptive Quiz",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AmberGold
                                )
                                Text(
                                    text = "Earn bonus coins with perfect answers",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Chat with AI Tutor
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTutor() }
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoPurple.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "AI Tutor",
                                tint = IndigoPurple,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Conversation Practice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = IndigoPurple
                                )
                                Text(
                                    text = "Chat about grammar & vocabulary recommendations",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = IndigoPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // AI Study Planner Row Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlanner() }
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "AI Study Planner",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "My Personal AI Study Plan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = EmeraldGreen
                                )
                                Text(
                                    text = "See goals, weak topic drills, and checklists",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Sign Language Translator Row Button (Feature 15)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSignLanguage() }
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .testTag("sign_language_menu_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoPurple.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Accessibility,
                                contentDescription = "Sign Language Translator",
                                tint = IndigoPurple,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sign Language Translator",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = IndigoPurple
                                )
                                Text(
                                    text = "Convert text and speech into visual sign language",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = IndigoPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- 6. Unlocked Achievements Badges Grid ---
        item {
            val badges by viewModel.achievementBadges.collectAsStateWithLifecycle()
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Achievement Badges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    badges.forEach { badge ->
                        val alpha = if (badge.isUnlocked) 1.0f else 0.4f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, Color.LightGray.copy(alpha = if (badge.isUnlocked) 0.5f else 0.2f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = badge.iconEmoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier.alpha(alpha)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = badge.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    color = CharcoalTextDark.copy(alpha = alpha)
                                )
                                Text(
                                    text = if (badge.isUnlocked) "Unlocked" else "Locked",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (badge.isUnlocked) EmeraldGreen else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
