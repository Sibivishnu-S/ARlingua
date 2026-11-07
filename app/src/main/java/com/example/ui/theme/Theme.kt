package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = IndigoPurple,
    tertiary = AmberGold,
    background = SlateBackgroundDark,
    surface = SlateSurfaceDark,
    onPrimary = SilverTextLight,
    onSecondary = SilverTextLight,
    onTertiary = CharcoalTextDark,
    onBackground = SilverTextLight,
    onSurface = SilverTextLight
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    secondary = IndigoPurple,
    tertiary = AmberGold,
    background = SlateBackgroundLight,
    surface = SlateSurfaceLight,
    onPrimary = SlateSurfaceLight,
    onSecondary = SlateSurfaceLight,
    onTertiary = CharcoalTextDark,
    onBackground = CharcoalTextDark,
    onSurface = CharcoalTextDark
)

val CustomTextSelectionColors = TextSelectionColors(
    handleColor = Color(0xFF2563EB),
    backgroundColor = Color(0xFF2563EB).copy(alpha = 0.3f)
)

object AppTextFieldDefaults {
    @Composable
    fun colors(
        focusedContainerColor: Color = Color.Transparent,
        unfocusedContainerColor: Color = Color.Transparent,
        focusedIndicatorColor: Color = Color(0xFF2563EB),
        unfocusedIndicatorColor: Color = Color(0xFF94A3B8),
        disabledIndicatorColor: Color = Color.Transparent,
        errorIndicatorColor: Color = Color(0xFFEF4444)
    ) = TextFieldDefaults.colors(
        focusedTextColor = Color(0xFF2563EB),
        unfocusedTextColor = Color(0xFF2563EB),
        cursorColor = Color(0xFF2563EB),
        errorCursorColor = Color(0xFFEF4444),
        focusedContainerColor = focusedContainerColor,
        unfocusedContainerColor = unfocusedContainerColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        disabledIndicatorColor = disabledIndicatorColor,
        errorIndicatorColor = errorIndicatorColor,
        focusedLabelColor = Color(0xFF2563EB),
        unfocusedLabelColor = Color(0xFF64748B),
        focusedPlaceholderColor = Color(0xFF94A3B8),
        unfocusedPlaceholderColor = Color(0xFF94A3B8),
        errorTextColor = Color(0xFFEF4444),
        errorLabelColor = Color(0xFFEF4444),
        errorSupportingTextColor = Color(0xFFEF4444)
    )

    @Composable
    fun outlinedColors(
        focusedBorderColor: Color = Color(0xFF2563EB),
        unfocusedBorderColor: Color = Color(0xFF94A3B8),
        errorBorderColor: Color = Color(0xFFEF4444)
    ) = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF2563EB),
        unfocusedTextColor = Color(0xFF2563EB),
        cursorColor = Color(0xFF2563EB),
        errorCursorColor = Color(0xFFEF4444),
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        errorBorderColor = errorBorderColor,
        focusedLabelColor = Color(0xFF2563EB),
        unfocusedLabelColor = Color(0xFF64748B),
        focusedPlaceholderColor = Color(0xFF94A3B8),
        unfocusedPlaceholderColor = Color(0xFF94A3B8),
        errorTextColor = Color(0xFFEF4444),
        errorLabelColor = Color(0xFFEF4444),
        errorSupportingTextColor = Color(0xFFEF4444)
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep brand identity strong by default, but allow dynamic override
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  CompositionLocalProvider(
      LocalTextSelectionColors provides CustomTextSelectionColors
  ) {
      MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
