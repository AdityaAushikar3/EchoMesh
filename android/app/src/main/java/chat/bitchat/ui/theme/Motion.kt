package chat.bitchat.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object EchoMotion {
    val SoftEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val GentleEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    fun <T> soft(durationMs: Int = 420) = tween<T>(durationMs, easing = SoftEase)
    fun <T> gentle(durationMs: Int = 280) = tween<T>(durationMs, easing = GentleEase)
    fun <T> slow(durationMs: Int = 900) = tween<T>(durationMs, easing = SoftEase)
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}
