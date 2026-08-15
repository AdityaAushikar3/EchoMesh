package chat.bitchat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin

private data class FloatingParticle(
    val relX: Float,
    val relY: Float,
    val radius: Float,
    val speed: Float,
    val angle: Float,
    val alpha: Float
)

@Composable
fun AntigravityAmbientBackground(
    modifier: Modifier = Modifier,
    particleCount: Int = 24,
    color: Color = EchoAccent
) {
    val reduced = rememberReducedMotion()
    if (reduced) return

    val transition = rememberInfiniteTransition(label = "antigravityField")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(120000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val particles = remember(particleCount) {
        List(particleCount) { index ->
            // Use golden ratio dispersion for uniform, organic distribution
            val relX = ((index * 0.6180339887f) % 1.0f).coerceIn(0.05f, 0.95f)
            val relY = ((index * 0.4142135623f + 0.13f) % 1.0f).coerceIn(0.05f, 0.95f)
            FloatingParticle(
                relX = relX,
                relY = relY,
                radius = 1.5f + (index % 4).toFloat() * 1.2f,
                speed = 0.3f + (index % 5) * 0.15f,
                angle = (index * 47f) * (Math.PI / 180f).toFloat(),
                alpha = 0.12f + (index % 3) * 0.08f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        particles.forEach { p ->
            val baseX = p.relX * w
            val baseY = p.relY * h

            val driftX = cos(p.angle + time * 0.003f * p.speed) * (25f * p.speed)
            val driftY = sin(p.angle + time * 0.004f * p.speed) * (30f * p.speed)

            var currentX = (baseX + driftX) % w
            var currentY = (baseY + driftY) % h

            if (currentX < 0f) currentX += w
            if (currentY < 0f) currentY += h

            drawCircle(
                color = color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(currentX, currentY)
            )
        }
    }
}
