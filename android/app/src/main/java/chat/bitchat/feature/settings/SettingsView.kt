package chat.bitchat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import chat.bitchat.ui.components.EchoGhostButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.theme.EchoDanger
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
    var showDialog by remember { mutableStateOf(false) }

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
                color = EchoTextPrimary
            )
            Spacer(modifier = Modifier.height(EchoSpace.xl))

            EchoSectionLabel(text = "Mesh")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Text(
                text = "Bluetooth mesh stays local. Messages hop across nearby devices — no account, no cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.height(EchoSpace.xs))
            Text(
                text = "Max hops · 7",
                style = MaterialTheme.typography.labelMedium,
                color = EchoTextTertiary
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))
            EchoSectionLabel(text = "Privacy")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Text(
                text = "Wipe clears chat history and remembered peers on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.height(EchoSpace.md))
            TextButton(onClick = { showDialog = true }) {
                Text(
                    text = "Wipe local data",
                    color = EchoDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Wipe local data?") },
            text = {
                Text("This permanently deletes chat history and peer cache on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        viewModel.clearAllMessagesAndPeers()
                    }
                ) {
                    Text("Wipe", color = EchoDanger)
                }
            },
            dismissButton = {
                EchoGhostButton(text = "Cancel", onClick = { showDialog = false })
            }
        )
    }
}
