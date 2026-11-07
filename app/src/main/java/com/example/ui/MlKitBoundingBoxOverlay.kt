package com.example.ui

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.service.RawDetectedObject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.SlateBackgroundDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Triggers subtle tactile haptic feedback when an object overlay is identified and generated.
 */
fun triggerObjectIdentificationHaptic(context: Context, hapticFeedback: HapticFeedback?) {
    try {
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    } catch (_: Exception) {}
}

/**
 * State holder for smooth spring-based bounding box & label animations.
 */
private class AnimatedObjectState(
    val key: String,
    initialBox: RectF,
    val coroutineScope: CoroutineScope
) {
    var rawObject by mutableStateOf<RawDetectedObject?>(null)

    val animLeft = Animatable(initialBox.left)
    val animTop = Animatable(initialBox.top)
    val animRight = Animatable(initialBox.right)
    val animBottom = Animatable(initialBox.bottom)

    val animAlpha = Animatable(0f)
    val animScale = Animatable(0.75f)

    var isRemoving by mutableStateOf(false)

    fun updateTarget(
        obj: RawDetectedObject,
        springPosSpec: AnimationSpec<Float>,
        springScaleSpec: AnimationSpec<Float>,
        springAlphaSpec: AnimationSpec<Float>
    ) {
        rawObject = obj
        isRemoving = false

        val targetBox = obj.boundingBox
        coroutineScope.launch {
            launch { animLeft.animateTo(targetBox.left, springPosSpec) }
            launch { animTop.animateTo(targetBox.top, springPosSpec) }
            launch { animRight.animateTo(targetBox.right, springPosSpec) }
            launch { animBottom.animateTo(targetBox.bottom, springPosSpec) }
            launch { animAlpha.animateTo(1f, springAlphaSpec) }
            launch { animScale.animateTo(1f, springScaleSpec) }
        }
    }

    fun dismiss(
        springAlphaSpec: AnimationSpec<Float>,
        springScaleSpec: AnimationSpec<Float>,
        onDismissed: () -> Unit
    ) {
        if (isRemoving) return
        isRemoving = true
        coroutineScope.launch {
            val job1 = launch { animAlpha.animateTo(0f, springAlphaSpec) }
            val job2 = launch { animScale.animateTo(0.75f, springScaleSpec) }
            job1.join()
            job2.join()
            onDismissed()
        }
    }
}

/**
 * Canvas-based overlay UI that renders bounding boxes in real-time over the camera preview
 * based on data provided by the MLKit Object Detection frame processor.
 * 
 * Features:
 * - Real-time Compose Canvas rendering of MLKit bounding boxes.
 * - Bouncy spring-based enter/leave/tracking animations for overlay labels & boxes.
 * - Corner bracket highlights with glowing animated accents.
 * - Dynamic color coding based on detection confidence score.
 * - Crisp text label badges with object class name and percentage confidence.
 * - Scaled coordinate transformation from MLKit camera frame bounds to view surface space.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun MlKitBoundingBoxOverlay(
    modifier: Modifier = Modifier,
    detectedObjects: List<RawDetectedObject>,
    frameWidth: Int = 1080,
    frameHeight: Int = 1920,
    selectedTrackingId: Int? = null,
    showLabels: Boolean = true,
    accentColor: Color = EmeraldLight
) {
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val activeStatesMap = remember { mutableStateMapOf<String, AnimatedObjectState>() }

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Spring specs for springy bounding box & label entry/exit transitions
    val springPosSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
    val springScaleSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
    val springAlphaSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    // Spring-driven state updater when frame detections change
    LaunchedEffect(detectedObjects) {
        val currentKeys = detectedObjects.map { obj ->
            obj.trackingId?.toString() ?: "${obj.label}_${(obj.boundingBox.centerX() / 60).toInt()}_${(obj.boundingBox.centerY() / 60).toInt()}"
        }.toSet()

        var hasNewlyIdentifiedObject = false

        // Update existing or insert new entering objects
        for (obj in detectedObjects) {
            val key = obj.trackingId?.toString() ?: "${obj.label}_${(obj.boundingBox.centerX() / 60).toInt()}_${(obj.boundingBox.centerY() / 60).toInt()}"
            val existing = activeStatesMap[key]
            if (existing != null) {
                existing.updateTarget(obj, springPosSpec, springScaleSpec, springAlphaSpec)
            } else {
                hasNewlyIdentifiedObject = true
                val newState = AnimatedObjectState(key, obj.boundingBox, coroutineScope)
                activeStatesMap[key] = newState
                newState.updateTarget(obj, springPosSpec, springScaleSpec, springAlphaSpec)
            }
        }

        if (hasNewlyIdentifiedObject) {
            triggerObjectIdentificationHaptic(context, hapticFeedback)
        }

        // Spring dismiss objects leaving the camera frame
        val keysToRemove = activeStatesMap.keys.filter { it !in currentKeys }
        for (key in keysToRemove) {
            activeStatesMap[key]?.dismiss(springAlphaSpec, springScaleSpec) {
                activeStatesMap.remove(key)
            }
        }
    }

    // Infinite transition for glowing corner pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("mlkit_bounding_box_overlay_canvas")
    ) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activeStatesMap.isEmpty()) return@Canvas

            // Calculate scale factors to map frame coordinates to screen canvas coordinates
            val scaleX = viewWidth / frameWidth.coerceAtLeast(1).toFloat()
            val scaleY = viewHeight / frameHeight.coerceAtLeast(1).toFloat()

            for (state in activeStatesMap.values) {
                val obj = state.rawObject ?: continue
                val alphaVal = state.animAlpha.value
                val scaleVal = state.animScale.value

                if (alphaVal <= 0.01f) continue

                // Scaled animated bounding box coordinates
                val left = state.animLeft.value * scaleX
                val top = state.animTop.value * scaleY
                val right = state.animRight.value * scaleX
                val bottom = state.animBottom.value * scaleY

                val width = right - left
                val height = bottom - top

                if (width <= 0 || height <= 0) continue

                val isSelected = selectedTrackingId != null && obj.trackingId == selectedTrackingId

                // Determine dynamic box color based on confidence & selection state
                val boxColor = when {
                    isSelected -> EmeraldLight
                    obj.confidence >= 0.75f -> EmeraldGreen
                    obj.confidence >= 0.50f -> AmberGold
                    else -> Color(0xFF29B6F6) // Cyan accent for lower threshold detections
                }

                val strokeWidth = if (isSelected) 8f else 5f
                val boxAlpha = if (isSelected) 1.0f else pulseAlpha

                val centerX = (left + right) / 2f
                val centerY = (top + bottom) / 2f

                // Draw bounding box and spring-animated scaled label around box center
                scale(scale = scaleVal, pivot = Offset(centerX, centerY)) {
                    // 1. Draw semi-transparent background fill inside bounding box
                    drawRect(
                        color = boxColor.copy(alpha = 0.12f * alphaVal),
                        topLeft = Offset(left, top),
                        size = Size(width, height)
                    )

                    // 2. Draw dashed main bounding box outline
                    drawRoundRect(
                        color = boxColor.copy(alpha = boxAlpha * alphaVal),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(12f, 12f),
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                        )
                    )

                    // 3. Draw 4 glowing corner brackets for futuristic AR feel
                    val bracketLen = (minOf(width, height) * 0.25f).coerceIn(16f, 40f)
                    val bracketStroke = strokeWidth + 2f

                    // Top-Left corner
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(left, top), Offset(left + bracketLen, top), strokeWidth = bracketStroke)
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(left, top), Offset(left, top + bracketLen), strokeWidth = bracketStroke)

                    // Top-Right corner
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(right, top), Offset(right - bracketLen, top), strokeWidth = bracketStroke)
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(right, top), Offset(right, top + bracketLen), strokeWidth = bracketStroke)

                    // Bottom-Left corner
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth = bracketStroke)
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(left, bottom), Offset(left, bottom - bracketLen), strokeWidth = bracketStroke)

                    // Bottom-Right corner
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(right, bottom), Offset(right - bracketLen, bottom), strokeWidth = bracketStroke)
                    drawLine(boxColor.copy(alpha = alphaVal), Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth = bracketStroke)

                    // 4. Draw Label Badge with Object Name and Confidence Score
                    if (showLabels) {
                        val confPercent = (obj.confidence * 100).toInt()
                        val labelText = "${obj.label} ($confPercent%)"

                        val textStyle = TextStyle(
                            color = Color.White.copy(alpha = alphaVal),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val measuredText = textMeasurer.measure(labelText, textStyle)
                        val badgePaddingX = 16f
                        val badgePaddingY = 8f
                        val badgeWidth = measuredText.size.width + badgePaddingX * 2
                        val badgeHeight = measuredText.size.height + badgePaddingY * 2

                        val badgeTop = if (top - badgeHeight - 6f >= 0) top - badgeHeight - 6f else top + 6f
                        val badgeLeft = left.coerceIn(0f, viewWidth - badgeWidth)

                        // Draw pill background for label
                        drawRoundRect(
                            color = SlateBackgroundDark.copy(alpha = 0.90f * alphaVal),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        // Draw pill border
                        drawRoundRect(
                            color = boxColor.copy(alpha = alphaVal),
                            topLeft = Offset(badgeLeft, badgeTop),
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 2f)
                        )

                        // Draw text inside label pill
                        drawText(
                            textLayoutResult = measuredText,
                            topLeft = Offset(badgeLeft + badgePaddingX, badgeTop + badgePaddingY)
                        )
                    }
                }
            }
        }
    }
}

