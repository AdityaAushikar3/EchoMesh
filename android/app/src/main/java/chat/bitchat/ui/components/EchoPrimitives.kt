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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextOnAccent
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
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
            .height(48.dp),
        shape = RoundedCornerShape(EchoRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = EchoAccent,
            contentColor = EchoTextOnAccent,
            disabledContainerColor = EchoElevated,
            disabledContentColor = EchoTextTertiary
        ),
        contentPadding = PaddingValues(horizontal = EchoSpace.lg)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EchoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(EchoRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = EchoElevated,
            contentColor = EchoTextPrimary
        ),
        contentPadding = PaddingValues(horizontal = EchoSpace.lg)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EchoGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = EchoTextSecondary,
            style = MaterialTheme.typography.labelLarge
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
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EchoTextPrimary
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary
            )
        }
    }
}

@Composable
fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = EchoSuccess
) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (active) color else EchoTextTertiary.copy(alpha = 0.4f))
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
            .background(EchoElevated)
            .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            active = isActive,
            color = if (peerCount > 0) EchoSuccess else EchoAccent
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                peerCount > 0 -> "$peerCount nearby"
                isActive -> "Listening"
                else -> "Paused"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (peerCount > 0) EchoTextPrimary else EchoTextSecondary
        )
    }
}

@Composable
fun EchoEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(EchoSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration != null) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                illustration()
            }
            Spacer(modifier = Modifier.height(EchoSpace.lg))
        }
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
            color = EchoTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DefaultRadarIllustration(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "radarIllustration")
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
    val hairline = EchoHairline

    Canvas(modifier = modifier.size(90.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width / 2f

        // Subtle range circles
        listOf(0.4f, 0.7f, 1.0f).forEach { fraction ->
            drawCircle(
                color = hairline,
                radius = maxRadius * fraction,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        if (isScanning && !reduced) {
            rotate(sweepAngle, pivot = center) {
                drawLine(
                    color = accent.copy(alpha = 0.5f),
                    start = center,
                    end = Offset(center.x, center.y - maxRadius),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        drawCircle(
            color = if (isScanning) accent else hairline,
            radius = 4.dp.toPx(),
            center = center
        )
    }
}
