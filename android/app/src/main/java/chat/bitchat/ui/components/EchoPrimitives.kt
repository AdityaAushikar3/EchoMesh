package chat.bitchat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoTextOnAccent
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.theme.rememberReducedMotion

@Composable
fun EchoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(EchoRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = EchoAccent,
            contentColor = EchoTextOnAccent,
            disabledContainerColor = EchoElevated,
            disabledContentColor = EchoTextTertiary
        ),
        contentPadding = PaddingValues(horizontal = EchoSpace.lg)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EchoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = text, color = EchoTextSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EchoGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = text, color = EchoTextSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EchoEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(EchoSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            illustration()
        }
        Spacer(modifier = Modifier.height(EchoSpace.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = EchoTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(EchoSpace.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextTertiary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EchoEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isScanning: Boolean = false
) {
    EchoEmptyState(
        title = title,
        subtitle = subtitle,
        modifier = modifier
    ) {
        DefaultRadarIllustration(isScanning = isScanning)
    }
}

@Composable
fun DefaultRadarIllustration(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "radarSweep")
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

    Canvas(modifier = modifier.size(110.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width / 2f

        // Concentric radar grid rings
        listOf(0.35f, 0.68f, 1.0f).forEach { fraction ->
            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = maxRadius * fraction,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Sweeping holographic beam (ONLY when listening is ON)
        if (isScanning && !reduced) {
            rotate(sweepAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.03f),
                            accent.copy(alpha = 0.28f)
                        ),
                        center = center
                    ),
                    radius = maxRadius,
                    center = center
                )
            }
        }

        // Central glowing beacon dot
        drawCircle(
            color = accent.copy(alpha = if (isScanning) 0.35f else 0.12f),
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

@Composable
fun DiscoverCompassIllustration(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "compass")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compassAngle"
    )

    val accent = EchoAccent
    val voidBg = EchoVoid

    Canvas(modifier = modifier.size(110.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.width / 2f

        // Outer ring
        drawCircle(
            color = accent.copy(alpha = 0.18f),
            radius = maxR,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Inner ring
        drawCircle(
            color = accent.copy(alpha = 0.08f),
            radius = maxR * 0.7f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // North-South-East-West ticks
        val tickLen = 6.dp.toPx()
        listOf(0f, 90f, 180f, 270f).forEach { deg ->
            rotate(deg, pivot = center) {
                drawLine(
                    color = accent.copy(alpha = 0.4f),
                    start = Offset(center.x, center.y - maxR),
                    end = Offset(center.x, center.y - maxR + tickLen),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        // Rotating compass needle
        rotate(angle, pivot = center) {
            // Draw North pointer (solid Accent)
            val pathNorth = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - maxR * 0.8f)
                lineTo(center.x - 8.dp.toPx(), center.y)
                lineTo(center.x + 8.dp.toPx(), center.y)
                close()
            }
            drawPath(pathNorth, color = accent)

            // Draw South pointer (Muted Accent)
            val pathSouth = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y + maxR * 0.8f)
                lineTo(center.x - 8.dp.toPx(), center.y)
                lineTo(center.x + 8.dp.toPx(), center.y)
                close()
            }
            drawPath(pathSouth, color = accent.copy(alpha = 0.3f))
        }

        // Center hub
        drawCircle(color = voidBg, radius = 5.dp.toPx(), center = center)
        drawCircle(color = accent, radius = 3.dp.toPx(), center = center)
    }
}

@Composable
fun ChatsEmptyIllustration(modifier: Modifier = Modifier) {
    val accent = EchoAccent
    val elevated = EchoElevated

    Canvas(modifier = modifier.size(110.dp)) {
        // Draw background bubble (Muted)
        val bgBubbleRect = androidx.compose.ui.geometry.RoundRect(
            left = 38.dp.toPx(),
            top = 22.dp.toPx(),
            right = 92.dp.toPx(),
            bottom = 62.dp.toPx(),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
        )
        val bgBubblePath = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(bgBubbleRect)
            // Tail
            moveTo(80.dp.toPx(), 62.dp.toPx())
            lineTo(88.dp.toPx(), 72.dp.toPx())
            lineTo(88.dp.toPx(), 62.dp.toPx())
            close()
        }
        drawPath(bgBubblePath, color = accent.copy(alpha = 0.12f))
        drawPath(bgBubblePath, color = accent.copy(alpha = 0.25f), style = Stroke(width = 1.dp.toPx()))

        // Draw foreground bubble (Bright)
        val fgBubbleRect = androidx.compose.ui.geometry.RoundRect(
            left = 18.dp.toPx(),
            top = 38.dp.toPx(),
            right = 72.dp.toPx(),
            bottom = 78.dp.toPx(),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
        )
        val fgBubblePath = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(fgBubbleRect)
            // Tail
            moveTo(30.dp.toPx(), 78.dp.toPx())
            lineTo(22.dp.toPx(), 88.dp.toPx())
            lineTo(22.dp.toPx(), 78.dp.toPx())
            close()
        }
        drawPath(fgBubblePath, color = elevated)
        drawPath(fgBubblePath, color = accent.copy(alpha = 0.8f), style = Stroke(width = 1.5.dp.toPx()))

        // Draw chat line signals inside foreground bubble
        drawLine(
            color = accent.copy(alpha = 0.6f),
            start = Offset(30.dp.toPx(), 52.dp.toPx()),
            end = Offset(60.dp.toPx(), 52.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = accent.copy(alpha = 0.6f),
            start = Offset(30.dp.toPx(), 62.dp.toPx()),
            end = Offset(50.dp.toPx(), 62.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun EchoSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextTertiary
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = EchoTextSecondary
            )
        }
    }
}

@Composable
fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = EchoAccent
) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (active) color else EchoTextTertiary.copy(alpha = 0.5f))
    )
}

@Composable
fun MeshStatusPill(
    peerCount: Int,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(EchoRadius.full))
            .background(if (isActive && peerCount > 0) EchoAccent.copy(alpha = 0.12f) else EchoElevated)
            .border(
                width = 1.dp,
                color = if (isActive && peerCount > 0) EchoAccent.copy(alpha = 0.35f) else EchoElevated,
                shape = RoundedCornerShape(EchoRadius.full)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            active = isActive,
            color = if (peerCount > 0) EchoAccent else EchoTextSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                peerCount > 0 -> "$peerCount ${if (peerCount == 1) "peer" else "peers"}"
                isActive -> "Mesh active"
                else -> "Mesh idle"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive && peerCount > 0) EchoAccent else EchoTextSecondary
        )
    }
}

@Composable
fun tapTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    ) {
        content()
    }
}
