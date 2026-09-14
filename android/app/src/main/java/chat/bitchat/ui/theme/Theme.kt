package chat.bitchat.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material Color Schemes
private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF181205),
    primaryContainer = Color(0xFF2E220D),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFF9CA3AF),
    onSecondary = Color(0xFF111827),
    tertiary = Color(0xFF10B981),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF191F2A),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF222A38),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2E384A),
    outlineVariant = Color(0xFF2E384A).copy(alpha = 0.5f),
    error = Color(0xFFEF4444),
    onError = Color(0xFFF3F4F6),
    errorContainer = Color(0xFF451A1A),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF92400E),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFDDE1E8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFDC2626)
)

@Composable
fun EchoMeshTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MaterialDarkScheme else MaterialLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
