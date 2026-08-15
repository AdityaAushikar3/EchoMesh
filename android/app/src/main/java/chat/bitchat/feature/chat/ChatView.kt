package chat.bitchat.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.components.EchoMessageBubble
import chat.bitchat.ui.components.MessageComposer
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.util.rememberHaptics
import chat.bitchat.ui.util.rssiToMeters

@Composable
fun ChatView(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peerDisplayName by viewModel.displayName.collectAsState()
    val messages by viewModel.messagesState.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val nearbyRssi by viewModel.nearbyRssi.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()

    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.size > 4 && lastVisible < messages.size - 2
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isNearby = nearbyRssi != null
    val statusText = when {
        isNearby && nearbyRssi != null -> "Nearby · ${rssiToMeters(nearbyRssi!!)}m"
        isNearby -> "Nearby"
        else -> "Out of range"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.xs, vertical = EchoSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = EchoTextSecondary
                )
            }
            PeerAvatar(name = peerDisplayName, size = 36.dp)
            Spacer(modifier = Modifier.width(EchoSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = EchoTextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(active = isNearby, color = if (isNearby) EchoSuccess else EchoTextTertiary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoTextTertiary
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = EchoTextSecondary)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear conversation") },
                        onClick = {
                            showMenu = false
                            viewModel.clearChatHistory()
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EchoSpace.lg),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.sm),
                contentPadding = PaddingValues(vertical = EchoSpace.md)
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                    val showTime = index == messages.lastIndex ||
                        index == 0 ||
                        messages[index].timestamp - messages[index - 1].timestamp > 5 * 60 * 1000
                    EchoMessageBubble(
                        message = message,
                        isOwn = message.isOutgoing,
                        showTimestamp = showTime || message.isOutgoing
                    )
                }
            }

            // Scroll to Bottom FAB
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottom,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(EchoSpace.md)
            ) {
                val coroutineScope = rememberCoroutineScope()
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    containerColor = EchoAccent,
                    contentColor = EchoTextPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("↓", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (isSending) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                color = EchoAccent,
                trackColor = EchoVoid
            )
        }

        MessageComposer(
            value = textInput,
            onValueChange = { textInput = it },
            enabled = !isSending,
            onSend = {
                if (textInput.isNotBlank()) {
                    haptics.tick()
                    viewModel.sendMessage(textInput.trim())
                    textInput = ""
                }
            }
        )
    }
}
