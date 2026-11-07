package com.example.ui

import android.app.Activity
import android.content.Context
import android.media.Image
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.ArCoreManager
import com.example.service.CameraService
import com.example.ui.theme.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.*

private const val TAG = "CameraView"

/**
 * State seal representing the ARCore session lifecycle & tracking state in CameraView.
 */
sealed class CameraViewArState {
    object Initializing : CameraViewArState()
    object SessionCreated : CameraViewArState()
    data class Tracking(val planeCount: Int) : CameraViewArState()
    object SearchingForSurfaces : CameraViewArState()
    data class Error(val message: String, val isInstallRequired: Boolean = false) : CameraViewArState()
    object FallbackCameraX : CameraViewArState()
}

/**
 * CameraView Composable:
 * Initializes ARCore, sets up the AR scene and OpenGL surface, allowing the camera preview
 * to be visible in the Compose UI. Includes automatic fallback to CameraX for devices/emulators
 * lacking ARCore hardware support, lifecycle management, tap detection, plane updates,
 * and custom Compose overlay rendering.
 */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    enablePlaneDetection: Boolean = true,
    enableSpatialAnchors: Boolean = true,
    showStatusHud: Boolean = true,
    onSessionInitialized: ((Session) -> Unit)? = null,
    onPlaneDetected: ((List<Plane>) -> Unit)? = null,
    onAnchorUpdated: ((List<ArGLSurfaceView.ProjectedAnchor>) -> Unit)? = null,
    onFrameImageAvailable: ((Image, Int) -> Unit)? = null,
    onFrameBitmapAvailable: ((android.graphics.Bitmap, Int, Int, Int) -> Unit)? = null,
    onTapOnPlane: ((Anchor) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraService = remember { CameraService(context) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCameraActive by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        Log.i(TAG, "AR_CAMERA: initializing screen lifecycle observer...")
        onDispose {
            Log.i(TAG, "AR_CAMERA: releasing camera on lifecycle dispose...")
            cameraService.stopCamera()
            cameraService.release()
            isCameraActive = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_view_preview_container")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTap?.invoke(offset)
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                Log.i(TAG, "AR_CAMERA: preview created in AndroidView factory")
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                Log.i(TAG, "AR_CAMERA: initializing CameraX pipeline...")
                cameraError = null
                cameraService.startCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onFrameAvailable = { bitmap, rot, w, h ->
                        if (!isCameraActive) {
                            Log.i(TAG, "AR_CAMERA: READY - live frame streaming active")
                            isCameraActive = true
                        }
                        onFrameBitmapAvailable?.invoke(bitmap, rot, w, h)
                    },
                    onError = { err ->
                        Log.e(TAG, "AR_CAMERA: ERROR - ${err.message}")
                        cameraError = err.message
                        isCameraActive = false
                    }
                )

                previewView
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                Log.i(TAG, "AR_CAMERA: AndroidView released, stopping camera")
                cameraService.stopCamera()
            }
        )

        if (cameraError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Camera Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "CameraX Error",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = cameraError ?: "Failed to open camera.",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Status Pill HUD
        if (showStatusHud) {
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
                            .background(if (isCameraActive) EmeraldLight else Color.Yellow)
                    )
                    Text(
                        text = if (isCameraActive) "Camera ready — tap an object" else "CAMERA_INITIALIZING",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Render user overlay content over camera preview
        overlayContent?.invoke(this)
    }
}
