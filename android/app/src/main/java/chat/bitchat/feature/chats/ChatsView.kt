package chat.bitchat.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsView(
    viewModel: ChatsViewModel,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
            .padding(horizontal = EchoSpace.lg)
    ) {
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Text(
            text = "Chats",
            style = MaterialTheme.typography.headlineMedium,
            color = EchoTextPrimary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xs))
        Text(
            text = "Conversations with people you've met nearby.",
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextSecondary
        )
        Spacer(modifier = Modifier.height(EchoSpace.lg))

        if (conversations.isEmpty()) {
            EchoEmptyState(
                title = "No conversations yet",
                subtitle = "Find someone in Space and say hello.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = EchoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.xs)
            ) {
                items(conversations, key = { it.peerKey }) { convo ->
                    ConversationRow(convo = convo, onClick = { onOpenChat(convo.peerKey) })
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    convo: ConversationPreview,
    onClick: () -> Unit
) {
    val time = rememberTime(convo.timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.md))
            .background(EchoElevated.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeerAvatar(name = convo.displayName, size = 48.dp)
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = convo.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = EchoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (convo.isNearby) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusDot(active = true, color = EchoSuccess)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Nearby",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoSuccess
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = convo.lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextTertiary
        )
    }
}

@Composable
private fun rememberTime(timestamp: Long): String {
    val fmt = androidx.compose.runtime.remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    return fmt.format(Date(timestamp))
}
