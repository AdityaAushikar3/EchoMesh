package chat.bitchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoAccentMuted
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.util.Interest

@Composable
fun InterestChip(
    interest: Interest,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val bg = if (emphasized) EchoAccentMuted else EchoElevated
    val fg = if (emphasized) EchoAccent else EchoTextSecondary
    Text(
        text = interest.display,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(EchoRadius.full))
            .background(bg)
            .padding(horizontal = EchoSpace.sm, vertical = EchoSpace.xxs + 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestChipRow(
    interests: List<Interest>,
    modifier: Modifier = Modifier,
    emphasizedLabels: Set<String> = emptySet(),
    max: Int = 6
) {
    if (interests.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EchoSpace.xs),
        verticalArrangement = Arrangement.spacedBy(EchoSpace.xs)
    ) {
        interests.take(max).forEach { interest ->
            InterestChip(
                interest = interest,
                emphasized = emphasizedLabels.any {
                    it.equals(interest.label, ignoreCase = true)
                }
            )
        }
    }
}
