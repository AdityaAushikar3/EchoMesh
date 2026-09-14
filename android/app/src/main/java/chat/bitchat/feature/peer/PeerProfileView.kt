package chat.bitchat.feature.peer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.NearMe
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.components.EchoPrimaryButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.components.InterestChipRow
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
import chat.bitchat.ui.util.rememberHaptics

@Composable
fun PeerProfileView(
    viewModel: PeerProfileViewModel,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsState()
    val haptics = rememberHaptics()
    var showBlockConfirmDialog by remember { mutableStateOf(false) }

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
                text = "Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = EchoTextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EchoSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(EchoSpace.md))

            // Profile Card Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.lg))
                    .background(EchoSurface)
                    .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.lg))
                    .padding(EchoSpace.lg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PeerAvatar(
                        name = ui.displayName,
                        size = 80.dp,
                        fontSize = 28.sp,
                        highlighted = !ui.isBlocked,
                        ringColor = if (ui.isBlocked) EchoDanger else if (ui.isNearby) EchoAccent else EchoHairline
                    )
                    Spacer(modifier = Modifier.height(EchoSpace.sm))
                    Text(
                        text = ui.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EchoTextPrimary
                    )
                    if (ui.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(EchoSpace.xs))
                        Text(
                            text = ui.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EchoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(EchoSpace.md))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Proximity Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoElevated)
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            StatusDot(
                                active = ui.isNearby && !ui.isBlocked,
                                color = if (ui.isBlocked) EchoDanger else if (ui.isNearby) EchoSuccess else EchoTextTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    ui.isBlocked -> "Blocked"
                                    ui.isNearby && ui.meters != null -> "${ui.meters}m away"
                                    ui.isNearby -> "Nearby"
                                    else -> "Mesh Relay"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (ui.isBlocked) EchoDanger else EchoTextSecondary
                            )
                        }

                        // Monospace Node ID Chip
                        Text(
                            text = ui.peerKey.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EchoTextTertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoElevated)
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (ui.sharedInterests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Shared Interests")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.sharedInterests)
            }

            if (ui.movies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Movies & Series")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.movies)
            }

            if (ui.music.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Music")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.music)
            }

            if (ui.singers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Artists")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.singers)
            }

            if (ui.career.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Career & Skills")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.career)
            }

            if (ui.interests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Interests")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.interests)
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            if (!ui.isBlocked) {
                EchoPrimaryButton(
                    text = "Message",
                    onClick = {
                        haptics.confirm()
                        onMessage(ui.peerKey)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(EchoSpace.sm))

            TextButton(
                onClick = {
                    if (ui.isBlocked) {
                        viewModel.toggleBlockPeer()
                        haptics.confirm()
                    } else {
                        showBlockConfirmDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (ui.isBlocked) "Unblock Node" else "Block Node",
                    color = if (ui.isBlocked) EchoAccent else EchoDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = {
                Text(
                    text = "Block ${ui.displayName}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EchoTextPrimary
                )
            },
            text = {
                Text(
                    text = "You will no longer receive messages or profile broadcasts from this node across the mesh network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleBlockPeer()
                        haptics.confirm()
                        showBlockConfirmDialog = false
                    }
                ) {
                    Text("Block", color = EchoDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = EchoTextSecondary)
                }
            },
            containerColor = EchoSurface
        )
    }
}
