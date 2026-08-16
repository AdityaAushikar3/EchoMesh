package chat.bitchat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// CompositionLocal to provide dark/light theme state down the layout hierarchy
val LocalDarkTheme = staticCompositionLocalOf { true }

// Raw Color Constants for Dark Theme (Cyber tactical OLED)
private val DarkVoid = Color(0xFF07090E)
private val DarkInk = Color(0xFF0C0F17)
private val DarkSurface = Color(0xFF101520)
private val DarkElevated = Color(0xFF161E2E)
private val DarkHairline = Color(0xFF222D42)

private val DarkTextPrimary = Color(0xFFF4F6FA)
private val DarkTextSecondary = Color(0xFF9AA3B5)
private val DarkTextTertiary = Color(0xFF6B7385)

private val DarkAccent = Color(0xFF00F2FE)       // Electric Cyan
private val DarkAccentSoft = Color(0xFF0072FF)   // Deep Blue
private val DarkAccentMuted = Color(0xFF0C2448)  // Navy
private val DarkSuccess = Color(0xFF34D399)     // Warmer mint green (updated from 0xFF00FFA3)
private val DarkWarning = Color(0xFFFFB000)
private val DarkDanger = Color(0xFFFF4560)

// Raw Color Constants for Light Theme (Sleek Clean Daylight)
private val LightVoid = Color(0xFFF5F6F8)        // Soft gray-white background
private val LightInk = Color(0xFFEBEEF4)
private val LightSurface = Color(0xFFFFFFFF)     // Pure white surfaces
private val LightElevated = Color(0xFFF0F2F6)    // Elevated surface cards
private val LightHairline = Color(0xFFD5DAE4)    // Slate borders

private val LightTextPrimary = Color(0xFF0F172A)  // Dark Slate / Near Black
private val LightTextSecondary = Color(0xFF475569)// Muted Slate
private val LightTextTertiary = Color(0xFF64748B) // Pale Slate

private val LightAccent = Color(0xFF0284C7)      // Ocean Blue/Cyan for light bg readability
private val LightAccentSoft = Color(0xFF0369A1)
private val LightAccentMuted = Color(0xFFE0F2FE) // Very light blue container
private val LightSuccess = Color(0xFF10B981)     // Warm emerald green
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
val EchoTextOnAccent: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF07090E) else Color.White

val EchoAccent: Color @Composable get() = if (LocalDarkTheme.current) DarkAccent else LightAccent
val EchoAccentSoft: Color @Composable get() = if (LocalDarkTheme.current) DarkAccentSoft else LightAccentSoft
val EchoAccentMuted: Color @Composable get() = if (LocalDarkTheme.current) DarkAccentMuted else LightAccentMuted

val EchoSuccess: Color @Composable get() = if (LocalDarkTheme.current) DarkSuccess else LightSuccess
val EchoWarning: Color @Composable get() = if (LocalDarkTheme.current) DarkWarning else LightWarning
val EchoDanger: Color @Composable get() = if (LocalDarkTheme.current) DarkDanger else LightDanger

val EchoYou: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFE8ECF4) else Color(0xFF334155)

// Static Avatar colors (work beautifully on both light and dark backgrounds)
val AvatarCoral = Color(0xFFE07A6A)
val AvatarSage = Color(0xFF6BA88A)
val AvatarSky = Color(0xFF6A9BCF)
val AvatarSand = Color(0xFFC4A574)
val AvatarLilac = Color(0xFF9B8BC4)
val AvatarSlate = Color(0xFF7A8799)
val AvatarRose = Color(0xFFC47A96)
val AvatarTeal = Color(0xFF5FA8A0)
