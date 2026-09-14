package chat.bitchat.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoAccentMuted
import chat.bitchat.ui.theme.EchoDanger
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

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        val bubbleShape = if (isOwn) {
            RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
        }

        val bubbleBg = if (isOwn) {
            EchoAccentMuted
        } else {
            EchoElevated
        }

        val bubbleBorder = if (isOwn) {
            EchoAccent.copy(alpha = 0.35f)
        } else {
            EchoHairline
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, bubbleShape)
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = EchoTextPrimary
            )
        }

        if (showTimestamp || isOwn) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (showTimestamp) {
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoTextTertiary
                    )
                }
                if (isOwn) {
                    if (showTimestamp) Spacer(modifier = Modifier.width(6.dp))
                    val statusText = when (message.deliveryStatus) {
                        "sending" -> "..."
                        "failed" -> "Failed"
                        "sent" -> "✓"
                        "delivered", "read" -> "✓✓"
                        else -> null
                    }
                    if (statusText != null) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (message.deliveryStatus) {
                                "failed" -> EchoDanger
                                "delivered", "read" -> EchoSuccess
                                else -> EchoTextTertiary
                            }
                        )
                    }
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

    val buttonBg by animateColorAsState(
        targetValue = if (hasText) EchoAccent else EchoElevated,
        label = "sendButtonBg"
    )

    val iconTint by animateColorAsState(
        targetValue = if (hasText) EchoTextOnAccent else EchoTextTertiary,
        label = "sendIconTint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoSurface)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EchoHairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.xs + 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(EchoRadius.md))
                    .background(EchoElevated)
                    .border(
                        width = 1.dp,
                        color = if (hasText) EchoAccent.copy(alpha = 0.5f) else EchoHairline,
                        shape = RoundedCornerShape(EchoRadius.md)
                    )
                    .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Message...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EchoTextTertiary
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = EchoTextPrimary),
                    cursorBrush = SolidColor(EchoAccent),
                    maxLines = 4
                )
            }
            Spacer(modifier = Modifier.width(EchoSpace.sm))
            IconButton(
                onClick = onSend,
                enabled = hasText,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(buttonBg)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
