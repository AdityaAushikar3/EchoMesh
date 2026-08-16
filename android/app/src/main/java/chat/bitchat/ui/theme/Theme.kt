package chat.bitchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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

// Private raw schemes to configure MaterialTheme
private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFF00F2FE),
    onPrimary = Color(0xFF07090E),
    primaryContainer = Color(0xFF0C2448),
    onPrimaryContainer = Color(0xFFF4F6FA),
    secondary = Color(0xFF9AA3B5),
    onSecondary = Color(0xFF0C0F17),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF07090E),
    onBackground = Color(0xFFF4F6FA),
    surface = Color(0xFF101520),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF161E2E),
    onSurfaceVariant = Color(0xFF9AA3B5),
    outline = Color(0xFF222D42),
    outlineVariant = Color(0xFF222D42).copy(alpha = 0.5f),
    error = Color(0xFFFF4560),
    onError = Color(0xFFF4F6FA),
    errorContainer = Color(0xFF3A1A20),
    onErrorContainer = Color(0xFFFF4560)
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    tertiary = Color(0xFF10B981),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF0F2F6),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFD5DAE4),
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
