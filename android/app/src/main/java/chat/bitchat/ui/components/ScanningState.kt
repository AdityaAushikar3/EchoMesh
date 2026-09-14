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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.rememberReducedMotion

@Composable
fun ScanningState(
    modifier: Modifier = Modifier,
    label: String = "Finding nearby people",
    isScanning: Boolean = true
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "scanningSweep")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val accent = EchoAccent
    val hairline = EchoHairline

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
            Canvas(modifier = Modifier.size(110.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f

                // Clean rings
                listOf(0.35f, 0.70f, 1.0f).forEach { fraction ->
                    drawCircle(
                        color = hairline,
                        radius = maxR * fraction,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Expanding pulse ring (when listening)
                if (isScanning && !reduced && pulseAlpha > 0f) {
                    drawCircle(
                        color = accent.copy(alpha = pulseAlpha * 0.4f),
                        radius = maxR * pulseScale,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // Subtle sweep line
                if (isScanning && !reduced) {
                    rotate(sweepAngle, pivot = center) {
                        drawLine(
                            color = accent.copy(alpha = 0.6f),
                            start = center,
                            end = Offset(center.x, center.y - maxR),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // Center origin dot
                drawCircle(
                    color = if (isScanning) accent else hairline,
                    radius = 3.5.dp.toPx(),
                    center = center
                )
            }
        }
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Text(
            text = if (isScanning) label else "Scanning Paused",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = EchoTextPrimary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xxs))
        Text(
            text = if (isScanning) "Listening for offline mesh signals" else "Tap Listen in the top bar to discover peers",
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextSecondary
        )
    }
}
