package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.document.*
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentTranslateScreen(
    docViewModel: DocumentTranslationViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    val processingStage by docViewModel.processingStage.collectAsStateWithLifecycle()
    val preprocessingMode by docViewModel.preprocessingMode.collectAsStateWithLifecycle()
    val capturedBitmap by docViewModel.capturedBitmap.collectAsStateWithLifecycle()
    val ocrDocument by docViewModel.ocrDocument.collectAsStateWithLifecycle()
    val qualityReport by docViewModel.ocrQualityReport.collectAsStateWithLifecycle()
    val sourceLang by docViewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLang by docViewModel.targetLanguage.collectAsStateWithLifecycle()
    val translationResult by docViewModel.translationResult.collectAsStateWithLifecycle()
    val editedTranslationText by docViewModel.editedTranslationText.collectAsStateWithLifecycle()
    val generatedPdfInfo by docViewModel.generatedPdfInfo.collectAsStateWithLifecycle()
    val chunkProgress by docViewModel.chunkProgress.collectAsStateWithLifecycle()
    val errorMessage by docViewModel.errorMessage.collectAsStateWithLifecycle()
    val isTtsSpeaking by docViewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val documentHistory by docViewModel.documentHistory.collectAsStateWithLifecycle()

    var activeInputTab by remember { mutableIntStateOf(0) } // 0: Camera, 1: Gallery, 2: History
    var showTargetLangDropdown by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { docViewModel.processImageUri(it) }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Document Translator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "OCR, Translation & PDF Compiler",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (processingStage != DocumentProcessingStage.IDLE) {
                        TextButton(onClick = { docViewModel.resetState() }) {
                            Text("Reset", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- Input Mode Selector Tabs ---
            TabRow(
                selectedTabIndex = activeInputTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = EmeraldGreen
            ) {
                Tab(
                    selected = activeInputTab == 0,
                    onClick = { activeInputTab = 0 },
                    text = { Text("Camera Scan", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeInputTab == 0) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (activeInputTab == 0) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Tab(
                    selected = activeInputTab == 1,
                    onClick = {
                        activeInputTab = 1
                        galleryLauncher.launch("image/*")
                    },
                    text = { Text("Import Image", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeInputTab == 1) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (activeInputTab == 1) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Tab(
                    selected = activeInputTab == 2,
                    onClick = { activeInputTab = 2 },
                    text = { Text("History (${documentHistory.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeInputTab == 2) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (activeInputTab == 2) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            // --- Error Banner ---
            if (errorMessage != null) {
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
                            text = errorMessage ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { docViewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- Main Workspace Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    // TAB 2: History List
                    activeInputTab == 2 -> {
                        DocumentHistoryList(
                            records = documentHistory,
                            onSelectRecord = { record ->
                                docViewModel.updateEditedTranslationText(record.translation)
                                docViewModel.setTargetLanguage(record.language)
                            }
                        )
                    }

                    // Processing / Loading State Indicator
                    processingStage == DocumentProcessingStage.PROCESSING_IMAGE ||
                    processingStage == DocumentProcessingStage.OCR_PROCESSING ||
                    processingStage == DocumentProcessingStage.TRANSLATING ||
                    processingStage == DocumentProcessingStage.GENERATING_PDF -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = EmeraldGreen, strokeWidth = 4.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when (processingStage) {
                                    DocumentProcessingStage.PROCESSING_IMAGE -> "Enhancing document image..."
                                    DocumentProcessingStage.OCR_PROCESSING -> "Extracting printed & handwritten text..."
                                    DocumentProcessingStage.TRANSLATING -> if (chunkProgress.second > 1) "Translating chunk ${chunkProgress.first} of ${chunkProgress.second} via Gemini..." else "Translating document into $targetLang..."
                                    DocumentProcessingStage.GENERATING_PDF -> "Generating Unicode PDF document..."
                                    else -> "Processing..."
                                },
                                fontWeight = FontWeight.Bold,
                                color = CharcoalTextDark,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Document Review & Translation Workspace
                    ocrDocument != null && (processingStage == DocumentProcessingStage.OCR_COMPLETE ||
                            processingStage == DocumentProcessingStage.TRANSLATION_COMPLETE ||
                            processingStage == DocumentProcessingStage.PDF_COMPLETE) -> {

                        DocumentReviewWorkspace(
                            ocrDoc = ocrDocument!!,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            showLangDropdown = showTargetLangDropdown,
                            onToggleLangDropdown = { showTargetLangDropdown = !showTargetLangDropdown },
                            onSelectTargetLang = {
                                docViewModel.setTargetLanguage(it)
                                showTargetLangDropdown = false
                            },
                            editedText = editedTranslationText,
                            onTextEdited = { docViewModel.updateEditedTranslationText(it) },
                            onTranslate = { docViewModel.translateDocumentText() },
                            onGeneratePdf = { docViewModel.generatePdf() },
                            generatedPdfInfo = generatedPdfInfo,
                            onOpenPdf = { docViewModel.openPdf(context) },
                            onSharePdf = { docViewModel.sharePdf(context) },
                            isTtsSpeaking = isTtsSpeaking,
                            onPlayTts = { docViewModel.speakTranslation() }
                        )
                    }

                    // TAB 0: Camera Viewfinder & Alignment Frame
                    activeInputTab == 0 -> {
                        if (!cameraPermissionState.status.isGranted) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.9f))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Camera Permission Required", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                        Text("Enable Camera")
                                    }
                                }
                            }
                        } else {
                            CameraScannerView(
                                preprocessingMode = preprocessingMode,
                                onModeSelected = { docViewModel.setPreprocessingMode(it) },
                                qualityReport = qualityReport,
                                onCaptureFrame = { bitmap ->
                                    docViewModel.processBitmapOcr(bitmap)
                                }
                            )
                        }
                    }

                    // Default Fallback
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = { galleryLauncher.launch("image/*") }) {
                                Text("Select Document Image")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraScannerView(
    preprocessingMode: PreprocessingMode,
    onModeSelected: (PreprocessingMode) -> Unit,
    qualityReport: DocumentQualityReport,
    onCaptureFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewRef = this

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        } catch (e: Exception) {
                            Log.e("CameraScannerView", "Error binding CameraX", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Document Alignment Rectangle Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .border(2.5.dp, EmeraldLight, RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }

        // Quality warning chips
        if (qualityReport.isLowLight || qualityReport.isBlurry) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = qualityReport.warningMessage ?: "Align document inside frame",
                    color = AmberGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Controls: Preprocessing Modes & Capture FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PreprocessingMode.entries.toTypedArray()) { mode ->
                    FilterChip(
                        selected = preprocessingMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.name.replace("_", " ")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Capture FAB
            FloatingActionButton(
                onClick = {
                    val view = previewViewRef
                    if (view != null && view.bitmap != null) {
                        onCaptureFrame(view.bitmap!!)
                    }
                },
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Scan Document", modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun DocumentReviewWorkspace(
    ocrDoc: OcrDocument,
    sourceLang: String,
    targetLang: String,
    showLangDropdown: Boolean,
    onToggleLangDropdown: () -> Unit,
    onSelectTargetLang: (String) -> Unit,
    editedText: String,
    onTextEdited: (String) -> Unit,
    onTranslate: () -> Unit,
    onGeneratePdf: () -> Unit,
    generatedPdfInfo: GeneratedPdfInfo?,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    isTtsSpeaking: Boolean,
    onPlayTts: () -> Unit
) {
    var activeViewTab by remember { mutableIntStateOf(0) } // 0: Translation, 1: Original OCR Text

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Source & Target Language Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Detected Language", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sourceLang, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    }

                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = EmeraldGreen)

                    Box {
                        OutlinedButton(onClick = onToggleLangDropdown) {
                            Text("Target: $targetLang", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = EmeraldGreen)
                        }

                        DropdownMenu(
                            expanded = showLangDropdown,
                            onDismissRequest = onToggleLangDropdown
                        ) {
                            TranslationEngine.SUPPORTED_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.displayName} (${lang.nativeName})", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { onSelectTargetLang(lang.displayName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons: Translate & Generate PDF
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTranslate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Translate", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onGeneratePdf,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPurple, contentColor = Color.White),
                    enabled = editedText.isNotBlank()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF", fontWeight = FontWeight.Bold)
                }
            }
        }

        // PDF Success Card
        if (generatedPdfInfo != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PDF Compiled Successfully", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }

                        Text(
                            text = "${generatedPdfInfo.fileName} (${generatedPdfInfo.fileSizeFormatted}, ${generatedPdfInfo.pageCount} Pages)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenPdf, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open PDF")
                            }

                            Button(
                                onClick = onSharePdf,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF")
                            }
                        }
                    }
                }
            }
        }

        // Text Views Switcher (Original OCR vs Translated)
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = activeViewTab == 0,
                    onClick = { activeViewTab = 0 },
                    label = { Text("Translated Text ($targetLang)", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = activeViewTab == 1,
                    onClick = { activeViewTab = 1 },
                    label = { Text("Original Extracted OCR Text", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // Editable Translation / Original Text Workspace
        item {
            if (activeViewTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Review & Edit Translation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

                        IconButton(onClick = onPlayTts) {
                            Icon(
                                imageVector = if (isTtsSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "TTS",
                                tint = EmeraldGreen
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editedText,
                        onValueChange = onTextEdited,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 400.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = EmeraldGreen
                        )
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = ocrDoc.fullText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentHistoryList(
    records: List<com.example.data.OcrRecord>,
    onSelectRecord: (com.example.data.OcrRecord) -> Unit
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No saved document translations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(records) { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRecord(record) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Language: ${record.language}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldGreen)
                            Text(
                                java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.scanTime)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = record.translation,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
