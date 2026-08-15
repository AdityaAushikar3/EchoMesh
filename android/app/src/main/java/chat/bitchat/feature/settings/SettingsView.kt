package chat.bitchat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.components.EchoGhostButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoDanger
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid

@Composable
fun SettingsView(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blockedPeers by viewModel.blockedPeers.collectAsState()
    var showWipeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.padding(start = EchoSpace.xs, top = EchoSpace.xs)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = EchoTextSecondary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EchoSpace.lg)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EchoTextPrimary
            )
            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Mesh Network Section
            EchoSectionLabel(text = "Mesh Protocol")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Text(
                text = "Bluetooth mesh operates fully off-grid. Messages hop peer-to-peer across nearby devices with authenticated hardware-backed E2EE encryption.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.height(EchoSpace.xs))
            Text(
                text = "Max swarm hops · 10 TTL",
                style = MaterialTheme.typography.labelMedium,
                color = EchoTextTertiary
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Blocked Nodes Section
            EchoSectionLabel(text = "Blocked Nodes (${blockedPeers.size})")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            if (blockedPeers.isEmpty()) {
                Text(
                    text = "No blocked nodes. When you block someone, their messages and profile broadcasts will be silently dropped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextSecondary
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(EchoSpace.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (blocked in blockedPeers) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(EchoRadius.md))
                                .background(EchoElevated)
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
                                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = "Blocked",
                                    tint = EchoDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(EchoSpace.sm))
                                Column {
                                    Text(
                                        text = blocked.nickname.ifBlank { "Unknown Node" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EchoTextPrimary
                                    )
                                    Text(
                                        text = blocked.peerID,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = EchoTextTertiary
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.unblockPeer(blocked.peerID) }) {
                                Text("Unblock", color = EchoAccent)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Privacy & Wipe Section
            EchoSectionLabel(text = "Privacy & Local Wipe")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Text(
                text = "Wipe permanently clears your chat history, remembered swarm peers, and blacklists on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.height(EchoSpace.md))
            TextButton(onClick = { showWipeDialog = true }) {
                Text(
                    text = "Wipe local data",
                    color = EchoDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe local data?") },
            text = {
                Text("This permanently deletes all messages, peer identity cache, and blocklists on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        viewModel.clearAllMessagesAndPeers()
                    }
                ) {
                    Text("Wipe", color = EchoDanger)
                }
            },
            dismissButton = {
                EchoGhostButton(text = "Cancel", onClick = { showWipeDialog = false })
            },
            containerColor = EchoElevated
        )
    }
}
