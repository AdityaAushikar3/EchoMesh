package chat.bitchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EchoDarkScheme = darkColorScheme(
    primary = EchoAccent,
    onPrimary = EchoTextPrimary,
    primaryContainer = EchoAccentMuted,
    onPrimaryContainer = EchoTextPrimary,
    secondary = EchoTextSecondary,
    onSecondary = EchoInk,
    tertiary = EchoSuccess,
    background = EchoVoid,
    onBackground = EchoTextPrimary,
    surface = EchoSurface,
    onSurface = EchoTextPrimary,
    surfaceVariant = EchoElevated,
    onSurfaceVariant = EchoTextSecondary,
    outline = EchoHairline,
    outlineVariant = EchoHairline.copy(alpha = 0.5f),
    error = EchoDanger,
    onError = EchoTextPrimary,
    errorContainer = Color(0xFF3A1A20),
    onErrorContainer = EchoDanger
)

private val EchoLightScheme = lightColorScheme(
    primary = Color(0xFF3B6FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFF),
    onPrimaryContainer = Color(0xFF102048),
    secondary = Color(0xFF5A6478),
    onSecondary = Color.White,
    tertiary = Color(0xFF1FA876),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF10131A),
    surface = Color.White,
    onSurface = Color(0xFF10131A),
    surfaceVariant = Color(0xFFEBEEF4),
    onSurfaceVariant = Color(0xFF5A6478),
    outline = Color(0xFFD5DAE4),
    error = EchoDanger,
    onError = Color.White
)

@Composable
fun EchoMeshTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme || isSystemInDarkTheme()) EchoDarkScheme else EchoLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
