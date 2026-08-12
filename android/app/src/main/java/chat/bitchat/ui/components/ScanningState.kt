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

@Composable
fun ScanningState(
    modifier: Modifier = Modifier,
    label: String = "Finding people"
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val maxR = size.minDimension / 2f
                drawCircle(
                    color = EchoAccent.copy(alpha = alpha * 0.55f),
                    radius = maxR * pulse,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = EchoAccent.copy(alpha = 0.7f),
                    radius = 5.dp.toPx()
                )
            }
        }
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextTertiary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xxs))
        Text(
            text = "Listening for people nearby",
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
