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
import androidx.compose.material.icons.filled.*
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
import com.example.service.CameraService
import com.example.service.CameraStatus
import com.example.ui.theme.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.*

private const val TAG = "ARCamera"

/**
 * State seal representing the ARCore session lifecycle & tracking state.
 */
sealed class ARSessionState {
    object Initializing : ARSessionState()
    object SessionCreated : ARSessionState()
    data class Tracking(val planeCount: Int) : ARSessionState()
    object SearchingForSurfaces : ARSessionState()
    data class Error(val message: String, val isInstallRequired: Boolean = false) : ARSessionState()
    object FallbackCameraX : ARSessionState()
}

/**
 * ARCamera Composable:
 * Initializes the ARCore session, configures camera tracking, renders the high-performance
 * camera preview stream, manages lifecycle (resume/pause/close), and provides interactive callbacks
 * tailored for real-world language learning (tap identification, 3D spatial anchor tracking,
 * plane detection, and frame image processing).
 */
@Composable
fun ARCamera(
    modifier: Modifier = Modifier,
    enablePlaneDetection: Boolean = true,
    enableSpatialAnchors: Boolean = true,
    onSessionInitialized: ((Session) -> Unit)? = null,
    onPlaneDetected: ((List<Plane>) -> Unit)? = null,
    onAnchorUpdated: ((List<ArGLSurfaceView.ProjectedAnchor>) -> Unit)? = null,
    onFrameImageAvailable: ((Image, Int) -> Unit)? = null,
    onFrameBitmapAvailable: ((android.graphics.Bitmap, Int, Int, Int) -> Unit)? = null,
    onTapOnPlane: ((Anchor) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    CameraView(
        modifier = modifier,
        enablePlaneDetection = enablePlaneDetection,
        enableSpatialAnchors = enableSpatialAnchors,
        onSessionInitialized = onSessionInitialized,
        onPlaneDetected = onPlaneDetected,
        onAnchorUpdated = onAnchorUpdated,
        onFrameImageAvailable = onFrameImageAvailable,
        onFrameBitmapAvailable = onFrameBitmapAvailable,
        onTapOnPlane = onTapOnPlane,
        onTap = onTap,
        overlayContent = overlayContent
    )
}

// Alias for camelCase naming
@Composable
fun ArCamera(
    modifier: Modifier = Modifier,
    enablePlaneDetection: Boolean = true,
    enableSpatialAnchors: Boolean = true,
    onSessionInitialized: ((Session) -> Unit)? = null,
    onPlaneDetected: ((List<Plane>) -> Unit)? = null,
    onAnchorUpdated: ((List<ArGLSurfaceView.ProjectedAnchor>) -> Unit)? = null,
    onFrameImageAvailable: ((Image, Int) -> Unit)? = null,
    onFrameBitmapAvailable: ((android.graphics.Bitmap, Int, Int, Int) -> Unit)? = null,
    onTapOnPlane: ((Anchor) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    ARCamera(
        modifier = modifier,
        enablePlaneDetection = enablePlaneDetection,
        enableSpatialAnchors = enableSpatialAnchors,
        onSessionInitialized = onSessionInitialized,
        onPlaneDetected = onPlaneDetected,
        onAnchorUpdated = onAnchorUpdated,
        onFrameImageAvailable = onFrameImageAvailable,
        onFrameBitmapAvailable = onFrameBitmapAvailable,
        onTapOnPlane = onTapOnPlane,
        onTap = onTap,
        overlayContent = overlayContent
    )
}
