package chat.bitchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoDanger
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSurface
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
    modifier: Modifier = Modifier
) {
    val timeFormat = rememberTimeFormat()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = EchoTextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(EchoRadius.md))
                .background(if (isOwn) EchoAccent.copy(alpha = 0.18f) else EchoElevated)
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
                    "sending" -> "Sending…"
                    "failed" -> "Failed"
                    "sent" -> "Sent"
                    else -> null
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.deliveryStatus == "failed") EchoDanger else EchoTextTertiary
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoSurface)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(EchoRadius.lg))
                .background(EchoElevated)
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "Message",
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
        Spacer(modifier = Modifier.width(EchoSpace.xs))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank()) EchoAccent else EchoElevated
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = if (enabled && value.isNotBlank()) EchoTextPrimary else EchoTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
