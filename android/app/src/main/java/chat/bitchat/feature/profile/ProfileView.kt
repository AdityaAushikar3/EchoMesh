package chat.bitchat.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.components.EchoPrimaryButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.components.InterestChipRow
import chat.bitchat.ui.components.PeerAvatar
import chat.bitchat.ui.components.StatusDot
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSuccess
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.rememberHaptics

@Composable
fun ProfileView(
    viewModel: ProfileViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profileState.collectAsState()
    val localId = viewModel.localIdentity
    val haptics = rememberHaptics()

    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf("") }
    var music by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var singers by remember { mutableStateOf("") }
    var career by remember { mutableStateOf("") }
    var savedFlash by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        profile?.let {
            name = it.name
            bio = it.bio
            movies = it.favoriteMovies
            music = it.favoriteMusic
            interests = it.interests
            singers = it.singers
            career = it.career
        }
    }

    val previewInterests = remember(interests, music, movies, singers, career) {
        parseInterests(
            listOfNotNull(
                interests.takeIf { it.isNotBlank() },
                music.takeIf { it.isNotBlank() },
                movies.takeIf { it.isNotBlank() },
                singers.takeIf { it.isNotBlank() },
                career.takeIf { it.isNotBlank() }
            ).joinToString(", ")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Identity",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EchoTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = EchoSpace.xs)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = EchoTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EchoSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(EchoSpace.sm))

            // 🪪 Futuristic Tactical Identity Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.lg))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                EchoElevated.copy(alpha = 0.95f),
                                EchoSurface.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EchoAccent.copy(alpha = 0.45f),
                                EchoHairline.copy(alpha = 0.20f)
                            )
                        ),
                        shape = RoundedCornerShape(EchoRadius.lg)
                    )
                    .padding(EchoSpace.lg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar with glowing ring
                    PeerAvatar(
                        name = name.ifBlank { "You" },
                        size = 92.dp,
                        fontSize = 32.sp,
                        highlighted = true,
                        ringColor = EchoAccent
                    )
                    Spacer(modifier = Modifier.height(EchoSpace.md))

                    // User name
                    Text(
                        text = name.ifBlank { "Your Codename" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = EchoTextPrimary
                    )

                    if (bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(EchoSpace.xs))
                        Text(
                            text = "“$bio”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EchoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(EchoSpace.md))

                    // Node ID & Security Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monospace Node ID Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoVoid.copy(alpha = 0.6f))
                                .border(1.dp, EchoAccent.copy(alpha = 0.3f), RoundedCornerShape(EchoRadius.full))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            StatusDot(active = true, color = EchoSuccess)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = localId,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = EchoAccent
                            )
                        }

                        // E2EE Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoVoid.copy(alpha = 0.6f))
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "E2EE",
                                tint = EchoSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ECDH SECP256R1",
                                style = MaterialTheme.typography.labelSmall,
                                color = EchoTextSecondary
                            )
                        }
                    }

                    if (previewInterests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(EchoSpace.md))
                        InterestChipRow(interests = previewInterests)
                    }
                }
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Section 1: Profile Details
            EchoSectionLabel(text = "Personal Dossier")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Codename / Display Name",
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                hint = "How nearby mesh peers see you"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Bio / Status",
                value = bio,
                onValueChange = { bio = it },
                singleLine = false,
                hint = "A short quote or off-grid status"
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Section 2: Shared Mesh Telemetry & Tags
            EchoSectionLabel(text = "Radio Discovery Tags")
            Spacer(modifier = Modifier.height(EchoSpace.xs))
            Text(
                text = "Tags are automatically compared with nearby peers to highlight shared interests.",
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))

            EchoField(
                label = "🎬 Movies & Series",
                value = movies,
                onValueChange = { movies = it },
                singleLine = false,
                hint = "e.g. Breaking Bad, Interstellar, Mr. Robot"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "🎵 Music Genres",
                value = music,
                onValueChange = { music = it },
                singleLine = false,
                hint = "e.g. Synthwave, Rock, Lo-fi, Electronic"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "🎤 Favorite Artists & Bands",
                value = singers,
                onValueChange = { singers = it },
                singleLine = false,
                hint = "e.g. Daft Punk, The Weeknd, Hans Zimmer"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "💼 Career / Focus",
                value = career,
                onValueChange = { career = it },
                singleLine = false,
                hint = "e.g. Software Engineer, Robotics, Design"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "⚡ Passions & Hobbies",
                value = interests,
                onValueChange = { interests = it },
                singleLine = false,
                hint = "e.g. Mesh Radios, Astronomy, Cycling"
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Save Action Button
            EchoPrimaryButton(
                text = if (savedFlash) "✓ Profile Broadcasted" else "Save & Broadcast to Swarm",
                onClick = {
                    viewModel.updateProfile(name, bio, movies, music, interests, singers, career)
                    haptics.confirm()
                    savedFlash = true
                }
            )
            Spacer(modifier = Modifier.height(EchoSpace.xxl))
        }
    }

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1600)
            savedFlash = false
        }
    }
}

@Composable
private fun EchoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    hint: String? = null
) {
    val isFilled = value.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isFilled) EchoAccent else EchoTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = EchoTextPrimary),
            cursorBrush = SolidColor(EchoAccent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(EchoRadius.md))
                .background(EchoElevated.copy(alpha = 0.75f))
                .border(
                    width = 1.dp,
                    color = if (isFilled) EchoAccent.copy(alpha = 0.35f) else EchoHairline,
                    shape = RoundedCornerShape(EchoRadius.md)
                )
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
            decorationBox = { inner ->
                if (value.isEmpty() && hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = EchoTextTertiary.copy(alpha = 0.7f)
                    )
                }
                inner()
            }
        )
    }
}
