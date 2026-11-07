package com.example.ui

import android.app.Activity
import android.graphics.RectF
import android.media.Image
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.MlKitManager
import com.example.service.CameraService
import com.example.service.ObjectDetectionService
import com.example.service.RawDetectedObject
import com.example.ui.theme.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.*

private const val TAG = "ARCameraPreview"

/**
 * ARCameraPreview:
 * A full-screen Jetpack Compose component that uses the ARCore dependency to display the live
 * camera feed. Automatically initializes the ARCore Session, handles lifecycle events (pause/resume/destroy),
 * sets up OpenGL surface rendering with plane detection, integrates ML Kit object detection to identify
 * objects in real-time, renders bounding box overlays, and provides automatic high-speed CameraX
 * fallback if device/emulator lacks native ARCore hardware support.
 */
@Composable
fun ARCameraPreview(
    modifier: Modifier = Modifier,
    enablePlaneDetection: Boolean = true,
    enableSpatialAnchors: Boolean = true,
    enableObjectDetection: Boolean = true,
    showBoundingBoxOverlay: Boolean = true,
    showStatusBadge: Boolean = true,
    selectedTrackingId: Int? = null,
    onSessionInitialized: ((Session) -> Unit)? = null,
    onPlaneDetected: ((List<Plane>) -> Unit)? = null,
    onAnchorUpdated: ((List<ArGLSurfaceView.ProjectedAnchor>) -> Unit)? = null,
    onFrameImageAvailable: ((Image, Int) -> Unit)? = null,
    onObjectsDetected: ((List<RawDetectedObject>) -> Unit)? = null,
    onObjectSelected: ((RawDetectedObject?) -> Unit)? = null,
    onTapOnPlane: ((Anchor) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var arSession by remember { mutableStateOf<Session?>(null) }
    var arSessionState by remember { mutableStateOf<CameraViewArState>(CameraViewArState.Initializing) }
    var glSurfaceView by remember { mutableStateOf<ArGLSurfaceView?>(null) }
    var isInstallPrompted by remember { mutableStateOf(false) }

    // Real-time ML Kit Object Detection State
    var detectedObjects by remember { mutableStateOf<List<RawDetectedObject>>(emptyList()) }
    var internalSelectedId by remember { mutableStateOf<Int?>(selectedTrackingId) }
    var frameWidth by remember { mutableIntStateOf(1080) }
    var frameHeight by remember { mutableIntStateOf(1920) }

    val mlKitManager = remember { MlKitManager(context) }
    val objectDetectionService = remember { ObjectDetectionService(context) }
    val fallbackCameraService = remember { CameraService(context) }

    LaunchedEffect(selectedTrackingId) {
        internalSelectedId = selectedTrackingId
    }

    DisposableEffect(mlKitManager, objectDetectionService) {
        onDispose {
            mlKitManager.close()
            objectDetectionService.close()
        }
    }

    fun setupArCoreSession(): Session? {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isSupported) {
                val session = Session(context)
                val config = Config(session).apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = if (enablePlaneDetection) {
                        Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    } else {
                        Config.PlaneFindingMode.DISABLED
                    }
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }
                session.configure(config)
                Log.i(TAG, "[ARCameraPreview] ARCore session configured successfully.")
                session
            } else if (availability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ||
                availability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD) {
                val activity = context as? Activity
                if (activity != null && !isInstallPrompted) {
                    when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            isInstallPrompted = true
                            arSessionState = CameraViewArState.Error("ARCore installation required.", isInstallRequired = true)
                            return null
                        }
                        ArCoreApk.InstallStatus.INSTALLED -> {
                            return setupArCoreSession()
                        }
                    }
                }
                arSessionState = CameraViewArState.FallbackCameraX
                null
            } else {
                Log.w(TAG, "[ARCameraPreview] ARCore unsupported on this device. Fallback to CameraX preview.")
                arSessionState = CameraViewArState.FallbackCameraX
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[ARCameraPreview] Exception creating ARCore session: ${e.message}", e)
            arSessionState = CameraViewArState.FallbackCameraX
            null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    arSession?.let { session ->
                        try {
                            session.resume()
                            glSurfaceView?.onResume()
                            Log.i(TAG, "[ARCameraPreview] Session resumed.")
                        } catch (e: Exception) {
                            Log.e(TAG, "[ARCameraPreview] Error resuming session", e)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView?.onPause()
                    arSession?.let { session ->
                        try {
                            session.pause()
                            Log.i(TAG, "[ARCameraPreview] Session paused.")
                        } catch (e: Exception) {
                            Log.e(TAG, "[ARCameraPreview] Error pausing session", e)
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    glSurfaceView?.releaseAllResources()
                    arSession?.close()
                    arSession = null
                    fallbackCameraService.release()
                    Log.i(TAG, "[ARCameraPreview] Session destroyed.")
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView?.releaseAllResources()
            arSession?.close()
            arSession = null
            fallbackCameraService.release()
        }
    }

    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("ar_camera_preview_fullscreen_container")
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detectedObjects, frameWidth, frameHeight, containerWidth, containerHeight) {
                    detectTapGestures { offset ->
                        onTap?.invoke(offset)

                        if (enableObjectDetection && detectedObjects.isNotEmpty()) {
                            val scaleX = containerWidth / frameWidth.coerceAtLeast(1).toFloat()
                            val scaleY = containerHeight / frameHeight.coerceAtLeast(1).toFloat()

                            val tappedObj = detectedObjects.firstOrNull { obj ->
                                val box = obj.boundingBox
                                val left = box.left * scaleX
                                val top = box.top * scaleY
                                val right = box.right * scaleX
                                val bottom = box.bottom * scaleY
                                offset.x in left..right && offset.y in top..bottom
                            }

                            if (tappedObj != null) {
                                triggerObjectIdentificationHaptic(context, hapticFeedback)
                            }

                            internalSelectedId = tappedObj?.trackingId
                            onObjectSelected?.invoke(tappedObj)
                        }
                    }
                }
        ) {
        when (val state = arSessionState) {
            is CameraViewArState.Initializing,
            is CameraViewArState.SessionCreated,
            is CameraViewArState.Tracking,
            is CameraViewArState.SearchingForSurfaces -> {
                AndroidView(
                    factory = { ctx ->
                        val view = ArGLSurfaceView(ctx).apply {
                            val session = setupArCoreSession()
                            if (session != null) {
                                arSession = session
                                mSession = session
                                arSessionState = CameraViewArState.SessionCreated
                                onSessionInitialized?.invoke(session)

                                this.onPlaneDetected = { planes ->
                                    if (planes.isNotEmpty()) {
                                        arSessionState = CameraViewArState.Tracking(planes.size)
                                    } else {
                                        arSessionState = CameraViewArState.SearchingForSurfaces
                                    }
                                    onPlaneDetected?.invoke(planes)
                                }

                                this.onAnchorUpdated = { anchors ->
                                    onAnchorUpdated?.invoke(anchors)
                                }

                                this.onFrameImageAvailable = { img, rot ->
                                    frameWidth = img.width
                                    frameHeight = img.height

                                    if (enableObjectDetection) {
                                        val frameBm = try {
                                            val yBuffer = img.planes[0].buffer
                                            val vuBuffer = img.planes[2].buffer
                                            val ySize = yBuffer.remaining()
                                            val vuSize = vuBuffer.remaining()
                                            val nv21 = ByteArray(ySize + vuSize)
                                            yBuffer.get(nv21, 0, ySize)
                                            vuBuffer.get(nv21, ySize, vuSize)
                                            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, img.width, img.height, null)
                                            val out = java.io.ByteArrayOutputStream()
                                            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, img.width, img.height), 80, out)
                                            val imageBytes = out.toByteArray()
                                            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        } catch (_: Exception) { null }

                                        if (frameBm != null) {
                                            objectDetectionService.processFrame(
                                                bitmap = frameBm,
                                                rotationDegrees = rot,
                                                frameWidth = img.width,
                                                frameHeight = img.height,
                                                onSuccess = { rawObjects ->
                                                    detectedObjects = rawObjects
                                                    onObjectsDetected?.invoke(rawObjects)
                                                    try { img.close() } catch (_: Exception) {}
                                                },
                                                onError = {
                                                    try { img.close() } catch (_: Exception) {}
                                                }
                                            )
                                        } else {
                                            try { img.close() } catch (_: Exception) {}
                                        }
                                    } else {
                                        try { img.close() } catch (_: Exception) {}
                                    }

                                    onFrameImageAvailable?.invoke(img, rot)
                                }

                                this.onTapOnPlane = { anchor ->
                                    onTapOnPlane?.invoke(anchor)
                                }
                            } else {
                                arSessionState = CameraViewArState.FallbackCameraX
                            }
                        }
                        glSurfaceView = view
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        view.releaseAllResources()
                    }
                )
            }

            is CameraViewArState.FallbackCameraX -> {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        fallbackCameraService.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            onFrameAvailable = { bitmap, rot, width, height ->
                                if (enableObjectDetection) {
                                    frameWidth = width
                                    frameHeight = height
                                    objectDetectionService.processFrame(
                                        bitmap = bitmap,
                                        rotationDegrees = rot,
                                        frameWidth = width,
                                        frameHeight = height,
                                        onSuccess = { rawObjects ->
                                            detectedObjects = rawObjects
                                            onObjectsDetected?.invoke(rawObjects)
                                        },
                                        onError = { }
                                    )
                                }
                            },
                            onError = { err ->
                                Log.e(TAG, "[ARCameraPreview Fallback Error] ${err.message}")
                            }
                        )

                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = {
                        fallbackCameraService.stopCamera()
                    }
                )
            }

            is CameraViewArState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SlateBackgroundDark)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "AR Session Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Camera Feed Active",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = state.message,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = {
                                arSessionState = CameraViewArState.FallbackCameraX
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Text("Switch to High-Speed Live Preview", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Real-time ML Kit Bounding Box Overlay
        if (showBoundingBoxOverlay && detectedObjects.isNotEmpty()) {
            MlKitBoundingBoxOverlay(
                modifier = Modifier.fillMaxSize(),
                detectedObjects = detectedObjects,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                selectedTrackingId = internalSelectedId
            )
        }

        if (showStatusBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SlateSurfaceDark.copy(alpha = 0.85f))
                    .border(1.dp, EmeraldLight.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (arSessionState) {
                                    is CameraViewArState.Tracking -> EmeraldLight
                                    is CameraViewArState.SearchingForSurfaces -> Color(0xFFFFB74D)
                                    is CameraViewArState.FallbackCameraX -> Color(0xFF00E5FF)
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = when (val s = arSessionState) {
                            is CameraViewArState.Tracking -> if (detectedObjects.isNotEmpty()) "ARCore + MLKit (${detectedObjects.size} objects)" else "ARCore Active (${s.planeCount} planes)"
                            is CameraViewArState.SearchingForSurfaces -> if (detectedObjects.isNotEmpty()) "MLKit Active (${detectedObjects.size} objects)" else "Scanning surfaces..."
                            is CameraViewArState.FallbackCameraX -> if (detectedObjects.isNotEmpty()) "MLKit Active (${detectedObjects.size} objects)" else "Live Feed Active"
                            is CameraViewArState.Initializing -> "Starting ARCore..."
                            is CameraViewArState.SessionCreated -> "AR Scene Ready"
                            is CameraViewArState.Error -> "AR Status"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        overlayContent?.invoke(this)
        }
    }
}

/**
 * CamelCase alias for ARCameraPreview.
 */
@Composable
fun ArCameraPreview(
    modifier: Modifier = Modifier,
    enablePlaneDetection: Boolean = true,
    enableSpatialAnchors: Boolean = true,
    enableObjectDetection: Boolean = true,
    showBoundingBoxOverlay: Boolean = true,
    showStatusBadge: Boolean = true,
    selectedTrackingId: Int? = null,
    onSessionInitialized: ((Session) -> Unit)? = null,
    onPlaneDetected: ((List<Plane>) -> Unit)? = null,
    onAnchorUpdated: ((List<ArGLSurfaceView.ProjectedAnchor>) -> Unit)? = null,
    onFrameImageAvailable: ((Image, Int) -> Unit)? = null,
    onObjectsDetected: ((List<RawDetectedObject>) -> Unit)? = null,
    onObjectSelected: ((RawDetectedObject?) -> Unit)? = null,
    onTapOnPlane: ((Anchor) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    ARCameraPreview(
        modifier = modifier,
        enablePlaneDetection = enablePlaneDetection,
        enableSpatialAnchors = enableSpatialAnchors,
        enableObjectDetection = enableObjectDetection,
        showBoundingBoxOverlay = showBoundingBoxOverlay,
        showStatusBadge = showStatusBadge,
        selectedTrackingId = selectedTrackingId,
        onSessionInitialized = onSessionInitialized,
        onPlaneDetected = onPlaneDetected,
        onAnchorUpdated = onAnchorUpdated,
        onFrameImageAvailable = onFrameImageAvailable,
        onObjectsDetected = onObjectsDetected,
        onObjectSelected = onObjectSelected,
        onTapOnPlane = onTapOnPlane,
        onTap = onTap,
        overlayContent = overlayContent
    )
}

