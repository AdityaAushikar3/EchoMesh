package chat.bitchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoTextPrimary
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
        fontWeight = FontWeight.Medium,
        color = if (band == ProximityBand.Near) EchoAccent else EchoTextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(EchoRadius.sm))
            .background(EchoElevated)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
