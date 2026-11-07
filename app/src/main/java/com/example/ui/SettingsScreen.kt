package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val loggedInUserName by viewModel.loggedInUserName.collectAsStateWithLifecycle()
    val loggedInUserEmail by viewModel.loggedInUserEmail.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val availableLanguages = listOf(
        "Spanish", "French", "Japanese", "Korean",
        "Tamil", "Hindi", "German", "Chinese", "English"
    )

    var speedSlider by remember { mutableFloatStateOf(viewModel.speechSpeed) }
    var isDarkTheme by remember { mutableStateOf(false) }

    // Offline packs download states mapping
    val packDownloads = remember {
        mutableStateMapOf(
            "Spanish" to "Downloaded",
            "French" to "Download",
            "Japanese" to "Download",
            "German" to "Download"
        )
    }
    val downloadProgress = remember { mutableStateMapOf<String, Float>() }

    fun downloadPack(lang: String) {
        scope.launch {
            packDownloads[lang] = "Downloading"
            downloadProgress[lang] = 0.0f
            // Simulate incremental loading progress bar
            for (progress in 1..10) {
                delay(300)
                downloadProgress[lang] = progress / 10f
            }
            packDownloads[lang] = "Downloaded"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundLight)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 0. Profile & Log Out block ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loggedInUserName.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = loggedInUserName,
                                fontWeight = FontWeight.ExtraBold,
                                color = CharcoalTextDark,
                                fontSize = 15.sp
                            )
                            Text(
                                text = loggedInUserEmail.ifEmpty { "Guest Session" },
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.logoutUser() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.08f), contentColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Log out", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 1. Target Language selection block ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = "Lang", tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active Study Language", fontWeight = FontWeight.ExtraBold, color = CharcoalTextDark, fontSize = 15.sp)
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.4f))

                    // Column layout grid of languages
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableLanguages.chunked(3).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunk.forEach { lang ->
                                    val isSelected = lang == selectedLang
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) EmeraldGreen else SlateBackgroundLight)
                                            .clickable { viewModel.setStudyLanguage(lang) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else CharcoalTextDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Audio Speak Speeds Sliders ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Audio settings", tint = IndigoPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio & Pronunciation Speed", fontWeight = FontWeight.ExtraBold, color = CharcoalTextDark, fontSize = 15.sp)
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Speed Rate", fontSize = 13.sp, color = CharcoalTextDark, fontWeight = FontWeight.Bold)
                        Text("${"%.1f".format(speedSlider)}x Speed", fontSize = 12.sp, color = IndigoPurple, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = speedSlider,
                        onValueChange = {
                            speedSlider = it
                            viewModel.speechSpeed = it
                        },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPurple,
                            activeTrackColor = IndigoPurple,
                            inactiveTrackColor = IndigoPurple.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )
                }
            }
        }

        // --- 3. Offline Pack Downloader Block ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Offline Packs", tint = AmberGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offline Translation Packs", fontWeight = FontWeight.ExtraBold, color = CharcoalTextDark, fontSize = 15.sp)
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.4f))

                    // List of offline package downloads
                    packDownloads.forEach { (lang, state) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "$lang Vocabulary Deck", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                                Text(
                                    text = when (lang) {
                                        "Spanish" -> "25 MB • Over 1,500 scans"
                                        "French" -> "18 MB • Over 1,100 scans"
                                        "Japanese" -> "42 MB • Over 2,300 scans"
                                        else -> "21 MB • Over 1,300 scans"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            when (state) {
                                "Downloaded" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Saved", fontSize = 12.sp, color = EmeraldDark, fontWeight = FontWeight.Bold)
                                    }
                                }
                                "Downloading" -> {
                                    val progress = downloadProgress[lang] ?: 0f
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        CircularProgressIndicator(
                                            color = AmberGold,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text("${(progress * 100).toInt()}%", fontSize = 10.sp, color = AmberGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> {
                                    IconButton(
                                        onClick = { downloadPack(lang) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(AmberGold.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download pack", tint = AmberGold, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (lang != packDownloads.keys.last()) {
                            Divider(color = Color.LightGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // --- 4. General Settings / Theme Toggles ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = "Theme", tint = CharcoalTextDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Visual Night Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { isDarkTheme = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "App Info", tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("ARLingua Learn Environment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                                Text("Build v1.0.4 Native Android", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Text("Licensed", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
