package chat.bitchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.bitchat.ui.theme.EchoAccent
import chat.bitchat.ui.theme.EchoElevated
import chat.bitchat.ui.theme.EchoHairline
import chat.bitchat.ui.theme.EchoRadius
import chat.bitchat.ui.theme.EchoSpace
import chat.bitchat.ui.theme.EchoSurface
import chat.bitchat.ui.theme.EchoTextPrimary
import chat.bitchat.ui.theme.EchoTextSecondary
import chat.bitchat.ui.theme.EchoTextTertiary
import chat.bitchat.ui.util.InterestMatcher

@Composable
fun RemovableTagChip(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(EchoRadius.full))
            .background(EchoElevated)
            .border(1.dp, EchoHairline, RoundedCornerShape(EchoRadius.full))
            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = EchoTextPrimary
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove tag",
                tint = EchoTextTertiary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EchoTagInputField(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    maxTags: Int = 20
) {
    var inputText by remember { mutableStateOf("") }
    val isLimitReached = tags.size >= maxTags

    fun commitTags() {
        if (inputText.isBlank() || isLimitReached) {
            inputText = ""
            return
        }

        // Split on comma or semicolon for bulk typing or pasting
        val tokens = inputText.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.length in 2..30 }

        val currentNormalized = tags.map { InterestMatcher.normalize(it) }.toSet()
        val newUniqueTags = mutableListOf<String>()

        for (token in tokens) {
            val norm = InterestMatcher.normalize(token)
            if (norm !in currentNormalized && newUniqueTags.none { InterestMatcher.normalize(it) == norm }) {
                if (tags.size + newUniqueTags.size < maxTags) {
                    newUniqueTags.add(token)
                }
            }
        }

        if (newUniqueTags.isNotEmpty()) {
            onTagsChanged(tags + newUniqueTags)
        }
        inputText = ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tag Chips FlowRow
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EchoSpace.xs),
                verticalArrangement = Arrangement.spacedBy(EchoSpace.xs)
            ) {
                tags.forEach { tag ->
                    RemovableTagChip(
                        text = tag,
                        onRemove = {
                            onTagsChanged(tags.filterNot { it.equals(tag, ignoreCase = true) })
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(EchoSpace.sm))
        }

        // Text Input Field
        if (!isLimitReached) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    if (newText.contains(",") || newText.contains(";") || newText.contains("\n")) {
                        inputText = newText
                        commitTags()
                    } else {
                        inputText = newText
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commitTags() }
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = EchoTextPrimary),
                cursorBrush = SolidColor(EchoAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EchoRadius.md))
                    .background(EchoElevated)
                    .border(
                        width = 1.dp,
                        color = if (inputText.isNotBlank()) EchoAccent.copy(alpha = 0.4f) else EchoHairline,
                        shape = RoundedCornerShape(EchoRadius.md)
                    )
                    .padding(horizontal = EchoSpace.md, vertical = EchoSpace.sm + 2.dp),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (tags.isEmpty()) "Add an interest or skill (e.g. AI, Kotlin, Guitar)..." else "Add another interest...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EchoTextTertiary.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Helper count text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = if (isLimitReached) "Maximum $maxTags tags reached" else "${tags.size}/$maxTags tags",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLimitReached) EchoAccent else EchoTextTertiary,
                fontSize = 11.sp
            )
        }
    }
}
