package chat.bitchat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.components.EchoPrimaryButton
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
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.rememberHaptics

@Composable
fun ProfileView(
    viewModel: ProfileViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profileState.collectAsState()
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
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "You",
                style = MaterialTheme.typography.headlineMedium,
                color = EchoTextPrimary,
                modifier = Modifier.weight(1f).padding(start = EchoSpace.xs)
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
            Spacer(modifier = Modifier.height(EchoSpace.md))
            PeerAvatar(
                name = name.ifBlank { "You" },
                size = 88.dp,
                fontSize = 30.sp,
                highlighted = true
            )
            Spacer(modifier = Modifier.height(EchoSpace.md))
            Text(
                text = name.ifBlank { "Your name" },
                style = MaterialTheme.typography.headlineSmall,
                color = EchoTextPrimary
            )
            if (bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(EchoSpace.xs))
                Text(
                    text = "“$bio”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EchoTextSecondary
                )
            }
            if (previewInterests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EchoSpace.md))
                InterestChipRow(interests = previewInterests)
            }

            Spacer(modifier = Modifier.height(EchoSpace.xl))
            EchoSectionLabel(text = "Identity")
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(label = "Name", value = name, onValueChange = { name = it }, singleLine = true)
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(label = "Bio", value = bio, onValueChange = { bio = it }, singleLine = false)

            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Movies / Web Series",
                value = movies,
                onValueChange = { movies = it },
                singleLine = false,
                hint = "e.g. Breaking Bad, Interstellar"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Music Genre",
                value = music,
                onValueChange = { music = it },
                singleLine = false,
                hint = "e.g. Rock, Lo-fi, Classical"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Singers / Artists",
                value = singers,
                onValueChange = { singers = it },
                singleLine = false,
                hint = "e.g. The Weeknd, Hans Zimmer"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "Career Domain",
                value = career,
                onValueChange = { career = it },
                singleLine = false,
                hint = "e.g. Software Dev, AI/ML, Finance"
            )
            Spacer(modifier = Modifier.height(EchoSpace.sm))
            EchoField(
                label = "General Interests",
                value = interests,
                onValueChange = { interests = it },
                singleLine = false,
                hint = "e.g. Hiking, Chess, Gaming"
            )

            Spacer(modifier = Modifier.height(EchoSpace.lg))
            EchoPrimaryButton(
                text = if (savedFlash) "Saved" else "Save",
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
            kotlinx.coroutines.delay(1200)
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = EchoTextTertiary
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
                .background(EchoElevated)
                .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm),
            decorationBox = { inner ->
                if (value.isEmpty() && hint != null) {
                    Text(text = hint, style = MaterialTheme.typography.bodyLarge, color = EchoTextTertiary)
                }
                inner()
            }
        )
    }
}
