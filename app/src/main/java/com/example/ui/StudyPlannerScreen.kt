package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LearnedWord
import com.example.data.PlannerTask
import com.example.ui.theme.*

/**
 * AI-Powered Personalized Learning Planner & Analytics Hub.
 * 
 * Features integrated:
 * 1. AI Personalized Daily Learning Plan
 * 2. Smart AR Daily Missions
 * 3. Adaptive Vocabulary Review (Spaced Repetition)
 * 4. Weekly Learning Calendar
 * 5. Learning Analytics Dashboard
 * 6. Custom Planner Task Manager & Reminders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlannerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val userName by viewModel.loggedInUserName.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val userProgress by viewModel.userProgress.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingPlanner.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingFirebase.collectAsStateWithLifecycle()

    // 1. Daily Plan Tasks State
    val dailyPlanTasks by viewModel.dailyPlanTasks.collectAsStateWithLifecycle()

    // 2. AR Missions State
    val arMissions by viewModel.arMissions.collectAsStateWithLifecycle()

    // 3. Spaced Repetition Words State
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()

    // 4. Weekly Schedule State
    val weeklySchedule by viewModel.weeklySchedule.collectAsStateWithLifecycle()

    // 5. Custom Manual Tasks State
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    // Analytics Data
    val quizResults by viewModel.quizResults.collectAsStateWithLifecycle()
    val allPractices by viewModel.allPractices.collectAsStateWithLifecycle()
    val allMistakes by viewModel.allGrammarMistakes.collectAsStateWithLifecycle()

    // UI Local Filter & Dialog State
    var activeCategoryFilter by remember { mutableStateOf("All") }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var isReminderEnabled by remember { mutableStateOf(true) }
    var selectedWeeklyDayForModal by remember { mutableStateOf<WeeklyDaySchedule?>(null) }

    // Manual task builder inputs
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskCategory by remember { mutableStateOf("Vocabulary") }
    var newTaskPriority by remember { mutableStateOf("Medium") }
    var newTaskLesson by remember { mutableStateOf("") }
    var newTaskMinutes by remember { mutableStateOf("15") }

    val categories = listOf("All", "Vocabulary", "Quiz", "Pronunciation", "Revision")
    val filteredManualTasks = if (activeCategoryFilter == "All") {
        allTasks
    } else {
        allTasks.filter { it.category.equals(activeCategoryFilter, ignoreCase = true) }
    }

    // Daily Plan completion stats
    val dailyPlanCompletedCount = dailyPlanTasks.count { it.isCompleted }
    val dailyPlanTotalCount = dailyPlanTasks.size
    val dailyPlanProgressPercent = if (dailyPlanTotalCount > 0) (dailyPlanCompletedCount.toFloat() / dailyPlanTotalCount.toFloat()) else 0f

    // AR Mission stats
    val arMissionsCompletedCount = arMissions.count { it.isCompleted }
    val arMissionsTotalCount = arMissions.size

    // Spaced Repetition Words List (Priority: lowest accuracy or oldest reviewed)
    val reviewWords = remember(allWords) {
        if (allWords.isNotEmpty()) {
            allWords.sortedBy { it.accuracyScore }.take(5)
        } else {
            // Default sample vocabulary items if database has no words yet
            listOf(
                LearnedWord(id = 1, word = "Laptop", translation = "Portátil", ipa = "/ˈlæptɒp/", exampleSentence = "I work on my laptop.", exampleTranslation = "Trabajo en mi portátil.", difficulty = "Beginner", accuracyScore = 60, category = "Electronics", language = selectedLanguage),
                LearnedWord(id = 2, word = "Keyboard", translation = "Teclado", ipa = "/ˈkiːbɔːd/", exampleSentence = "The keyboard is clean.", exampleTranslation = "El teclado está limpio.", difficulty = "Beginner", accuracyScore = 50, category = "Electronics", language = selectedLanguage),
                LearnedWord(id = 3, word = "Bottle", translation = "Botella", ipa = "/ˈbɒtl/", exampleSentence = "Pass me the water bottle.", exampleTranslation = "Pásame la botella de agua.", difficulty = "Beginner", accuracyScore = 70, category = "Kitchen", language = selectedLanguage),
                LearnedWord(id = 4, word = "Window", translation = "Ventana", ipa = "/ˈwɪndoʊ/", exampleSentence = "Open the window.", exampleTranslation = "Abre la ventana.", difficulty = "Beginner", accuracyScore = 40, category = "Home", language = selectedLanguage),
                LearnedWord(id = 5, word = "Notebook", translation = "Cuaderno", ipa = "/ˈnoʊtbʊk/", exampleSentence = "Write in your notebook.", exampleTranslation = "Escribe en tu cuaderno.", difficulty = "Beginner", accuracyScore = 65, category = "Office", language = selectedLanguage)
            )
        }
    }

    val todayDayName = remember { getCurrentDayOfWeekName() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("study_planner_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Custom Task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SlateBackgroundLight),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- SECTION 1: HEADER & GREETING ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = getGreetingText(userName),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CharcoalTextDark
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IndigoPurple.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$selectedLanguage AI Coordinator",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPurple
                                )
                            }
                            Text(
                                text = "• Streak: ${userProgress.streak} Days 🔥",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold
                            )
                        }
                    }

                    // Firebase Sync Indicator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSyncing) IndigoPurple.copy(alpha = 0.1f) else EmeraldGreen.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = IndigoPurple)
                                Text("Syncing", fontSize = 10.sp, color = IndigoPurple, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                Text("Synced", fontSize = 10.sp, color = EmeraldDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- FEATURE 1: AI PERSONALIZED DAILY LEARNING PLAN ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        IndigoPurple,
                                        IndigoLight
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Today's Learning Plan",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${(dailyPlanProgressPercent * 100).toInt()}% Done",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { dailyPlanProgressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = AmberGold,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )

                            // Interactive Checklist Items
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dailyPlanTasks.forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (task.isCompleted) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                                            )
                                            .clickable { viewModel.toggleDailyPlanTask(task.id) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(if (task.isCompleted) AmberGold else Color.White.copy(alpha = 0.3f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (task.isCompleted) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Completed", tint = CharcoalTextDark, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(
                                                text = task.title,
                                                color = Color.White,
                                                fontWeight = if (task.isCompleted) FontWeight.Medium else FontWeight.Bold,
                                                fontSize = 13.sp,
                                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AmberGold.copy(alpha = 0.25f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("+${task.xpReward} XP", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = AmberGold)
                                        }
                                    }
                                }
                            }

                            // Re-generate Plan with Gemini Button
                            Button(
                                onClick = {
                                    viewModel.generatePlannerTasks()
                                    Toast.makeText(context, "AI customized your plan based on streak & performance!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("generate_ai_plan_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isGenerating
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CharcoalTextDark)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing performance with Gemini...", color = CharcoalTextDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CharcoalTextDark, modifier = Modifier.size(16.dp))
                                        Text("RE-GENERATE AI LEARNING PLAN", color = CharcoalTextDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- FEATURE 2: SMART AR DAILY MISSIONS ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Today's AR Challenge", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CharcoalTextDark)
                                    Text("Scan real objects to complete missions", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Text(
                                text = "$arMissionsCompletedCount/$arMissionsTotalCount Cleared",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = EmeraldGreen
                            )
                        }

                        // Rewards Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldGreen.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.MilitaryTech, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                Text("Mission Reward:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldDark)
                            }
                            Text("+100 XP • +50 Coins • Object Master Badge", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = EmeraldDark)
                        }

                        // Mission Item List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            arMissions.forEach { mission ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, if (mission.isCompleted) EmeraldGreen else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .background(if (mission.isCompleted) EmeraldGreen.copy(alpha = 0.08f) else SlateBackgroundLight)
                                        .clickable { viewModel.toggleArMissionItem(mission.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(if (mission.isCompleted) EmeraldGreen else Color.LightGray.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (mission.isCompleted) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Text(
                                            text = "Scan: ${mission.objectNameEn}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (mission.isCompleted) EmeraldDark else CharcoalTextDark
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (mission.isCompleted) {
                                            Text("✓ Scanned", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldGreen)
                                        } else {
                                            Text("+${mission.xpReward} XP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndigoPurple)
                                        }
                                    }
                                }
                            }
                        }

                        // Action Buttons: Open AR Scanner & Generate New Missions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.requestRouteSwitch("vision") },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OPEN AR CAMERA", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.generateNewArMissions() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPurple)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Missions", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // --- FEATURE 3: ADAPTIVE VOCABULARY REVIEW (SPACED REPETITION) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AmberGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Spaced Repetition Review", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CharcoalTextDark)
                                    Text("Prioritizing forgotten & low-accuracy words", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Button(
                                onClick = { viewModel.requestRouteSwitch("words") },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("View All", color = IndigoPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        // Vocabulary Cards
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reviewWords.forEach { word ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = word.word,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 15.sp,
                                                    color = CharcoalTextDark
                                                )
                                                Text(
                                                    text = word.ipa,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(
                                                text = "Translation: ${word.translation}",
                                                fontSize = 12.sp,
                                                color = EmeraldDark,
                                                fontWeight = FontWeight.Bold
                                            )

                                            // Mastery Percentage Bar
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                LinearProgressIndicator(
                                                    progress = { (word.accuracyScore / 100f).coerceIn(0.1f, 1f) },
                                                    modifier = Modifier
                                                        .width(80.dp)
                                                        .height(6.dp)
                                                        .clip(CircleShape),
                                                    color = if (word.accuracyScore >= 70) EmeraldGreen else AmberGold,
                                                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                                                )
                                                Text(
                                                    text = "${word.accuracyScore}% Mastery",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Play TTS Audio button
                                            IconButton(
                                                onClick = { viewModel.speakText(word.word, selectedLanguage) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Pronounce", tint = IndigoPurple, modifier = Modifier.size(20.dp))
                                            }

                                            // Mark Reviewed Button
                                            Button(
                                                onClick = {
                                                    viewModel.markSpacedRepetitionWordReviewed(word.id)
                                                    Toast.makeText(context, "Marked '${word.word}' as reviewed (+15% Mastery)", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Reviewed", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- FEATURE 4: WEEKLY LEARNING CALENDAR ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(IndigoPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Weekly Learning Calendar", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CharcoalTextDark)
                                    Text("Tap a day to launch scheduled exercise", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Text(
                                text = "Today: $todayDayName",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = IndigoPurple
                            )
                        }

                        // Horizontal Day Cards Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(weeklySchedule) { day ->
                                val isToday = day.dayName.equals(todayDayName, ignoreCase = true)
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable { selectedWeeklyDayForModal = day },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isToday) IndigoPurple else if (day.isCompleted) EmeraldGreen.copy(alpha = 0.15f) else SlateBackgroundLight
                                    ),
                                    border = if (isToday) null else borderIfCompleted(day.isCompleted)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = day.dayShort,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = if (isToday) Color.White else Color.Gray
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isToday) AmberGold else if (day.isCompleted) EmeraldGreen else Color.LightGray.copy(alpha = 0.3f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (day.isCompleted) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            } else {
                                                Icon(
                                                    imageVector = getIconForTopic(day.topic),
                                                    contentDescription = null,
                                                    tint = if (isToday) CharcoalTextDark else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = day.topic,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = if (isToday) Color.White else CharcoalTextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- FEATURE 5: LEARNING ANALYTICS DASHBOARD ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AmberGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Learning Analytics Dashboard", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CharcoalTextDark)
                                    Text("Performance breakdown & mastery metrics", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Grid of 4 Metric Badges
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnalyticsMetricCard(
                                    title = "CEFR Level & XP",
                                    value = "Lvl ${userProgress.level} (${userProgress.xp} XP)",
                                    subtitle = "Top 10% Learners",
                                    icon = Icons.Default.School,
                                    color = IndigoPurple,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsMetricCard(
                                    title = "Vocabulary Mastery",
                                    value = "${allWords.size} Words",
                                    subtitle = "${allWords.count { it.accuracyScore >= 70 }} Mastered",
                                    icon = Icons.Default.MenuBook,
                                    color = EmeraldGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val quizAvg = if (quizResults.isNotEmpty()) {
                                    (quizResults.sumOf { it.score }.toFloat() / quizResults.sumOf { it.totalQuestions.coerceAtLeast(1) } * 100).toInt()
                                } else 92
                                AnalyticsMetricCard(
                                    title = "Quiz Accuracy",
                                    value = "$quizAvg%",
                                    subtitle = "${quizResults.size} Quizzes Completed",
                                    icon = Icons.Default.Quiz,
                                    color = AmberGold,
                                    modifier = Modifier.weight(1f)
                                )

                                val speechAvg = if (allPractices.isNotEmpty()) {
                                    allPractices.map { it.score }.average().toInt()
                                } else 88
                                AnalyticsMetricCard(
                                    title = "Pronunciation Score",
                                    value = "$speechAvg%",
                                    subtitle = "${allPractices.size} Speaking Sessions",
                                    icon = Icons.Default.Mic,
                                    color = IndigoPurple,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Custom Weekly Practice Bar Chart
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateBackgroundLight)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("WEEKLY PRACTICE ACTIVITY (MINUTES)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
                            
                            val sampleActivity = listOf("M" to 25, "T" to 40, "W" to 30, "T" to 50, "F" to 35, "S" to 60, "S" to 20)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                sampleActivity.forEach { (day, minutes) ->
                                    val heightFraction = (minutes / 60f).coerceIn(0.15f, 1f)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .fillMaxHeight(heightFraction)
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(if (minutes >= 40) EmeraldGreen else IndigoPurple)
                                        )
                                        Text(day, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 6: MANUAL CUSTOM TASK LIST & NOTIFICATIONS ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Custom Study Tasks", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CharcoalTextDark)
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (isReminderEnabled) "Reminders On" else "Off", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isReminderEnabled) EmeraldDark else Color.Gray)
                                Switch(
                                    checked = isReminderEnabled,
                                    onCheckedChange = {
                                        isReminderEnabled = it
                                        if (it) {
                                            Toast.makeText(context, "Study push notifications active!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.2f)),
                                    modifier = Modifier.scale(0.6f)
                                )
                            }
                        }

                        // Category Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSel = activeCategoryFilter == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) EmeraldGreen else SlateBackgroundLight)
                                        .clickable { activeCategoryFilter = cat }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = cat.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSel) Color.White else Color.Gray
                                    )
                                }
                            }
                        }

                        if (filteredManualTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No custom tasks in $activeCategoryFilter. Tap '+' to create one.", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredManualTasks.forEach { task ->
                                    PlannerTaskRow(
                                        task = task,
                                        onToggle = { viewModel.toggleTaskCompleted(task) },
                                        onDelete = { viewModel.deletePlannerTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DAY DETAIL MODAL FOR WEEKLY CALENDAR ---
    selectedWeeklyDayForModal?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedWeeklyDayForModal = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(IndigoPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = getIconForTopic(day.topic), contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(18.dp))
                    }
                    Text("${day.dayName}: ${day.topic}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(day.detailDescription, fontSize = 13.sp, color = CharcoalTextDark, lineHeight = 18.sp)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateBackgroundLight)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = if (day.isCompleted) "✓ Completed (+15 XP)" else "Pending",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = if (day.isCompleted) EmeraldGreen else AmberGold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetRoute = day.routeTarget
                        selectedWeeklyDayForModal = null
                        viewModel.requestRouteSwitch(targetRoute)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LAUNCH EXERCISE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleWeeklyDayCompleted(day.dayName)
                        selectedWeeklyDayForModal = null
                    }
                ) {
                    Text(if (day.isCompleted) "Mark Incomplete" else "Mark Complete", color = IndigoPurple, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- ADD CUSTOM TASK DIALOG ---
    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.EditCalendar, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Lesson Task", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        label = { Text("Task Title (e.g., Speak 5 phrases)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppTextFieldDefaults.outlinedColors()
                    )

                    OutlinedTextField(
                        value = newTaskLesson,
                        onValueChange = { newTaskLesson = it },
                        label = { Text("Lesson Reference (e.g., Cafe Scanner)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppTextFieldDefaults.outlinedColors()
                    )

                    OutlinedTextField(
                        value = newTaskMinutes,
                        onValueChange = { newTaskMinutes = it },
                        label = { Text("Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppTextFieldDefaults.outlinedColors()
                    )

                    // Category Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Category", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Vocabulary", "Quiz", "Pronunciation", "Revision").forEach { cat ->
                                val isSel = newTaskCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) EmeraldGreen else Color.LightGray.copy(alpha = 0.3f))
                                        .clickable { newTaskCategory = cat }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(cat, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else CharcoalTextDark)
                                }
                            }
                        }
                    }

                    // Priority Selection
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Priority", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("High", "Medium", "Low").forEach { pri ->
                                val isSel = newTaskPriority == pri
                                val color = when (pri) {
                                    "High" -> Color.Red
                                    "Medium" -> AmberGold
                                    else -> IndigoPurple
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) color else Color.LightGray.copy(alpha = 0.3f))
                                        .clickable { newTaskPriority = pri }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(pri, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSel) Color.White else CharcoalTextDark)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            val mins = newTaskMinutes.toIntOrNull() ?: 15
                            viewModel.addManualTask(
                                title = newTaskTitle,
                                category = newTaskCategory,
                                priority = newTaskPriority,
                                recommendedLesson = newTaskLesson.ifBlank { "Custom Practice" },
                                estimatedMinutes = mins
                            )
                            newTaskTitle = ""
                            newTaskLesson = ""
                            newTaskMinutes = "15"
                            showAddTaskDialog = false
                            Toast.makeText(context, "Added new study task!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Add Task", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun AnalyticsMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = CharcoalTextDark)
            Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
fun PlannerTaskRow(
    task: PlannerTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryIcon = when (task.category.lowercase()) {
        "vocabulary" -> Icons.Default.Visibility
        "quiz" -> Icons.Default.Quiz
        "pronunciation" -> Icons.Default.Mic
        else -> Icons.Default.Refresh
    }

    val priorityColor = when (task.priority.lowercase()) {
        "high" -> Color.Red
        "medium" -> AmberGold
        else -> IndigoPurple
    }

    val categoryColor = when (task.category.lowercase()) {
        "vocabulary" -> EmeraldGreen
        "quiz" -> AmberGold
        "pronunciation" -> IndigoPurple
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("planner_task_row_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) EmeraldGreen else Color.LightGray.copy(alpha = 0.3f))
                        .clickable { onToggle() }
                        .testTag("planner_task_checkbox_${task.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (task.isCompleted) Color.Gray else CharcoalTextDark,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = "${task.category} • Ref: ${task.recommendedLesson}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(priorityColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(task.priority.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = priorityColor)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete task", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Helpers
private fun getGreetingText(userName: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val timeOfDay = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
    val name = if (userName.isNotBlank()) userName else "Explorer"
    return "$timeOfDay, $name"
}

private fun getCurrentDayOfWeekName(): String {
    return when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Monday"
        java.util.Calendar.TUESDAY -> "Tuesday"
        java.util.Calendar.WEDNESDAY -> "Wednesday"
        java.util.Calendar.THURSDAY -> "Thursday"
        java.util.Calendar.FRIDAY -> "Friday"
        java.util.Calendar.SATURDAY -> "Saturday"
        java.util.Calendar.SUNDAY -> "Sunday"
        else -> "Monday"
    }
}

private fun getIconForTopic(topic: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (topic.lowercase()) {
        "vocabulary" -> Icons.Default.MenuBook
        "grammar" -> Icons.Default.Forum
        "speaking" -> Icons.Default.Mic
        "ar practice" -> Icons.Default.Visibility
        "ai tutor" -> Icons.Default.AutoAwesome
        "quiz" -> Icons.Default.Quiz
        else -> Icons.Default.Refresh
    }
}

private fun borderIfCompleted(isCompleted: Boolean): androidx.compose.foundation.BorderStroke? {
    return if (isCompleted) androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldGreen) else null
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(
        scaleX = scale,
        scaleY = scale
    )
)
