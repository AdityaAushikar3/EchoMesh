package chat.bitchat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.components.EchoPrimaryButton
import chat.bitchat.ui.components.EchoSectionLabel
import chat.bitchat.ui.components.EchoTagInputField
import chat.bitchat.ui.components.InterestChipRow
import chat.bitchat.ui.components.PeerAvatar
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
import chat.bitchat.ui.util.InterestMatcher
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
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var savedFlash by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        profile?.let {
            name = it.name
            bio = it.bio
            val mergedLegacy = listOfNotNull(
                it.interests.takeIf { s -> s.isNotBlank() },
                it.favoriteMusic.takeIf { s -> s.isNotBlank() },
                it.favoriteMovies.takeIf { s -> s.isNotBlank() },
                it.singers.takeIf { s -> s.isNotBlank() },
                it.career.takeIf { s -> s.isNotBlank() }
            ).joinToString(", ")
            tags = InterestMatcher.parseTags(mergedLegacy, max = 20)
        }
    }

    val previewInterests = remember(tags) {
        parseInterests(tags.joinToString(", "))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoVoid)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.lg, vertical = EchoSpace.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EchoTextPrimary
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

            // Profile Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.lg))
                    .background(EchoSurface)
                    .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.lg))
                    .padding(EchoSpace.lg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PeerAvatar(
                        name = name.ifBlank { "You" },
                        size = 80.dp,
                        fontSize = 28.sp,
                        highlighted = true,
                        ringColor = EchoAccent
                    )
                    Spacer(modifier = Modifier.height(EchoSpace.sm))
                    Text(
                        text = name.ifBlank { "Your Name" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EchoTextPrimary
                    )
                    if (bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(EchoSpace.xs))
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EchoTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(EchoSpace.md))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Node ID
                        Text(
                            text = localId.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EchoTextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoElevated)
                                .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )

                        // E2EE Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(EchoRadius.full))
                                .background(EchoElevated)
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
                                text = "E2EE Protected",
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

            // Profile Fields
            EchoSectionLabel(text = "Personal Info")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Display Name",
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                hint = "How nearby people will see you"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Bio",
                value = bio,
                onValueChange = { bio = it },
                singleLine = false,
                hint = "A short status or intro"
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            // Interests & Skills Tag Input
            EchoSectionLabel(text = "Interests & Skills")
            Spacer(modifier = Modifier.height(EchoSpace.xs))
            Text(
                text = "Add passions, topics, or skills you're interested in. Used to highlight mutual connections when discovering people nearby.",
                style = MaterialTheme.typography.bodySmall,
                color = EchoTextTertiary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))

            EchoTagInputField(
                tags = tags,
                onTagsChanged = { tags = it },
                maxTags = 20
            )

            Spacer(modifier = Modifier.height(EchoSpace.xl))

            EchoPrimaryButton(
                text = if (savedFlash) "✓ Profile Saved" else "Save Profile",
                onClick = {
                    viewModel.updateProfile(name, bio, tags)
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
            fontWeight = FontWeight.Medium,
            color = EchoTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = EchoTextPrimary),
            cursorBrush = SolidColor(EchoAccent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(EchoRadius.md))
                .background(EchoElevated)
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
                        color = EchoTextTertiary.copy(alpha = 0.6f)
                    )
                }
                inner()
            }
        )
    }
}
