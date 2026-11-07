package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArScanResult
import com.example.service.RawDetectedObject
import com.example.service.TrackedArObject
import com.example.service.GeminiVerificationResult
import com.example.service.GeminiVerificationStatus
import com.example.ui.theme.*

/**
 * Production-Grade Tap-to-Identify AR Overlay Renderer with Gemini Vision Primary Verification & Debug HUD.
 * Highlights selected object and renders Canvas-based real-time MLKit bounding box overlays over camera preview.
 * Draws tap focus ripple animation, Gemini verification status, and developer debug overlay.
 */
@Composable
fun ArOverlayRenderer(
    modifier: Modifier = Modifier,
    selectedObject: TrackedArObject?,
    realtimeDetectedObjects: List<RawDetectedObject> = emptyList(),
    frameWidth: Int = 1080,
    frameHeight: Int = 1920,
    tapPoint: Offset?,
    isAnalyzingTap: Boolean,
    analyzingStatusText: String = "Analyzing tapped object...",
    geminiResult: GeminiVerificationResult? = null,
    isDebugOverlayVisible: Boolean = true,
    onToggleDebugOverlay: () -> Unit = {},
    noObjectFoundMessage: String?,
    isModelLoaded: Boolean,
    cameraError: String?,
    onSpeakText: (String) -> Unit,
    onDismissCard: () -> Unit,
    onRetryTap: () -> Unit = {},
    onSelectCandidate: (String) -> Unit = {},
    onPracticeSpeaking: (ArScanResult) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        // 0. Real-time MLKit Canvas Bounding Box Overlay Layer
        if (realtimeDetectedObjects.isNotEmpty()) {
            MlKitBoundingBoxOverlay(
                modifier = Modifier.fillMaxSize(),
                detectedObjects = realtimeDetectedObjects,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                selectedTrackingId = selectedObject?.id?.hashCode()
            )
        }

        // 1. Canvas Layer: Draw Focus Ripple at Tap Point & Single Bounding Box around Selected Object
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // A. Draw Tap Focus Ripple Animation
            tapPoint?.let { point ->
                drawCircle(
                    color = EmeraldLight,
                    radius = 24f,
                    center = point,
                    style = Stroke(width = 4f)
                )

                // Inner tap dot
                drawCircle(
                    color = EmeraldGreen,
                    radius = 8f,
                    center = point
                )

                // Crosshair tick marks
                val tickLen = 12f
                val gap = 16f
                drawLine(EmeraldLight, Offset(point.x - gap - tickLen, point.y), Offset(point.x - gap, point.y), strokeWidth = 3f)
                drawLine(EmeraldLight, Offset(point.x + gap, point.y), Offset(point.x + gap + tickLen, point.y), strokeWidth = 3f)
                drawLine(EmeraldLight, Offset(point.x, point.y - gap - tickLen), Offset(point.x, point.y - gap), strokeWidth = 3f)
                drawLine(EmeraldLight, Offset(point.x, point.y + gap), Offset(point.x, point.y + gap + tickLen), strokeWidth = 3f)
            }

            // B. Highlight ONLY the single selected object with ONE bounding box
            selectedObject?.let { tracked ->
                val rect = tracked.currentBounds
                val color = EmeraldLight
                val strokeW = 6f
                val cornerLength = (rect.width().coerceAtMost(rect.height()) * 0.25f).coerceIn(20f, 50f)

                drawRoundRect(
                    color = color.copy(alpha = 0.9f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width(), rect.height()),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
                )

                drawLine(color, Offset(rect.left, rect.top), Offset(rect.left + cornerLength, rect.top), strokeWidth = strokeW)
                drawLine(color, Offset(rect.left, rect.top), Offset(rect.left, rect.top + cornerLength), strokeWidth = strokeW)

                drawLine(color, Offset(rect.right, rect.top), Offset(rect.right - cornerLength, rect.top), strokeWidth = strokeW)
                drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.top + cornerLength), strokeWidth = strokeW)

                drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left + cornerLength, rect.bottom), strokeWidth = strokeW)
                drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - cornerLength), strokeWidth = strokeW)

                drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right - cornerLength, rect.bottom), strokeWidth = strokeW)
                drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom + cornerLength), strokeWidth = strokeW)
            }
        }

        // 2. Analyzing Tap Multi-Frame Verification Loading Overlay Indicator
        if (isAnalyzingTap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.90f))
                    .border(1.dp, EmeraldLight, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = EmeraldLight,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = analyzingStatusText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2.5 Developer Object Recognition Debug HUD (Step 2 Structured Format)
        if (geminiResult != null && isDebugOverlayVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 50.dp, end = 10.dp)
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.94f))
                    .border(1.dp, EmeraldLight.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OBJECT DETECTION DEBUG",
                            color = EmeraldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = onToggleDebugOverlay,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Debug", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }

                    HorizontalDivider(color = EmeraldLight.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 1.dp))

                    Text(text = "CAMERA FRAME:", color = AmberGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    geminiResult.capturedFrameBitmap?.let { fullBmp ->
                        Image(
                            bitmap = fullBmp.asImageBitmap(),
                            contentDescription = "Full Camera Frame",
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
                        )
                    }

                    Text(
                        text = "USER TAP:\nX: ${geminiResult.tapXScreen.toInt()}\nY: ${geminiResult.tapYScreen.toInt()}",
                        color = Color.White,
                        fontSize = 8.sp
                    )

                    Text(text = "SELECTED REGION / GEMINI INPUT:", color = AmberGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    geminiResult.cropBitmap?.let { cropBmp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                bitmap = cropBmp.asImageBitmap(),
                                contentDescription = "Selected Region Crop",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, EmeraldLight, RoundedCornerShape(4.dp))
                            )
                            Column {
                                Text(
                                    text = "IMAGE SIZE:\nWidth: ${geminiResult.imageWidth} px\nHeight: ${geminiResult.imageHeight} px",
                                    color = Color.White,
                                    fontSize = 7.5.sp
                                )
                                Text(
                                    text = "ROTATION: ${geminiResult.imageRotation}°",
                                    color = Color.White,
                                    fontSize = 7.5.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))

                    Text(text = "GEMINI RESULT 1", color = EmeraldLight, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    geminiResult.pass1Response?.let { p1 ->
                        Text(text = "Object: ${p1.objectName}", color = Color.White, fontSize = 8.sp)
                        Text(text = "Confidence: ${(p1.confidence * 100).toInt()}%", color = Color.White, fontSize = 8.sp)
                        Text(text = "Status: ${p1.status}", color = if (p1.status == "CONFIRMED") EmeraldLight else AmberGold, fontSize = 8.sp)
                        Text(text = "Reason: ${p1.visualEvidenceText}", color = Color.LightGray, fontSize = 7.5.sp)
                    } ?: Text(text = "Object: Pending...", color = Color.Gray, fontSize = 8.sp)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))

                    Text(text = "GEMINI RESULT 2", color = EmeraldLight, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    geminiResult.pass2Response?.let { p2 ->
                        Text(text = "Object: ${p2.objectName}", color = Color.White, fontSize = 8.sp)
                        Text(text = "Confidence: ${(p2.confidence * 100).toInt()}%", color = Color.White, fontSize = 8.sp)
                        Text(text = "Status: ${p2.status}", color = if (p2.status == "CONFIRMED") EmeraldLight else AmberGold, fontSize = 8.sp)
                        Text(text = "Reason: ${p2.visualEvidenceText}", color = Color.LightGray, fontSize = 7.5.sp)
                    } ?: Text(text = "Object: Pending...", color = Color.Gray, fontSize = 8.sp)

                    geminiResult.pass3Response?.let { p3 ->
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                        Text(text = "GEMINI RESULT 3 (Tie-Breaker)", color = Color(0xFF38BDF8), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Object: ${p3.objectName}", color = Color.White, fontSize = 8.sp)
                        Text(text = "Confidence: ${(p3.confidence * 100).toInt()}%", color = Color.White, fontSize = 8.sp)
                        Text(text = "Status: ${p3.status}", color = if (p3.status == "CONFIRMED") EmeraldLight else Color.Red, fontSize = 8.sp)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

                    val isConfirmed = geminiResult.status == GeminiVerificationStatus.CONFIRMED
                    Text(
                        text = "FINAL RESULT:",
                        color = AmberGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Object: ${geminiResult.finalConfirmedObject ?: "UNKNOWN"}",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Status: ${if (isConfirmed) "CONFIRMED" else "UNKNOWN"}",
                        color = if (isConfirmed) EmeraldLight else Color(0xFFFF8A80),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. UNKNOWN, UNCERTAIN, or Disambiguation State Card
        if (!isAnalyzingTap && (geminiResult?.status == GeminiVerificationStatus.UNKNOWN || geminiResult?.status == GeminiVerificationStatus.UNCERTAIN || noObjectFoundMessage != null) && selectedObject == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateSurfaceDark.copy(alpha = 0.96f))
                    .border(1.5.dp, AmberGold, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(40.dp)
                    )

                    // If candidates exist for Step 18 Disambiguation
                    val candidates = geminiResult?.disambiguationCandidates ?: emptyList()
                    if (candidates.isNotEmpty()) {
                        Text(
                            text = "Which object did you mean?",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            candidates.forEach { candidate ->
                                Button(
                                    onClick = { onSelectCandidate(candidate) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Text(
                                        text = candidate,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = geminiResult?.userGuidanceMessage ?: noObjectFoundMessage ?: "Object could not be identified clearly. Try moving closer or improving lighting.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissCard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = onRetryTap,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (candidates.isEmpty()) EmeraldGreen else IndigoPurple)
                        ) {
                            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Single Information Card for Tapped Selected Object with Spring-Based Entrance/Exit Animations
        AnimatedVisibility(
            visible = selectedObject != null,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(
                        initialOffsetY = { 80 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    ) +
                    scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    ),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                   slideOutVertically(
                       targetOffsetY = { 80 },
                       animationSpec = spring(stiffness = Spring.StiffnessMedium)
                   ) +
                   scaleOut(
                       targetScale = 0.88f,
                       animationSpec = spring(stiffness = Spring.StiffnessMedium)
                   ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedObject?.let { tracked ->
            val result = tracked.translationResult
            val confPercent = (tracked.confidence * 100).toInt().coerceIn(75, 99)

            // Anchor card near object bounding box or bottom of screen
            val cardTopPx = (tracked.currentBounds.bottom + 16f).coerceAtMost(containerHeightPx - 260f)
            val cardTopDp = with(density) { cardTopPx.toDp() }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceDark.copy(alpha = 0.96f)),
                border = BorderStroke(1.5.dp, EmeraldLight),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row: Object Name, Confidence Badge, Volume & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = result?.objectNameTarget ?: tracked.labelEn,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldLight
                                )

                                // Detection Engine Badge (YOLO vs Gemini Vision)
                                val engineLabel = result?.detectionEngine ?: "Detected by YOLO"
                                val isYolo = engineLabel.contains("YOLO", ignoreCase = true)
                                val engineColor = if (isYolo) EmeraldLight else Color(0xFF38BDF8)
                                val engineBg = if (isYolo) EmeraldGreen.copy(alpha = 0.25f) else Color(0xFF0284C7).copy(alpha = 0.25f)
                                val engineBorder = if (isYolo) EmeraldLight else Color(0xFF38BDF8)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(engineBg)
                                        .border(1.dp, engineBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isYolo) Icons.Default.Memory else Icons.Default.AutoAwesome,
                                            contentDescription = engineLabel,
                                            tint = engineColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = engineLabel,
                                            color = engineColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Confidence Score Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EmeraldGreen.copy(alpha = 0.25f))
                                        .border(1.dp, EmeraldLight, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$confPercent%",
                                        color = EmeraldLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Text(
                                text = "English: ${result?.objectNameEn ?: tracked.labelEn}",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pronounce Audio Button
                            IconButton(
                                onClick = {
                                    onSpeakText(result?.objectNameTarget ?: tracked.labelEn)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(EmeraldGreen.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speak Pronunciation",
                                    tint = EmeraldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Dismiss Card Button
                            IconButton(
                                onClick = onDismissCard,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Pronunciation IPA
                    result?.let { res ->
                        if (res.pronunciationIpa.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Pronunciation:",
                                    fontSize = 11.sp,
                                    color = MutedTextDark,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = res.pronunciationIpa,
                                    fontSize = 12.sp,
                                    color = AmberGold,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Example Sentence Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💬 Example Sentence:",
                                fontSize = 11.sp,
                                color = EmeraldLight,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = res.exampleSentenceTarget,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = res.exampleSentenceEn,
                                color = MutedTextDark,
                                fontSize = 11.sp
                            )
                        }

                        // Practice Speaking Action Button
                        Button(
                            onClick = { onPracticeSpeaking(res) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Practice Speaking '${res.objectNameTarget}' with AI Tutor 💬",
                                    fontSize = 11.sp,
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

        // 5. Instruction Prompt Banner when no object is selected
        if (selectedObject == null && noObjectFoundMessage == null && !isAnalyzingTap) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(1.dp, EmeraldLight.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Tap screen",
                        tint = EmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tap anywhere on an object to identify & translate",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. Camera Error Alert Notice
        if (cameraError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "📷 Camera Error: $cameraError",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
