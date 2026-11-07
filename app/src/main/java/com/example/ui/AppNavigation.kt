package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed class AppTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : AppTab("home", "Home", Icons.Default.Home)
    object Vision : AppTab("vision", "AR Vision", Icons.Default.Visibility)
    object Tutor : AppTab("tutor", "AI Tutor", Icons.Default.Forum)
    object Practice : AppTab("practice", "Quiz & Speak", Icons.Default.Quiz)
    object Words : AppTab("words", "My Words", Icons.Default.MenuBook)
    object Planner : AppTab("planner", "Planner", Icons.Default.CalendarToday)
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    if (!isLoggedIn) {
        LoginScreen(viewModel = viewModel)
    } else {
        var currentTab by remember { mutableStateOf<AppTab>(AppTab.Home) }
        var showSettingsModal by remember { mutableStateOf(false) }
        var currentSubScreen by remember { mutableStateOf<String?>(null) }

        val requestedRoute by viewModel.requestedRoute.collectAsStateWithLifecycle()
        LaunchedEffect(requestedRoute) {
            requestedRoute?.let { route ->
                val matchingTab = listOf(AppTab.Home, AppTab.Vision, AppTab.Tutor, AppTab.Practice, AppTab.Words, AppTab.Planner)
                    .find { it.route == route }
                if (matchingTab != null) {
                    currentTab = matchingTab
                }
                viewModel.clearRequestedRoute()
            }
        }

        LaunchedEffect(currentTab) {
            viewModel.stopSpeaking()
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    viewModel.stopSpeaking()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stopSpeaking()
            }
        }

        // Sliding selection state inside "Vision" Tab (0 = AR Object Scanner, 1 = OCR Doc Scanner)
        var visionActiveMode by remember { mutableStateOf(0) }
        
        // Sliding selection state inside "Practice" Tab (0 = Speaking/Translation, 1 = Quiz Arena)
        var practiceActiveMode by remember { mutableStateOf(0) }

        Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_app_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing, // Standard safe system notch drawing insets
        topBar = {
            // Elegant brand top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ARLingua",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                color = CharcoalTextDark
                            )
                        )
                    }

                    // Settings trigger icon
                    IconButton(
                        onClick = { showSettingsModal = !showSettingsModal },
                        modifier = Modifier
                            .size(36.dp)
                            .background(SlateBackgroundLight, CircleShape)
                            .testTag("settings_top_button")
                    ) {
                        Icon(
                            imageVector = if (showSettingsModal) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CharcoalTextDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Material 3 bottom navigation bar with active indicators
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars) // Respect system bottom gesture pill overlay padding
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                val tabs = listOf(AppTab.Home, AppTab.Vision, AppTab.Tutor, AppTab.Practice, AppTab.Words, AppTab.Planner)
                tabs.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            currentTab = tab
                            showSettingsModal = false
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) EmeraldGreen else Color.Gray
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (isSelected) EmeraldDark else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = EmeraldGreen.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SlateBackgroundLight)
        ) {
            if (showSettingsModal) {
                SettingsScreen(viewModel = viewModel)
            } else if (currentSubScreen == "sign_language") {
                SignLanguageScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = null }
                )
            } else {
                when (currentTab) {
                    AppTab.Home -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToQuiz = {
                                currentTab = AppTab.Practice
                                practiceActiveMode = 1
                            },
                            onNavigateToAr = {
                                currentTab = AppTab.Vision
                                visionActiveMode = 0
                            },
                            onNavigateToTutor = {
                                currentTab = AppTab.Tutor
                            },
                            onNavigateToPlanner = {
                                currentTab = AppTab.Planner
                            },
                            onNavigateToSignLanguage = {
                                currentSubScreen = "sign_language"
                            }
                        )
                    }

                    AppTab.Vision -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sub Tab bar for Vision Tab (Object Scanner vs Document OCR)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateBackgroundDark)
                                    .padding(vertical = 4.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (visionActiveMode == 0) EmeraldGreen.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { visionActiveMode = 0 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "OBJECT SCANNER",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (visionActiveMode == 0) EmeraldLight else Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (visionActiveMode == 1) EmeraldGreen.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { visionActiveMode = 1 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "DOCUMENT OCR",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (visionActiveMode == 1) EmeraldLight else Color.Gray
                                    )
                                }
                            }

                            if (visionActiveMode == 0) {
                                ArScannerScreen(viewModel = viewModel)
                            } else {
                                OcrScannerScreen(viewModel = viewModel)
                            }
                        }
                    }

                    AppTab.Tutor -> {
                        AiTutorScreen(viewModel = viewModel)
                    }

                    AppTab.Practice -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sub Tab bar for Practice Tab (Pronunciation vs Adaptive Quiz)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 4.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (practiceActiveMode == 0) EmeraldGreen.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable { practiceActiveMode = 0 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TRANSLATE & SPEAK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (practiceActiveMode == 0) EmeraldDark else Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (practiceActiveMode == 1) EmeraldGreen.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable { practiceActiveMode = 1 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ADAPTIVE QUIZ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (practiceActiveMode == 1) EmeraldDark else Color.Gray
                                    )
                                }
                            }

                            if (practiceActiveMode == 0) {
                                TranslationScreen(viewModel = viewModel)
                            } else {
                                QuizScreen(viewModel = viewModel)
                            }
                        }
                    }

                    AppTab.Words -> {
                        VocabularyScreen(viewModel = viewModel)
                    }

                    AppTab.Planner -> {
                        StudyPlannerScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
}
