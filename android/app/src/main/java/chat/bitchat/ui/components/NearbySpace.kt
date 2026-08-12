package chat.bitchat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoMotion
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoYou
import chat.bitchat.ui.theme.rememberReducedMotion
import chat.bitchat.ui.util.ProximityBand
import chat.bitchat.ui.util.chatKey
import chat.bitchat.ui.util.displayName
import chat.bitchat.ui.util.rssiToBand
import chat.bitchat.ui.util.rssiToMeters
import chat.bitchat.ui.util.stableAngle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun NearbySpace(
    devices: List<NearbyDevice>,
    youName: String,
    onPersonClick: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null
) {
    val reduced = rememberReducedMotion()
    val floatTransition = rememberInfiniteTransition(label = "float")
    val floatPhase by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = with(LocalDensity.current) { maxWidth.toPx() }
        val maxH = with(LocalDensity.current) { maxHeight.toPx() }
        val radiusBase = minOf(maxW, maxH) / 2f

        SoftOrbitRings(
            modifier = Modifier
                .fillMaxSize()
                .padding(EchoSpace.md)
                .alpha(0.9f)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PeerAvatar(
                name = youName.ifBlank { "You" },
                size = 56.dp,
                fontSize = 18.sp,
                highlighted = true,
                ringColor = EchoYou
            )
            Spacer(modifier = Modifier.height(EchoSpace.xs))
            Text(
                text = "YOU",
                style = MaterialTheme.typography.labelSmall,
                color = EchoTextTertiary
            )
        }

        val sortedDevices = remember(devices) { devices.sortedBy { it.id } }
        sortedDevices.forEachIndexed { index, device ->
            key(device.identity) {
                val band = rssiToBand(device.rssi)
                val radiusFraction = when (band) {
                    ProximityBand.Near -> 0.30f
                    ProximityBand.Mid -> 0.50f
                    ProximityBand.Far -> 0.72f
                }
                val angleDeg = stableAngle(device.id, index, sortedDevices.size)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val floatAmp = if (reduced) 0f else 5f
                val floatOffset = sin(floatPhase + index * 0.9f) * floatAmp
                val x = (cos(angleRad) * radiusBase * radiusFraction).toFloat()
                val y = (sin(angleRad) * radiusBase * radiusFraction).toFloat() + floatOffset

                NearbyPersonNode(
                    device = device,
                    selected = selectedId == device.id || selectedId == device.chatKey(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                    onClick = { onPersonClick(device) }
                )
            }
        }
    }
}

@Composable
fun NearbyPersonNode(
    device: NearbyDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val name = device.displayName()
    val meters = rssiToMeters(device.rssi)
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = EchoMotion.soft(520),
        label = "appear"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = 100.dp)
            .alpha(appear)
            .semantics { contentDescription = "$name, about $meters meters away" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        PeerAvatar(
            name = name,
            size = if (selected) 48.dp else 40.dp,
            fontSize = 13.sp,
            highlighted = selected,
            ringColor = EchoAccent
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = EchoTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (selected) "$meters m away" else "${meters}m",
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
