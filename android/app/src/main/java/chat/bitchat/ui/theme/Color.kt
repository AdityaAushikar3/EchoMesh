package chat.bitchat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// CompositionLocal to provide dark/light theme state down the layout hierarchy
val LocalDarkTheme = staticCompositionLocalOf { true }

// Raw Color Constants for Dark Theme (Refined Charcoal & Warm Amber Accent)
private val DarkVoid = Color(0xFF0E1116)          // Deep clean charcoal background
private val DarkInk = Color(0xFF141820)           // Surface neutral
private val DarkSurface = Color(0xFF191F2A)       // Primary card/surface container
private val DarkElevated = Color(0xFF222A38)      // Elevated components, inputs, buttons
private val DarkHairline = Color(0xFF2E384A)      // Subtle 1dp structural dividing border

private val DarkTextPrimary = Color(0xFFF3F4F6)   // Pure high-legibility off-white
private val DarkTextSecondary = Color(0xFF9CA3AF) // Calm, readable secondary text
private val DarkTextTertiary = Color(0xFF6B7280)  // Subtle metadata & timestamps

private val DarkAccent = Color(0xFFF59E0B)       // Warm Amber (Intentional, approachable, human)
private val DarkAccentSoft = Color(0xFFD97706)   // Deeper Amber
private val DarkAccentMuted = Color(0xFF2E220D)  // Muted Amber container
private val DarkSuccess = Color(0xFF10B981)     // Emerald green for verified link / delivery
private val DarkWarning = Color(0xFFF59E0B)     // Amber warning
private val DarkDanger = Color(0xFFEF4444)      // Crimson danger / block

// Raw Color Constants for Light Theme (Clean Modern Daylight)
private val LightVoid = Color(0xFFF8F9FA)        // Crisp light canvas
private val LightInk = Color(0xFFF1F3F5)
private val LightSurface = Color(0xFFFFFFFF)     // Pure white surfaces
private val LightElevated = Color(0xFFEEF0F3)    // Soft gray containers
private val LightHairline = Color(0xFFDDE1E8)    // Gentle dividers

private val LightTextPrimary = Color(0xFF111827)  // Deep slate near-black
private val LightTextSecondary = Color(0xFF4B5563)// Slate secondary
private val LightTextTertiary = Color(0xFF9CA3AF) // Muted tertiary

private val LightAccent = Color(0xFFD97706)      // Deep amber with strong light background contrast
private val LightAccentSoft = Color(0xFFB45309)
private val LightAccentMuted = Color(0xFFFEF3C7) // Soft amber container
private val LightSuccess = Color(0xFF059669)     // Emerald green
private val LightWarning = Color(0xFFD97706)     // Amber warning
private val LightDanger = Color(0xFFDC2626)      // Crimson danger

// Dynamic getters resolving colors based on active theme setting
val EchoVoid: Color @Composable get() = if (LocalDarkTheme.current) DarkVoid else LightVoid
val EchoInk: Color @Composable get() = if (LocalDarkTheme.current) DarkInk else LightInk
val EchoSurface: Color @Composable get() = if (LocalDarkTheme.current) DarkSurface else LightSurface
val EchoElevated: Color @Composable get() = if (LocalDarkTheme.current) DarkElevated else LightElevated
val EchoHairline: Color @Composable get() = if (LocalDarkTheme.current) DarkHairline else LightHairline

val EchoTextPrimary: Color @Composable get() = if (LocalDarkTheme.current) DarkTextPrimary else LightTextPrimary
val EchoTextSecondary: Color @Composable get() = if (LocalDarkTheme.current) DarkTextSecondary else LightTextSecondary
val EchoTextTertiary: Color @Composable get() = if (LocalDarkTheme.current) DarkTextTertiary else LightTextTertiary
val EchoTextOnAccent: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF181205) else Color.White

val EchoAccent: Color @Composable get() = if (LocalDarkTheme.current) DarkAccent else LightAccent
val EchoAccentSoft: Color @Composable get() = if (LocalDarkTheme.current) DarkAccentSoft else LightAccentSoft
val EchoAccentMuted: Color @Composable get() = if (LocalDarkTheme.current) DarkAccentMuted else LightAccentMuted

val EchoSuccess: Color @Composable get() = if (LocalDarkTheme.current) DarkSuccess else LightSuccess
val EchoWarning: Color @Composable get() = if (LocalDarkTheme.current) DarkWarning else LightWarning
val EchoDanger: Color @Composable get() = if (LocalDarkTheme.current) DarkDanger else LightDanger

val EchoYou: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFE5E7EB) else Color(0xFF374151)

// Balanced human avatar color palette
val AvatarSlate = Color(0xFF64748B)
val AvatarTerracotta = Color(0xFFC25E4A)
val AvatarMoss = Color(0xFF4D7C5D)
val AvatarOchre = Color(0xFFB8860B)
val AvatarDenim = Color(0xFF4A729A)
val AvatarPlum = Color(0xFF8B5E83)
val AvatarRust = Color(0xFFA0522D)
val AvatarSage = Color(0xFF5F8575)
