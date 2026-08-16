package chat.bitchat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.rememberReducedMotion

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun ScanningState(
    modifier: Modifier = Modifier,
    label: String = "Finding people",
    isScanning: Boolean = true
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val accent = EchoAccent
    val textTertiary = EchoTextTertiary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f

                // Concentric radar grid rings
                listOf(0.35f, 0.68f, 1.0f).forEach { fraction ->
                    drawCircle(
                        color = accent.copy(alpha = 0.12f),
                        radius = maxR * fraction,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Expanding pulse ring (only when scanning is ON)
                if (isScanning && !reduced) {
                    drawCircle(
                        color = accent.copy(alpha = alpha * 0.55f),
                        radius = maxR * pulse,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // Sweeping holographic radar line (only when scanning is ON)
                if (isScanning && !reduced) {
                    rotate(sweepAngle, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.03f),
                                    accent.copy(alpha = 0.35f)
                                ),
                                center = center
                            ),
                            radius = maxR,
                            center = center
                        )
                    }
                }

                // Central glowing beacon dot
                drawCircle(
                    color = accent.copy(alpha = if (isScanning) 0.35f else 0.15f),
                    radius = 8.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = if (isScanning) accent else textTertiary,
                    radius = 3.5.dp.toPx(),
                    center = center
                )
            }
        }
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Text(
            text = (if (isScanning) label else "Scanner Paused").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextTertiary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xxs))
        Text(
            text = if (isScanning) "Listening for people nearby" else "Tap Listen to start scanning",
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextPrimary.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun SoftOrbitRings(
    modifier: Modifier = Modifier,
    color: Color = EchoAccent.copy(alpha = 0.12f)
) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val max = size.minDimension / 2f
        listOf(0.28f, 0.52f, 0.78f).forEach { f ->
            drawCircle(
                color = color,
                radius = max * f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
