package chat.bitchat.feature.discover

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.domain.router.MeshGraph
import chat.bitchat.ui.components.DistanceIndicator
import chat.bitchat.ui.components.EchoEmptyState
import chat.bitchat.ui.components.InterestChipRow
import chat.bitchat.ui.components.MeshVisualizer
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.theme.EchoAccent
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
import chat.bitchat.ui.util.displayName
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
    val meshGraph by viewModel.meshGraph.collectAsState()

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
            fontWeight = FontWeight.Bold,
            color = EchoTextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when (selectedTab) {
                DiscoverTab.Topology -> "Live mesh routing constellation & node graph."
                DiscoverTab.Matches -> "People sharing common passions with you."
                DiscoverTab.All -> "Find nearby people sharing interests offline."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = EchoTextSecondary
        )
        Spacer(modifier = Modifier.height(EchoSpace.md))

        DiscoverTabs(
            selected = selectedTab,
            onSelect = { viewModel.selectTab(it) }
        )

        Spacer(modifier = Modifier.height(EchoSpace.md))

        if (selectedTab == DiscoverTab.Topology) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                MeshVisualizer(
                    graph = meshGraph,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EchoEmptyState(
                    title = if (selectedTab == DiscoverTab.Matches) "No common matches yet" else "No one nearby yet",
                    subtitle = if (selectedTab == DiscoverTab.Matches)
                        "People broadcasting mutual interests will appear here automatically."
                    else
                        "Make sure Bluetooth is active and listening in Space."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = EchoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.sm)
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
            .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (tab in DiscoverTab.entries) {
            val active = tab == selected
            val label = when (tab) {
                DiscoverTab.All -> "All Nearby"
                DiscoverTab.Matches -> "Matches"
                DiscoverTab.Topology -> "Topology"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) EchoAccent else EchoElevated)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) EchoTextOnAccent else EchoTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
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
    val meters = rssiToMeters(item.device.rssi)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.md))
            .background(EchoSurface)
            .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeerAvatar(
            name = name,
            size = 46.dp,
            proximityBand = rssiToBand(item.device.rssi)
        )
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EchoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DistanceIndicator(rssi = item.device.rssi)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$meters meters away",
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary
            )
            if (item.sharedInterests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                InterestChipRow(interests = item.sharedInterests, max = 3)
            }
        }
    }
}
