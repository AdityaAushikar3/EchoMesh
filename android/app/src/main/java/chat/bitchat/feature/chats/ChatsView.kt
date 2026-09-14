package chat.bitchat.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.MeshStatusPill
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoDanger
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoSurface
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
    val nearbyCount by viewModel.nearbyCount.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
            .padding(horizontal = EchoSpace.lg)
    ) {
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = EchoTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Offline peer-to-peer conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(EchoSpace.sm))
            MeshStatusPill(
                peerCount = nearbyCount,
                isActive = true
            )
        }
        Spacer(modifier = Modifier.height(EchoSpace.lg))

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EchoEmptyState(
                    title = "No conversations yet",
                    subtitle = "Tap anyone detected in Space to start a private off-grid chat."
                )
            }
        } else {
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = EchoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.xs)
            ) {
                items(conversations, key = { convo -> convo.peerKey }) { convo ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteConversation(convo.peerKey, convo.displayName)
                                true
                            } else {
                                false
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(EchoRadius.md))
                                    .background(if (isDismissing) EchoDanger else Color.Transparent)
                                    .padding(horizontal = EchoSpace.md),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        content = {
                            ConversationRow(
                                convo = convo,
                                modifier = Modifier.animateItemPlacement(),
                                onClick = { onOpenChat(convo.peerKey) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    convo: ConversationPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = rememberTime(convo.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.md))
            .background(EchoSurface)
            .border(
                width = 1.dp,
                color = if (convo.isUnread) EchoAccent.copy(alpha = 0.4f) else EchoHairline,
                shape = RoundedCornerShape(EchoRadius.md)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeerAvatar(
            name = convo.displayName,
            size = 48.dp,
            highlighted = convo.isUnread,
            ringColor = if (convo.isBlocked) EchoDanger else EchoAccent
        )
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = convo.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (convo.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (convo.isBlocked) EchoTextTertiary else EchoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (convo.isBlocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoDanger
                    )
                } else if (convo.isNearby) {
                    Spacer(modifier = Modifier.width(6.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (convo.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (convo.isUnread) EchoTextPrimary else EchoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (convo.isUnread) FontWeight.Bold else FontWeight.Normal,
                color = if (convo.isUnread) EchoAccent else EchoTextTertiary
            )
            if (convo.isUnread) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(EchoAccent, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun rememberTime(timestamp: Long): String {
    val fmt = androidx.compose.runtime.remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    return fmt.format(Date(timestamp))
}
