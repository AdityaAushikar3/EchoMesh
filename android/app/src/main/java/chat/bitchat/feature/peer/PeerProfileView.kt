package chat.bitchat.feature.peer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.components.EchoPrimaryButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.components.InterestChipRow
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoMotion
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
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
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = EchoMotion.soft(480),
        label = "peerAppear"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
            .alpha(appear)
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
                .padding(horizontal = EchoSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(EchoSpace.lg))
            PeerAvatar(name = ui.displayName, size = 96.dp, fontSize = 34.sp, highlighted = true)
            Spacer(modifier = Modifier.height(EchoSpace.md))
            Text(
                text = ui.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = EchoTextPrimary
            )
            if (ui.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                Text(
                    text = "“${ui.bio}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EchoTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = ui.isNearby, color = if (ui.isNearby) EchoSuccess else EchoTextTertiary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        ui.isNearby && ui.meters != null -> "Nearby · ${ui.meters}m"
                        ui.isNearby -> "Nearby"
                        else -> "Out of range"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoTextSecondary
                )
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
                EchoSectionLabel(text = "Singers / Artists")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.singers)
            }

            if (ui.career.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "Career Domain")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.career)
            }

            if (ui.interests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.lg))
                EchoSectionLabel(text = "General Interests")
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                InterestChipRow(interests = ui.interests)
            }

            Spacer(modifier = Modifier.height(EchoSpace.xxl))
            EchoPrimaryButton(
                text = "Message",
                onClick = {
                    haptics.confirm()
                    onMessage(ui.peerKey)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }
}
