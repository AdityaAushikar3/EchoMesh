package chat.bitchat.feature.discover

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.ui.components.DistanceIndicator
import chat.bitchat.ui.components.DiscoverCompassIllustration
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.components.InterestChipRow
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.util.displayName
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.rssiToBand
import chat.bitchat.ui.util.rssiToMeters

@Composable
fun DiscoverView(
    viewModel: DiscoverViewModel,
    onPersonSelected: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.discoverItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
            .padding(horizontal = EchoSpace.lg)
    ) {
        Spacer(modifier = Modifier.height(EchoSpace.md))
        Text(
            text = "Discover",
            style = MaterialTheme.typography.headlineMedium,
            color = EchoTextPrimary
        )
        Spacer(modifier = Modifier.height(EchoSpace.xs))
        Text(
            text = "Find people around you sharing interests offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextSecondary
        )
        Spacer(modifier = Modifier.height(EchoSpace.md))

        DiscoverTabs(
            selected = selectedTab,
            onSelect = { viewModel.selectTab(it) }
        )

        Spacer(modifier = Modifier.height(EchoSpace.md))

        if (items.isEmpty()) {
            EchoEmptyState(
                title = if (selectedTab == DiscoverTab.Matches) "No matches found" else "Quiet right now",
                subtitle = if (selectedTab == DiscoverTab.Matches) "Keep scanning to find people sharing interests." else "Open Space and keep listening to find people.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                DiscoverCompassIllustration()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = EchoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.xs)
            ) {
                items(items, key = { it.device.id }) { item ->
                    DiscoverPersonRow(
                        item = item,
                        onClick = { onPersonSelected(item.device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverTabs(
    selected: DiscoverTab,
    onSelect: (DiscoverTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.md))
            .background(EchoElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (tab in DiscoverTab.entries) {
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) EchoAccent else EchoVoid.copy(alpha = 0.2f))
                    .clickable { onSelect(tab) }
                    .padding(vertical = EchoSpace.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tab == DiscoverTab.All) "All Nearby" else "Matches",
                    color = if (active) EchoVoid else EchoTextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DiscoverPersonRow(
    item: DiscoverItem,
    onClick: () -> Unit
) {
    val name = item.device.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.md))
            .background(EchoElevated.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeerAvatar(
            name = name,
            size = 44.dp,
            proximityBand = rssiToBand(item.device.rssi)
        )
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = EchoTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "About ${rssiToMeters(item.device.rssi)} meters away",
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary
            )
            if (item.sharedInterests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🤝 Shared:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoAccent
                    )
                    for (shared in item.sharedInterests) {
                        Text(
                            text = shared.display,
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoTextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EchoElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        DistanceIndicator(rssi = item.device.rssi)
    }
}
