package chat.bitchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoMotion
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoYou
import chat.bitchat.ui.theme.rememberReducedMotion
import chat.bitchat.ui.util.ProximityBand
import chat.bitchat.ui.util.chatKey
import chat.bitchat.ui.util.displayName
import chat.bitchat.ui.util.rememberHaptics
import chat.bitchat.ui.util.rssiToBand
import chat.bitchat.ui.util.rssiToMeters
import chat.bitchat.ui.util.stableAngle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun NearbySpace(
    devices: List<NearbyDevice>,
    youName: String,
    onPersonClick: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    isScanning: Boolean = true
) {
    val reduced = rememberReducedMotion()
    val textMeasurer = rememberTextMeasurer()
    val haptics = rememberHaptics()

    // Smooth sweep animation for scanning
    val sweepTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced || !isScanning) 1 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // Gentle subtle breathing float
    val floatTransition = rememberInfiniteTransition(label = "float")
    val floatPhase by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth RSSI using Exponential Moving Average
    val smoothedRssiMap = remember { mutableMapOf<String, Double>() }
    val alpha = 0.25f
    val smoothedDevices = remember(devices) {
        devices.map { device ->
            val prev = smoothedRssiMap[device.id]
            val currentRssi = device.rssi.toDouble()
            val nextSmoothed = if (prev == null) {
                currentRssi
            } else {
                alpha * currentRssi + (1 - alpha) * prev
            }
            smoothedRssiMap[device.id] = nextSmoothed
            device.copy(rssi = nextSmoothed.roundToInt())
        }
    }

    val sortedDevices = remember(smoothedDevices) {
        smoothedDevices.sortedByDescending { it.rssi }.take(7).sortedBy { it.id }
    }

    var inspectedDevice by remember { mutableStateOf<NearbyDevice?>(null) }

    // Keep inspectedDevice up-to-date with live device state
    val activeInspected = remember(inspectedDevice, smoothedDevices) {
        inspectedDevice?.let { target ->
            smoothedDevices.find { it.id == target.id || it.identity == target.identity } ?: target
        }
    }

    val ringPadding = with(LocalDensity.current) { EchoSpace.lg.toPx() }
    val accent = EchoAccent
    val hairline = EchoHairline
    val textTertiary = EchoTextTertiary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val maxW = size.width
                val maxH = size.height
                val radiusBase = (minOf(maxW, maxH) - ringPadding * 2f) / 2f
                val c = Offset(maxW / 2f, maxH / 2f)

                // 1. Concentric Distance Rings: Near (<5m), Mid (15m), Far (30m+)
                val ringFractions = listOf(0.32f to "5m", 0.62f to "15m", 0.92f to "30m")
                ringFractions.forEach { (fraction, label) ->
                    val r = radiusBase * fraction
                    drawCircle(
                        color = hairline.copy(alpha = 0.65f),
                        radius = r,
                        center = c,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Range marker label
                    val layout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = textTertiary.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(c.x + 6.dp.toPx(), c.y - r - (layout.size.height / 2f))
                    )
                }

                // 2. Subtle crosshair ticks
                val tickLen = 4.dp.toPx()
                drawLine(
                    color = hairline,
                    start = Offset(c.x - radiusBase * 0.95f, c.y),
                    end = Offset(c.x - radiusBase * 0.95f + tickLen, c.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = hairline,
                    start = Offset(c.x + radiusBase * 0.95f - tickLen, c.y),
                    end = Offset(c.x + radiusBase * 0.95f, c.y),
                    strokeWidth = 1.dp.toPx()
                )

                // 3. Functional Sweep Beam (Active discovery indicator)
                if (isScanning && !reduced) {
                    rotate(sweepAngle, pivot = c) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.02f),
                                    accent.copy(alpha = 0.16f)
                                ),
                                center = c
                            ),
                            radius = radiusBase * 0.92f,
                            center = c
                        )
                    }
                }
            }
    ) {
        val maxW = with(LocalDensity.current) { maxWidth.toPx() }
        val maxH = with(LocalDensity.current) { maxHeight.toPx() }
        val radiusBase = (minOf(maxW, maxH) - ringPadding * 2f) / 2f

        // Center Origin: "YOU"
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PeerAvatar(
                name = youName.ifBlank { "You" },
                size = 52.dp,
                fontSize = 17.sp,
                highlighted = true,
                ringColor = EchoYou
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = EchoTextSecondary
            )
        }

        // Detected Nearby Peers
        sortedDevices.forEachIndexed { index, device ->
            key(device.identity) {
                val band = rssiToBand(device.rssi)
                val radiusFraction = when (band) {
                    ProximityBand.Near -> 0.32f
                    ProximityBand.Mid -> 0.62f
                    ProximityBand.Far -> 0.88f
                }
                val angleDeg = stableAngle(device.id, index, sortedDevices.size)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val floatAmp = if (reduced) 0f else 4f
                val floatOffset = sin(floatPhase + index * 0.8f) * floatAmp
                val targetX = (cos(angleRad) * radiusBase * radiusFraction).toFloat()
                val targetY = (sin(angleRad) * radiusBase * radiusFraction).toFloat() + floatOffset

                val animX by animateFloatAsState(
                    targetValue = targetX,
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "nodeX"
                )
                val animY by animateFloatAsState(
                    targetValue = targetY,
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "nodeY"
                )

                val isSelected = activeInspected?.id == device.id || selectedId == device.id || selectedId == device.chatKey()

                NearbyPersonNode(
                    device = device,
                    selected = isSelected,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(animX.roundToInt(), animY.roundToInt()) },
                    onClick = {
                        inspectedDevice = device
                        onPersonClick(device)
                    }
                )
            }
        }

        if (smoothedDevices.size > sortedDevices.size) {
            val remaining = smoothedDevices.size - sortedDevices.size
            Text(
                text = "+$remaining more nearby · view in Discover",
                style = MaterialTheme.typography.labelSmall,
                color = EchoTextTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = EchoSpace.sm)
            )
        }

        // Selected Peer Quick Drawer (Direct utility on tap)
        AnimatedVisibility(
            visible = activeInspected != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(EchoSpace.md)
        ) {
            activeInspected?.let { device ->
                QuickPeerDrawer(
                    device = device,
                    onOpenChat = {
                        haptics.confirm()
                        onPersonClick(device)
                    },
                    onDismiss = { inspectedDevice = null }
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
    val band = rssiToBand(device.rssi)
    val haptics = rememberHaptics()

    val (targetAvatarSize, fontSize) = when (band) {
        ProximityBand.Near -> Pair(if (selected) 48.dp else 44.dp, 14.sp)
        ProximityBand.Mid -> Pair(if (selected) 44.dp else 40.dp, 13.sp)
        ProximityBand.Far -> Pair(if (selected) 40.dp else 36.dp, 12.sp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = 84.dp)
            .semantics { contentDescription = "$name, $meters meters away" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
    ) {
        PeerAvatar(
            name = name,
            size = targetAvatarSize,
            fontSize = fontSize,
            highlighted = selected,
            ringColor = EchoAccent,
            proximityBand = band
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = EchoTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${meters}m",
            style = MaterialTheme.typography.labelSmall,
            color = if (band == ProximityBand.Near) EchoAccent else EchoTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickPeerDrawer(
    device: NearbyDevice,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = device.displayName()
    val meters = rssiToMeters(device.rssi)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EchoRadius.lg))
            .background(EchoSurface)
            .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.lg))
            .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeerAvatar(
            name = name,
            size = 44.dp,
            proximityBand = rssiToBand(device.rssi)
        )
        Spacer(modifier = Modifier.width(EchoSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = EchoTextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = true, color = EchoSuccess)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$meters meters away · Online",
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoTextSecondary
                )
            }
        }
        IconButton(
            onClick = onOpenChat,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(EchoAccent)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = "Message",
                tint = chat.bitchat.ui.theme.EchoTextOnAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = EchoTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
