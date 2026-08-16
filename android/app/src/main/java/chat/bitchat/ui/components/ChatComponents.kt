package chat.bitchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoAccentSoft
import chat.bitchat.ui.theme.EchoDanger
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextOnAccent
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EchoMessageBubble(
    message: MessageEntity,
    isOwn: Boolean,
    showTimestamp: Boolean,
    modifier: Modifier = Modifier,
    animateEntry: Boolean = false
) {
    val timeFormat = rememberTimeFormat()
    var visible by remember { mutableStateOf(!animateEntry) }

    if (animateEntry) {
        LaunchedEffect(Unit) {
            visible = true
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubbleScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubbleAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (animateEntry) {
                    Modifier
                        .scale(scale)
                        .alpha(alpha)
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isOwn) EchoTextOnAccent else EchoTextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(EchoRadius.md))
                .background(
                    brush = if (isOwn) {
                        Brush.horizontalGradient(
                            colors = listOf(EchoAccentSoft.copy(alpha = 0.82f), EchoAccent.copy(alpha = 0.95f))
                        )
                    } else {
                        SolidColor(EchoElevated)
                    }
                )
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showTimestamp) {
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoTextTertiary
                )
            }
            if (isOwn) {
                if (showTimestamp) Spacer(modifier = Modifier.width(8.dp))
                val status = when (message.deliveryStatus) {
                    "sending" -> "🕒"
                    "failed" -> "Failed"
                    "sent" -> "✓"
                    "delivered", "read" -> "✓✓"
                    else -> null
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.deliveryStatus) {
                            "failed" -> EchoDanger
                            "delivered", "read" -> EchoAccent
                            else -> EchoTextTertiary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTimeFormat(): SimpleDateFormat {
    return androidx.compose.runtime.remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val hasText = value.isNotBlank() && enabled

    val buttonScale by animateFloatAsState(
        targetValue = if (hasText) 1.0f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sendButtonScale"
    )

    val buttonBg by animateColorAsState(
        targetValue = if (hasText) EchoAccent else EchoElevated,
        label = "sendButtonBg"
    )

    val iconTint by animateColorAsState(
        targetValue = if (hasText) EchoTextOnAccent else EchoTextSecondary,
        label = "sendIconTint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoSurface.copy(alpha = 0.92f))
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Glowing hairline separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (hasText) EchoAccent.copy(alpha = 0.35f) else EchoAccent.copy(alpha = 0.15f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(EchoRadius.lg))
                    .background(EchoElevated)
                    .border(
                        width = 1.dp,
                        color = if (hasText) EchoAccent.copy(alpha = 0.45f) else EchoElevated,
                        shape = RoundedCornerShape(EchoRadius.lg)
                    )
                    .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Message on mesh...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = EchoTextTertiary
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = EchoTextPrimary),
                    cursorBrush = SolidColor(EchoAccent),
                    maxLines = 5
                )
            }
            Spacer(modifier = Modifier.width(EchoSpace.sm))
            IconButton(
                onClick = onSend,
                enabled = hasText,
                modifier = Modifier
                    .size(46.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(buttonBg)
                    .border(
                        width = if (hasText) 1.5.dp else 0.dp,
                        color = if (hasText) EchoAccent.copy(alpha = 0.6f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
