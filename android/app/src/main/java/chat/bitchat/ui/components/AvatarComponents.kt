package chat.bitchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.theme.*
import kotlin.math.abs

private val avatarPalette = listOf(
    AvatarSlate, AvatarTerracotta, AvatarMoss, AvatarOchre,
    AvatarDenim, AvatarPlum, AvatarRust, AvatarSage
)

fun getAvatarColor(name: String): Color {
    if (name.isBlank()) return AvatarSlate
    return avatarPalette[abs(name.hashCode()) % avatarPalette.size]
}

fun getInitials(name: String): String {
    val clean = name.trim()
    if (clean.isBlank()) return "?"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> parts[0].take(2).uppercase()
    }
}

@Composable
fun PeerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fontSize: TextUnit = 15.sp,
    highlighted: Boolean = false,
    ringColor: Color = EchoAccent,
    proximityBand: chat.bitchat.ui.util.ProximityBand? = null
) {
    val base = getAvatarColor(name)
    val effectiveRingColor = when (proximityBand) {
        chat.bitchat.ui.util.ProximityBand.Near -> EchoAccent
        chat.bitchat.ui.util.ProximityBand.Mid -> EchoAccent.copy(alpha = 0.5f)
        chat.bitchat.ui.util.ProximityBand.Far -> EchoHairline
        null -> ringColor
    }
    val showBorder = highlighted || proximityBand != null

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Avatar for $name" }
            .then(
                if (showBorder) {
                    Modifier.border(1.5.dp, effectiveRingColor, CircleShape)
                } else Modifier
            )
            .clip(CircleShape)
            .background(base.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = getInitials(name),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
