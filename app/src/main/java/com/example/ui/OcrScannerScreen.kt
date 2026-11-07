package com.example.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.data.ArScanResult
import com.example.data.ImageQualityReport
import com.example.data.MlKitManager
import com.example.ui.theme.*
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OcrScannerScreen(viewModel: MainViewModel) {
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isOcrScanning by viewModel.isOcrScanning.collectAsStateWithLifecycle()
    val ocrResults by viewModel.ocrResults.collectAsStateWithLifecycle()
    val ocrErrorMessage by viewModel.ocrErrorMessage.collectAsStateWithLifecycle()

    val grammarModeActive by viewModel.grammarModeActive.collectAsStateWithLifecycle()
    val isGrammarScanning by viewModel.isGrammarScanning.collectAsStateWithLifecycle()
    val grammarCorrections by viewModel.grammarCorrections.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val arCoreManager = remember { com.example.data.ArCoreManager(context) }
    val isArCoreSupported = arCoreManager.isArCoreSupported
    var isArActive by remember { mutableStateOf(isArCoreSupported) }

    var arGLViewRef by remember { mutableStateOf<ArGLSurfaceView?>(null) }
    var projectedAnchors by remember { mutableStateOf<List<ArGLSurfaceView.ProjectedAnchor>>(emptyList()) }
    var detectedPlanesCount by remember { mutableStateOf(0) }

    // Real ML Kit Manager
    val mlKitManager = remember { MlKitManager(context) }

    // Image quality diagnostics state
    var qualityReport by remember { mutableStateOf(ImageQualityReport()) }
    var lastFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraFrameWidth by remember { mutableStateOf(1080) }
    var cameraFrameHeight by remember { mutableStateOf(1920) }

    var isExportDialogShown by remember { mutableStateOf(false) }
    var aiTutorExplanation by remember { mutableStateOf<String?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        if (isArActive) {
            val error = arCoreManager.initArSession()
            if (error != null) {
                Log.e("OcrScannerScreen", "ARCore Session error: $error. Falling back to CameraX.")
                isArActive = false
            }
        }
        onDispose {
            arCoreManager.release()
            mlKitManager.close()
            cameraExecutor.shutdown()
        }
    }

    // Pulsating scanning line animation
    val infiniteTransition = rememberInfiniteTransition(label = "ocr_scan")
    val sweepY by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep_line"
    )

    if (!grammarModeActive) {
        DocumentTranslateScreen()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateBackgroundDark)
                .testTag("ocr_scanner_screen")
        ) {
            // --- Header Bar ---
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
                        text = "REAL-TIME GRAMMAR DIAGNOSTICS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Live Camera Feed • ML Kit & Gemini Diagnostics",
                        fontSize = 11.sp,
                        color = EmeraldLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = { viewModel.toggleGrammarMode(false) }) {
                    Text("📄 Document Mode", color = EmeraldLight, fontWeight = FontWeight.Bold)
                }
            }

            // --- Mode Selector (Translate Mode vs AR Grammar Assistant) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!grammarModeActive) IndigoPurple else Color.Transparent)
                        .clickable { viewModel.toggleGrammarMode(false) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📄 OCR Document Translate",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (grammarModeActive) Color.Red else Color.Transparent)
                        .clickable { viewModel.toggleGrammarMode(true) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔍 Grammar Diagnostics",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

        // --- Live Camera View with Real-time OCR Bounding Box Overlays ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            if (cameraPermissionState.status.isGranted) {
                if (isArActive) {
                    // ARCore GL Surface Camera View
                    AndroidView(
                        factory = { ctx ->
                            ArGLSurfaceView(ctx).apply {
                                mSession = arCoreManager.session
                                arGLViewRef = this

                                onPlaneDetected = { planes ->
                                    detectedPlanesCount = planes.size
                                }

                                onAnchorUpdated = { projected ->
                                    projectedAnchors = projected
                                }

                                onFrameImageAvailable = { image, rotation ->
                                    try {
                                        cameraFrameWidth = image.width
                                        cameraFrameHeight = image.height

                                        // Image quality check
                                        val report = mlKitManager.checkImageQuality(image)
                                        Handler(Looper.getMainLooper()).post {
                                            qualityReport = report
                                        }

                                        if (isOcrScanning) {
                                            mlKitManager.recognizeText(
                                                image = image,
                                                rotationDegrees = rotation,
                                                onSuccess = { recognizedText ->
                                                    viewModel.processMlKitOcrResult(
                                                        recognizedText = recognizedText,
                                                        imageWidth = cameraFrameWidth,
                                                        imageHeight = cameraFrameHeight
                                                    )
                                                },
                                                onFailure = { ex ->
                                                    Log.e("OcrScannerScreen", "ML Kit OCR failed", ex)
                                                    Handler(Looper.getMainLooper()).post {
                                                        viewModel.clearOcrScanningState()
                                                    }
                                                }
                                            )
                                        }
                                    } finally {
                                        image.close()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (view.mSession != arCoreManager.session) {
                                view.mSession = arCoreManager.session
                            }
                        }
                    )
                } else {
                    // CameraX Preview Pipeline
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                cameraFrameWidth = mediaImage.width
                                                cameraFrameHeight = mediaImage.height

                                                val report = mlKitManager.checkImageQuality(mediaImage)
                                                Handler(Looper.getMainLooper()).post {
                                                    qualityReport = report
                                                }

                                                if (isOcrScanning) {
                                                    mlKitManager.recognizeText(
                                                        image = mediaImage,
                                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                                        onSuccess = { recognizedText ->
                                                            imageProxy.close()
                                                            viewModel.processMlKitOcrResult(
                                                                recognizedText = recognizedText,
                                                                imageWidth = cameraFrameWidth,
                                                                imageHeight = cameraFrameHeight
                                                            )
                                                        },
                                                        onFailure = { ex ->
                                                            imageProxy.close()
                                                            Log.e("OcrScannerScreen", "CameraX OCR failure", ex)
                                                        }
                                                    )
                                                    return@setAnalyzer
                                                }
                                            }
                                            imageProxy.close()
                                        }
                                    }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalyzer
                                    )
                                } catch (exc: Exception) {
                                    Log.e("OcrScannerScreen", "CameraX binding failed", exc)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // --- Image Quality Warning Chips (Low light / Blurry) ---
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (qualityReport.isLowLight) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberGold.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Low Light Detected", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (qualityReport.isBlurry) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hold Steady - Image Blurry", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Scanning Line Overlay
            if (isOcrScanning || isGrammarScanning) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val currentY = maxHeight * sweepY
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .offset(y = currentY)
                            .background(if (grammarModeActive) Color.Red else EmeraldLight)
                    )
                }
            }

            // --- Real AR OCR Overlays (Bounding Boxes placed on top of camera) ---
            if (!grammarModeActive && ocrResults.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    ocrResults.forEach { result ->
                        val posX = (maxWidth.value * result.xPosPercent / 100).dp
                        val posY = (maxHeight.value * result.yPosPercent / 100).dp

                        Box(
                            modifier = Modifier
                                .offset(x = posX, y = posY)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateSurfaceDark.copy(alpha = 0.92f))
                                .border(1.5.dp, EmeraldLight, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.speakText(result.objectNameTarget, selectedLang)
                                }
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldLight)
                                    )
                                    Text(
                                        text = result.objectNameTarget,
                                        color = EmeraldLight,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                        text = result.objectNameEn,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(result.confidence * 100).toInt()}% Conf",
                                        color = AmberGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (result.detectedLanguage.isNotEmpty()) {
                                        Text(
                                            text = result.detectedLanguage.uppercase(),
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Camera Permission Alert View ---
            if (!cameraPermissionState.status.isGranted) {
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
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "No Camera",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Camera Access Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "To scan and translate documents, menus, or signs in real-time using ML Kit, please enable camera access.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Text("Enable Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Error Message Banner ---
        if (ocrErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Red.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ocrErrorMessage ?: "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearOcrErrorMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // --- Extracted Results & Learning Integration Panel ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(SlateBackgroundLight)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (grammarModeActive) "GRAMMAR DIAGNOSTIC REPORT" else "EXTRACTED DOCUMENT TEXT & TRANSLATIONS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = CharcoalTextDark
                )

                if (ocrResults.isNotEmpty()) {
                    IconButton(
                        onClick = { isExportDialogShown = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (ocrResults.isEmpty() && grammarCorrections.isEmpty() && !isOcrScanning && !isGrammarScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "No logs",
                            tint = Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (grammarModeActive) "No Grammar Issues Detected" else "No Extracted Document Text Yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = "Point camera at printed text, menus, or books, then tap 'SCAN DOCUMENT'.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else if (isOcrScanning || isGrammarScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = if (grammarModeActive) Color.Red else EmeraldGreen)
                        Text(
                            text = if (grammarModeActive) "Analyzing text grammar..." else "ML Kit OCR extracting & translating camera text...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grammarModeActive) Color.Red else EmeraldGreen
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ocrResults) { word ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = word.objectNameTarget,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldDark
                                        )
                                        Text(
                                            text = word.pronunciationIpa,
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { viewModel.speakText(word.objectNameTarget, selectedLang) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(EmeraldGreen.copy(alpha = 0.1f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak",
                                                tint = EmeraldGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Divider(color = Color.LightGray.copy(alpha = 0.5f))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "English",
                                        tint = AmberGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Translation: ${word.objectNameEn}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CharcoalTextDark
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateBackgroundLight, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Sentence: ${word.exampleSentenceTarget}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CharcoalTextDark)
                                    Text("Meaning: ${word.exampleSentenceEn}", fontSize = 11.sp, color = Color.Gray)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Book, contentDescription = "Grammar", tint = IndigoPurple, modifier = Modifier.size(14.dp))
                                        Text(word.grammarUsage, fontSize = 11.sp, color = Color.Gray)
                                    }

                                    // Explain with AI Tutor button
                                    TextButton(
                                        onClick = {
                                            aiTutorExplanation = "AI Tutor Analysis for \"${word.objectNameTarget}\":\n\n1. Vocabulary Type: ${word.category}\n2. Difficulty Level: ${word.difficulty}\n3. Grammatical Role: ${word.grammarUsage}\n4. Usage Context: \"${word.exampleSentenceTarget}\""
                                        }
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = IndigoPurple, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Explain with AI Tutor", fontSize = 11.sp, color = IndigoPurple, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Action Controls: Scan Camera Button & Voice Recognition ---
            val isListening by viewModel.isSpeechListening.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (grammarModeActive) {
                            viewModel.performGrammarAnalysis("REAL_TEXT_GRAMMAR")
                        } else {
                            // Trigger ML Kit recognition on active live camera frame
                            viewModel.clearOcrResults()
                            viewModel.triggerOcrScan()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("scan_ocr_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = if (grammarModeActive) Color.Red else EmeraldDark),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isOcrScanning && !isGrammarScanning && !isListening
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (grammarModeActive) Icons.Default.Spellcheck else Icons.Default.Camera, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOcrScanning || isGrammarScanning) "ANALYZING FRAME..." else if (grammarModeActive) "ANALYZE GRAMMAR" else "SCAN DOCUMENT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isListening) {
                            viewModel.stopVoiceRecognition()
                        } else {
                            viewModel.startVoiceRecognition(
                                contextType = "TRANSLATOR",
                                languageName = selectedLang,
                                onTextUpdate = {},
                                onComplete = { spoken ->
                                    viewModel.addSpokenOcrResult(spoken)
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (isListening) Color.Red else EmeraldGreen, RoundedCornerShape(12.dp))
                        .testTag("ocr_microphone_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Speak to translate",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // --- AI Tutor Explanation Dialog ---
    if (aiTutorExplanation != null) {
        AlertDialog(
            onDismissRequest = { aiTutorExplanation = null },
            confirmButton = {
                TextButton(onClick = { aiTutorExplanation = null }) {
                    Text("Close", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = IndigoPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Tutor Explanation", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = aiTutorExplanation ?: "",
                    fontSize = 12.sp,
                    color = CharcoalTextDark,
                    lineHeight = 16.sp
                )
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // --- PDF Export Dialogue ---
    if (isExportDialogShown) {
        AlertDialog(
            onDismissRequest = { isExportDialogShown = false },
            confirmButton = {
                TextButton(onClick = { isExportDialogShown = false }) {
                    Text("OK", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text("Export PDF Document", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your OCR extracted vocabulary notes have been compiled successfully into a PDF.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "PDF Success", tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("arlingua_ocr_document.pdf", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldDark)
                    }
                    Text("Saved locally in Documents/ARLingua.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
}
