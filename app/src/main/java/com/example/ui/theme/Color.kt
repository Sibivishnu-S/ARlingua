package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium High-Tech Language Learning Theme (Emerald & Indigo & Sunset Amber)
val EmeraldGreen = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF059669)

val IndigoPurple = Color(0xFF6366F1)
val IndigoLight = Color(0xFF818CF8)
val IndigoDark = Color(0xFF4F46E5)

val AmberGold = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFBBF24)
val AmberDark = Color(0xFFD97706)

val RosePink = Color(0xFFF43F5E)
val CyanBlue = Color(0xFF06B6D4)

val SlateBackgroundLight = Color(0xFFF8FAFC)
val SlateBackgroundDark = Color(0xFF0F172A)

val SlateSurfaceLight = Color(0xFFFFFFFF)
val SlateSurfaceDark = Color(0xFF1E293B)

val CharcoalTextDark = Color(0xFF0F172A)
val SilverTextLight = Color(0xFFF1F5F9)
val MutedTextLight = Color(0xFF64748B)
val MutedTextDark = Color(0xFF94A3B8)

// Gradients
val EmeraldGradient = Brush.horizontalGradient(listOf(EmeraldGreen, EmeraldDark))
val IndigoGradient = Brush.horizontalGradient(listOf(IndigoLight, IndigoPurple))
val AmberGradient = Brush.horizontalGradient(listOf(AmberLight, AmberGold))
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF312E81)))
val GlassBorderGradient = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f)))

