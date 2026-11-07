package com.example.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.ArScanResult
import com.example.service.CameraService
import com.example.service.ObjectDetectionService
import com.example.service.MultiFrameObjectVerifier
import com.example.service.GeminiPrimaryObjectVerifier
import com.example.service.GeminiVerificationResult
import com.example.service.GeminiVerificationStatus
import com.example.service.FrameHolderData
import com.example.service.DetectionSessionState
import com.example.service.SessionVerificationResult
import com.example.service.PronunciationService
import com.example.service.RawDetectedObject
import com.example.service.TrackedArObject
import com.example.service.TranslationService
import com.example.ui.theme.*
import kotlin.math.sqrt

class ArFrameHolder {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var rotationDegrees: Int = 90
    @Volatile var frameWidth: Int = 1080
    @Volatile var frameHeight: Int = 1920
}

/**
 * Tap-to-Identify AR Scanner Screen.
 * 
 * Behavior:
 * 1. Shows camera preview only.
 * 2. Idle performance (0% continuous ML detection overhead).
 * 3. On user tap:
 *    - Triggers focus ripple animation at tapped coordinates.
 *    - Captures camera frame & detects objects in region.
 *    - Selects closest object to tap location.
 *    - Displays single info card (Object, Translation, Pronunciation, Sentence, Confidence).
 *    - Highlights ONLY selected object with ONE bounding box.
 *    - Ignores all other objects.
 *    - Displays clear "No recognizable object found" message if tap missed objects.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ArScannerScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // AR Services
    val translationService = remember { TranslationService(context) }
    val pronunciationService = remember { PronunciationService(context) }
    val objectDetectionService = remember { ObjectDetectionService(context) }
    val cameraService = remember { CameraService(context) }
    val multiFrameVerifier = remember { MultiFrameObjectVerifier(objectDetectionService) }
    val geminiPrimaryVerifier = remember {
        GeminiPrimaryObjectVerifier(
            geminiService = viewModel.geminiService,
            cameraService = cameraService,
            objectDetectionService = objectDetectionService
        )
    }

    // Screen & Camera view dimensions
    var cameraBoxWidthPx by remember { mutableFloatStateOf(1080f) }
    var cameraBoxHeightPx by remember { mutableFloatStateOf(1920f) }

    // Frame holder (non-state to avoid 30fps recomposition thrashing)
    val frameHolder = remember { ArFrameHolder() }

    // Tap & Multi-Frame Session state
    var tapPoint by remember { mutableStateOf<Offset?>(null) }
    var isAnalyzingTap by remember { mutableStateOf(false) }
    var analyzingStatusText by remember { mutableStateOf("Analyzing tapped object...") }
    var latestGeminiResult by remember { mutableStateOf<GeminiVerificationResult?>(null) }
    var currentSessionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isDebugOverlayVisible by remember { mutableStateOf(true) }

    var selectedTrackedObject by remember { mutableStateOf<TrackedArObject?>(null) }
    var realtimeDetectedObjects by remember { mutableStateOf<List<RawDetectedObject>>(emptyList()) }
    var noObjectFoundMessage by remember { mutableStateOf<String?>(null) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        onDispose {
            currentSessionJob?.cancel()
            cameraService.release()
            objectDetectionService.close()
            pronunciationService.shutdown()
        }
    }

    // Helper: Primary Object Identification Pipeline with Gemini Vision
    fun processTapToIdentify(tapX: Float, tapY: Float) {
        val rawBitmap = frameHolder.bitmap
        if (rawBitmap == null || rawBitmap.isRecycled) {
            noObjectFoundMessage = "Camera stream initializing. Please try tapping again."
            Log.e("ArScannerScreen", "[Frame Capture] FAILED: Camera frame bitmap is null or recycled.")
            return
        }

        // Cancel previous session job to prevent async race conditions
        currentSessionJob?.cancel()

        val (sessionId, requestId) = geminiPrimaryVerifier.createSessionToken()
        isAnalyzingTap = true
        analyzingStatusText = "Capturing image frames..."
        tapPoint = Offset(tapX, tapY)
        selectedTrackedObject = null
        noObjectFoundMessage = null
        latestGeminiResult = null

        currentSessionJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                Log.i("ArScannerScreen", "==========================================================")
                Log.i("ArScannerScreen", "[Session $sessionId | Req $requestId] User tapped at screen ($tapX, $tapY). Starting Gemini Primary Verification...")

                val verificationResult = geminiPrimaryVerifier.verifyObjectWithGemini(
                    sessionId = sessionId,
                    requestId = requestId,
                    tapXScreen = tapX,
                    tapYScreen = tapY,
                    cameraBoxWidthPx = cameraBoxWidthPx,
                    cameraBoxHeightPx = cameraBoxHeightPx,
                    targetLanguage = selectedLang,
                    captureFrameSupplier = {
                        val currentBmp = frameHolder.bitmap
                        if (currentBmp != null && !currentBmp.isRecycled) {
                            FrameHolderData(
                                bitmap = currentBmp,
                                rotationDegrees = frameHolder.rotationDegrees,
                                frameWidth = frameHolder.frameWidth,
                                frameHeight = frameHolder.frameHeight
                            )
                        } else null
                    },
                    onProgressUpdate = { progress ->
                        coroutineScope.launch(Dispatchers.Main) {
                            latestGeminiResult = progress
                            analyzingStatusText = when (progress.status) {
                                GeminiVerificationStatus.CAPTURING_BURST -> "Capturing image frames..."
                                GeminiVerificationStatus.ANALYZING_QUALITY -> "Selecting sharpest frame..."
                                GeminiVerificationStatus.GEMINI_PASS_1 -> "Identifying object with Gemini Vision (Pass 1)..."
                                GeminiVerificationStatus.GEMINI_PASS_2 -> "Verifying object identity (Pass 2)..."
                                GeminiVerificationStatus.GEMINI_PASS_3 -> "Executing tie-breaker analysis (Pass 3)..."
                                else -> "Processing..."
                            }
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    latestGeminiResult = verificationResult
                    isAnalyzingTap = false

                    if (!geminiPrimaryVerifier.isSessionValid(sessionId, requestId)) {
                        Log.w("ArScannerScreen", "[Stale Check] Discarding response for stale session $sessionId")
                        return@withContext
                    }

                    if (verificationResult.status == GeminiVerificationStatus.CONFIRMED && verificationResult.finalConfirmedObject != null) {
                        val confirmedLabel = verificationResult.finalConfirmedObject
                        analyzingStatusText = "Translating $confirmedLabel to $selectedLang..."

                        val translationRes = viewModel.translateDetectedObjectName(confirmedLabel)

                        val bounds = RectF(
                            tapX - 120f, tapY - 120f, tapX + 120f, tapY + 120f
                        )

                        val scanResultObj = ArScanResult(
                            objectNameEn = translationRes.objectNameEn.ifBlank { confirmedLabel },
                            objectNameTarget = translationRes.objectNameTarget,
                            pronunciationIpa = translationRes.pronunciationIpa,
                            exampleSentenceTarget = translationRes.exampleSentenceTarget,
                            exampleSentenceEn = translationRes.exampleSentenceEn,
                            grammarUsage = translationRes.grammarUsage,
                            category = translationRes.category,
                            difficulty = translationRes.difficulty,
                            xPosPercent = 50,
                            yPosPercent = 50,
                            confidence = 0.95f,
                            detectionEngine = "Identified by Gemini Vision"
                        )

                        val trackedObj = TrackedArObject(
                            id = "selected_${System.currentTimeMillis()}",
                            labelEn = translationRes.objectNameEn.ifBlank { confirmedLabel },
                            currentBounds = bounds,
                            targetBounds = bounds,
                            confidence = 0.95f,
                            translationResult = scanResultObj,
                            alpha = 1.0f,
                            isLocked = true
                        )

                        selectedTrackedObject = trackedObj
                        noObjectFoundMessage = null
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        pronunciationService.speak(translationRes.objectNameTarget, selectedLang)

                        viewModel.loadArCulturalLearningCard(
                            objectNameEn = translationRes.objectNameEn.ifBlank { confirmedLabel },
                            confidence = 0.95f,
                            detectionEngine = "Identified by Gemini Vision"
                        )
                    } else {
                        selectedTrackedObject = null
                        noObjectFoundMessage = verificationResult.userGuidanceMessage ?: "Object could not be identified clearly. Try moving closer or changing camera angle."
                    }
                }
            } catch (e: Exception) {
                Log.e("ArScannerScreen", "Pipeline exception during Gemini verification", e)
                withContext(Dispatchers.Main) {
                    selectedTrackedObject = null
                    noObjectFoundMessage = "Session error. Please tap to try again."
                    isAnalyzingTap = false
                }
            }
        }
    }

    val handleSelectCandidate: (String) -> Unit = { candidateName ->
        coroutineScope.launch {
            isAnalyzingTap = true
            analyzingStatusText = "Translating '$candidateName'..."
            val translationRes = viewModel.translateDetectedObjectName(candidateName)
            withContext(Dispatchers.Main) {
                isAnalyzingTap = false
                val scanResultObj = ArScanResult(
                    objectNameEn = translationRes.objectNameEn.ifBlank { candidateName },
                    objectNameTarget = translationRes.objectNameTarget,
                    pronunciationIpa = translationRes.pronunciationIpa,
                    exampleSentenceTarget = translationRes.exampleSentenceTarget,
                    exampleSentenceEn = translationRes.exampleSentenceEn,
                    grammarUsage = translationRes.grammarUsage,
                    category = translationRes.category,
                    difficulty = translationRes.difficulty,
                    xPosPercent = 50,
                    yPosPercent = 50,
                    confidence = 0.98f,
                    detectionEngine = "Selected by User Disambiguation"
                )
                val currentTap = tapPoint ?: Offset(cameraBoxWidthPx / 2f, cameraBoxHeightPx / 2f)
                val bounds = RectF(
                    (currentTap.x - 120f).coerceAtLeast(0f),
                    (currentTap.y - 120f).coerceAtLeast(0f),
                    (currentTap.x + 120f).coerceAtMost(cameraBoxWidthPx),
                    (currentTap.y + 120f).coerceAtMost(cameraBoxHeightPx)
                )
                val trackedObj = TrackedArObject(
                    id = "disambiguated_${System.currentTimeMillis()}",
                    labelEn = candidateName,
                    currentBounds = bounds,
                    targetBounds = bounds,
                    confidence = 0.98f,
                    translationResult = scanResultObj,
                    alpha = 1.0f,
                    isLocked = true
                )
                selectedTrackedObject = trackedObj
                noObjectFoundMessage = null
                pronunciationService.speak(translationRes.objectNameTarget, selectedLang)
                viewModel.loadArCulturalLearningCard(
                    objectNameEn = candidateName,
                    confidence = 0.98f,
                    detectionEngine = "Selected by User Disambiguation"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackgroundDark)
            .testTag("ar_scanner_screen")
    ) {
        // --- Top Header HUD Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateBackgroundDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TAP-TO-IDENTIFY AR VISION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "👉 Tap anywhere on camera view to translate object",
                    fontSize = 11.sp,
                    color = EmeraldLight,
                    fontWeight = FontWeight.Bold
                )
            }

            // Target Language Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldGreen.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldLight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Target: $selectedLang",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // --- Main AR Camera Container with Tap Gesture Detection ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    val w = coords.size.width.toFloat()
                    val h = coords.size.height.toFloat()
                    if (w > 0 && h > 0) {
                        cameraBoxWidthPx = w
                        cameraBoxHeightPx = h
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        processTapToIdentify(offset.x, offset.y)
                    }
                }
        ) {
            if (cameraPermissionState.status.isGranted) {
                // Live AR Camera View with ARCore Session initialization & camera preview rendering
                ARCamera(
                    modifier = Modifier.fillMaxSize(),
                    onFrameBitmapAvailable = { bitmap, rot, w, h ->
                        frameHolder.bitmap = bitmap
                        frameHolder.rotationDegrees = rot
                        frameHolder.frameWidth = w
                        frameHolder.frameHeight = h
                    },
                    onTap = { offset ->
                        processTapToIdentify(offset.x, offset.y)
                    },
                    overlayContent = {
                        // Precision AR Tap & Highlight Overlay Renderer
                        ArOverlayRenderer(
                            modifier = Modifier.fillMaxSize(),
                            selectedObject = selectedTrackedObject,
                            realtimeDetectedObjects = realtimeDetectedObjects,
                            frameWidth = frameHolder.frameWidth,
                            frameHeight = frameHolder.frameHeight,
                            tapPoint = tapPoint,
                            isAnalyzingTap = isAnalyzingTap,
                            analyzingStatusText = analyzingStatusText,
                            geminiResult = latestGeminiResult,
                            isDebugOverlayVisible = isDebugOverlayVisible,
                            onToggleDebugOverlay = { isDebugOverlayVisible = !isDebugOverlayVisible },
                            noObjectFoundMessage = noObjectFoundMessage,
                            isModelLoaded = objectDetectionService.isModelLoaded,
                            cameraError = cameraErrorMessage,
                            onSpeakText = { text ->
                                pronunciationService.speak(text, selectedLang)
                                viewModel.speakText(text, selectedLang)
                            },
                            onDismissCard = {
                                selectedTrackedObject = null
                                noObjectFoundMessage = null
                                tapPoint = null
                                latestGeminiResult = null
                                viewModel.dismissArLearningCard()
                            },
                            onRetryTap = {
                                tapPoint?.let { processTapToIdentify(it.x, it.y) }
                            },
                            onSelectCandidate = handleSelectCandidate,
                            onPracticeSpeaking = { res ->
                                viewModel.sendTutorMessage("Let me practice speaking about ${res.objectNameEn} (${res.objectNameTarget} in $selectedLang).")
                                viewModel.requestRouteSwitch("tutor")
                            }
                        )

                        // 7-Card Material 3 AR Cultural Learning Bottom Sheet
                        ArCulturalLearningCardBottomSheet(
                            cardData = viewModel.currentArLearningCard.value,
                            isLoading = viewModel.isLoadingArLearningCard.value,
                            isVisible = viewModel.isArLearningCardVisible.value,
                            onDismiss = {
                                viewModel.dismissArLearningCard()
                                selectedTrackedObject = null
                                tapPoint = null
                            },
                            onSpeakText = { text ->
                                pronunciationService.speak(text, selectedLang)
                                viewModel.speakText(text, selectedLang)
                            },
                            onToggleFavorite = { card ->
                                viewModel.saveArLearningCardToVocabulary(card)
                            },
                            onSaveToVocabulary = { card ->
                                viewModel.saveArLearningCardToVocabulary(card)
                            },
                            onSelectRelatedWord = { wordEn ->
                                viewModel.loadArCulturalLearningCard(wordEn)
                            },
                            onCompleteQuiz = { isCorrect, xpEarned ->
                                viewModel.addXp(xpEarned, "AR_MINI_QUIZ")
                            }
                        )
                    }
                )
            } else {
                // Permission Request Fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Camera Permission Required",
                            tint = EmeraldLight,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "AR Vision requires live camera access to capture tapped objects and render translations.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
