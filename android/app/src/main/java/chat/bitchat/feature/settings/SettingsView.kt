package chat.bitchat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextOnAccent
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
    val proximityAlertsEnabled by viewModel.proximityAlertsEnabled.collectAsState()
    val proximityThreshold by viewModel.proximityThreshold.collectAsState()
    var showWipeDialog by remember { mutableStateOf(false) }

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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = EchoTextPrimary
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = EchoTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EchoSpace.lg)
        ) {
            Spacer(modifier = Modifier.height(EchoSpace.sm))

            // Appearance Section
            EchoSectionLabel(text = "Appearance")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            val currentContext = androidx.compose.ui.platform.LocalContext.current
            val activeTheme by chat.bitchat.ui.theme.ThemeConfig.themeMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.md))
                    .background(EchoElevated)
                    .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                    val isSelected = activeTheme == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EchoAccent else EchoElevated)
                            .clickable { viewModel.setThemeMode(currentContext, mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) EchoTextOnAccent else EchoTextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Proximity Match Alerts Section
            EchoSectionLabel(text = "Proximity Match Alerts")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.md))
                    .background(EchoSurface)
                    .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
                    .padding(EchoSpace.md)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibrate on Mutual Passions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EchoTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Delivers a subtle tactile pulse when someone nearby (<5m) shares common interests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EchoTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(EchoSpace.sm))
                        Switch(
                            checked = proximityAlertsEnabled,
                            onCheckedChange = { viewModel.setProximityAlertsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EchoTextOnAccent,
                                checkedTrackColor = EchoAccent,
                                uncheckedThumbColor = EchoTextTertiary,
                                uncheckedTrackColor = EchoElevated
                            )
                        )
                    }

                    if (proximityAlertsEnabled) {
                        Spacer(modifier = Modifier.height(EchoSpace.md))
                        Text(
                            text = "Minimum Matching Passions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = EchoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(EchoSpace.xs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(EchoRadius.md))
                                .background(EchoElevated)
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1 to "1+ Match", 2 to "2+ Matches", 3 to "3+ (Default)").forEach { (thresh, label) ->
                                val isSelected = proximityThreshold == thresh
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) EchoAccent else EchoElevated)
                                        .clickable { viewModel.setProximityThreshold(thresh) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) EchoTextOnAccent else EchoTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Mesh Network Section
            EchoSectionLabel(text = "Bluetooth Mesh Network")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.md))
                    .background(EchoSurface)
                    .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
                    .padding(EchoSpace.md)
            ) {
                Column {
                    Text(
                        text = "EchoMesh operates entirely off-grid using decentralized Bluetooth Low Energy. Messages hop securely peer-to-peer across nearby devices without internet servers or cellular towers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EchoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(EchoSpace.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum relay hops",
                            style = MaterialTheme.typography.bodySmall,
                            color = EchoTextTertiary
                        )
                        Text(
                            text = "10 hops",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = EchoTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Encryption",
                            style = MaterialTheme.typography.bodySmall,
                            color = EchoTextTertiary
                        )
                        Text(
                            text = "ECDH SECP256R1 (E2EE)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = EchoTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Blocked Nodes Section
            EchoSectionLabel(text = "Blocked Nodes (${blockedPeers.size})")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            if (blockedPeers.isEmpty()) {
                Text(
                    text = "No blocked nodes. When you block a node, their messages and broadcasts will be dropped automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextTertiary
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
                                .background(EchoSurface)
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(EchoSpace.sm))
                                Column {
                                    Text(
                                        text = blocked.nickname.ifBlank { "Unknown Node" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EchoTextPrimary
                                    )
                                    Text(
                                        text = blocked.peerID.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = EchoTextTertiary
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.unblockPeer(blocked.peerID) }) {
                                Text("Unblock", color = EchoAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Data & Privacy Section
            EchoSectionLabel(text = "Data & Privacy")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Text(
                text = "All chat history and node data are stored locally on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            TextButton(
                onClick = { showWipeDialog = true },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "Clear all local data",
                    color = EchoDanger,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = {
                Text(
                    text = "Clear all local data?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EchoTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all stored messages, cached peer profiles, and blocklists on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        viewModel.clearAllMessagesAndPeers()
                    }
                ) {
                    Text("Clear Data", color = EchoDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel", color = EchoTextSecondary)
                }
            },
            containerColor = EchoSurface
        )
    }
}
