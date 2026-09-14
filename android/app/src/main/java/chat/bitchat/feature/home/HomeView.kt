package chat.bitchat.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.NearbySpace
import chat.bitchat.ui.components.ScanningState
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoMotion
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.util.rememberHaptics

@Composable
fun HomeView(
    viewModel: HomeViewModel,
    onPersonSelected: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val youName by viewModel.youName.collectAsState()
    val haptics = rememberHaptics()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var prevCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!isScanning) viewModel.toggleScanning()
    }

    LaunchedEffect(devices.size) {
        if (devices.size > prevCount) haptics.tick()
        prevCount = devices.size
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
    ) {
        SpaceHeader(
            peopleCount = devices.size,
            isScanning = isScanning,
            onToggleScan = {
                haptics.confirm()
                viewModel.toggleScanning()
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = when {
                    devices.isNotEmpty() -> SpaceMode.People
                    isScanning -> SpaceMode.Scanning
                    else -> SpaceMode.Empty
                },
                transitionSpec = {
                    fadeIn(EchoMotion.gentle()) togetherWith fadeOut(EchoMotion.gentle())
                },
                label = "spaceMode"
            ) { mode ->
                when (mode) {
                    SpaceMode.People -> NearbySpace(
                        devices = devices,
                        youName = youName,
                        selectedId = selectedId,
                        isScanning = isScanning,
                        onPersonClick = { device ->
                            selectedId = device.id
                            haptics.light()
                            onPersonSelected(device)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    SpaceMode.Scanning -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ScanningState(
                            label = "Listening for nearby mesh nodes...",
                            isScanning = true
                        )
                    }
                    SpaceMode.Empty -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EchoEmptyState(
                            title = "Discovery Paused",
                            subtitle = "Tap Listen in the top right to discover people nearby over Bluetooth mesh."
                        )
                    }
                }
            }
        }
    }
}

private enum class SpaceMode { Scanning, People, Empty }

@Composable
private fun SpaceHeader(
    peopleCount: Int,
    isScanning: Boolean,
    onToggleScan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EchoSpace.lg, vertical = EchoSpace.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Space",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EchoTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    peopleCount > 0 -> "$peopleCount ${if (peopleCount == 1) "person" else "people"} detected nearby"
                    isScanning -> "Scanning nearby mesh nodes..."
                    else -> "Mesh discovery paused"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(EchoRadius.full))
                .background(EchoElevated)
                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
                .clickable(onClick = onToggleScan)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(
                active = isScanning,
                color = if (peopleCount > 0) EchoSuccess else EchoAccent
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isScanning) "Pause" else "Listen",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isScanning) EchoTextSecondary else EchoAccent
            )
        }
    }
}
