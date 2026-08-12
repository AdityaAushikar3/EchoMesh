package chat.bitchat.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.NearbySpace
import chat.bitchat.ui.components.ScanningState
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoMotion
import chat.bitchat.ui.theme.EchoSpace
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
            onToggleScan = { viewModel.toggleScanning() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.sm)
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
                        ScanningState()
                    }
                    SpaceMode.Empty -> EchoEmptyState(
                        title = "No one nearby yet",
                        subtitle = "Move around to discover people.",
                        modifier = Modifier.fillMaxSize()
                    )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EchoSpace.lg, vertical = EchoSpace.md)
    ) {
        Text(
            text = "Space",
            style = MaterialTheme.typography.headlineMedium,
            color = EchoTextPrimary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(active = isScanning)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    peopleCount > 0 -> "$peopleCount ${if (peopleCount == 1) "person" else "people"} nearby"
                    isScanning -> "Listening…"
                    else -> "Scanner paused"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = EchoTextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleScan) {
                Text(
                    text = if (isScanning) "Pause" else "Listen",
                    color = EchoTextTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
