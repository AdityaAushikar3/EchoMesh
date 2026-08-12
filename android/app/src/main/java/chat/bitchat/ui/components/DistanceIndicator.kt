package chat.bitchat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.util.ProximityBand
import chat.bitchat.ui.util.rssiToBand
import chat.bitchat.ui.util.rssiToMeters

@Composable
fun DistanceIndicator(
    rssi: Int,
    modifier: Modifier = Modifier,
    detailed: Boolean = false
) {
    val meters = rssiToMeters(rssi)
    val band = rssiToBand(rssi)
    val label = if (detailed) {
        when (band) {
            ProximityBand.Near -> "$meters m away"
            ProximityBand.Mid -> "$meters m away"
            ProximityBand.Far -> "$meters m away"
        }
    } else {
        "${meters}m"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (band == ProximityBand.Near) EchoTextSecondary else EchoTextTertiary,
        modifier = modifier
    )
}
