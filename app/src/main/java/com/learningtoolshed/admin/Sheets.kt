package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/* ============================================================
   The three sheets: one idea, the connection, the palettes.

   They are Dialogs rather than a ModalBottomSheet so they behave the
   same on a phone and a tablet, and so the inbox behind them never
   moves — the site's palette panel is a drop-down, not a page.
   ============================================================ */

private fun sheetShape() = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

@Composable
private fun SheetFrame(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.toolshed
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheetShape())
                    .background(c.surface)
                    .border(1.dp, c.border, sheetShape())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

/* ---------- one idea ---------- */

@Composable
fun IdeaSheet(
    idea: Idea,
    replies: List<Reply>,
    loadingReplies: Boolean,
    onClose: () -> Unit,
    onSetStatus: (String) -> Unit,
    onSaveNote: (String) -> Unit,
    onSendReply: (String) -> Unit,
    onShareReply: (String) -> Unit
) {
    val c = MaterialTheme.toolshed
    var note by remember(idea.id) { mutableStateOf(idea.note) }
    var replyDraft by remember(idea.id) { mutableStateOf("") }

    val email = EmailLooks.extract(idea.name)

    SheetFrame(onDismiss = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Label(idea.type.uppercase())
            if (idea.isFeature && idea.tool.isNotBlank()) {
                Label(idea.tool)
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = Clock.formatIdea(idea.whenIso),
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }

        Text(
            text = idea.idea,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text
        )

        if (idea.name.isNotBlank()) {
            Text(
                text = "From " + idea.name,
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }

        SectionHeader("Where it's got to")

        /* Five statuses scroll sideways rather than squeezing the text
           on a small phone. */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STATUSES.forEach { status ->
                StatusChip(
                    status = status,
                    selected = idea.status == status,
                    onClick = { onSetStatus(status) }
                )
            }
        }

        SectionHeader("Your note")

        TextFieldLike(
            value = note,
            onValueChange = { note = it },
            minLines = 3
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(text = "Save note", filled = true, onClick = { onSaveNote(note) })
            PillButton(text = "Close", onClick = onClose)
        }

        /* ---------- replies ---------- */

        if (email != null) {
            SectionHeader("Replies")

            if (loadingReplies) {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
            }

            if (replies.isEmpty() && !loadingReplies) {
                Text(
                    text = "No replies yet. A reply is saved here and you send it from your mail app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
            }

            replies.forEach { reply ->
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reply.author,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = c.accent,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = Clock.formatIdea(reply.whenIso),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.dim
                            )
                        }
                        Text(
                            text = reply.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            TextFieldLike(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                minLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = "Send",
                    filled = true,
                    onClick = {
                        onSendReply(replyDraft)
                        replyDraft = ""
                    }
                )
                /* Hand the draft to the mail app instead — the reply
                   is still saved in the thread, but goes out from the
                   admin's own mail client. */
                if (replyDraft.isNotBlank()) {
                    PillButton(
                        text = "Open in mail",
                        onClick = {
                            onShareReply(replyDraft)
                            replyDraft = ""
                        }
                    )
                }
            }
        }
    }
}

/* ---------- connection ---------- */

@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val app = LocalContext.current.applicationContext
    val client = remember { AdminClient(app) }
    var url by remember { mutableStateOf(client.endpoint) }
    var key by remember { mutableStateOf(client.key) }
    val c = MaterialTheme.toolshed

    SheetFrame(onDismiss = onDismiss) {
        Text(
            text = "Connection",
            style = MaterialTheme.typography.titleMedium,
            color = c.text
        )
        Text(
            text = "Paste the Ideas Engine /exec URL and the admin key. Both stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = c.dim
        )

        Text(
            text = "ENDPOINT",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.dim
        )
        TextFieldLike(
            value = url,
            onValueChange = { url = it },
            minLines = 2
        )

        Text(
            text = "ADMIN KEY",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.dim
        )
        TextFieldLike(
            value = key,
            onValueChange = { key = it }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "Save",
                filled = true,
                onClick = { onSave(url.trim(), key.trim()) }
            )
            PillButton(text = "Cancel", onClick = onDismiss)
        }
    }
}

/* ---------- palettes ---------- */

@Composable
fun PaletteSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.toolshed
    SheetFrame(onDismiss = onDismiss) {
        Text(
            text = "Palette",
            style = MaterialTheme.typography.titleMedium,
            color = c.text
        )
        Text(
            text = "The same eleven as the site and the timer.",
            style = MaterialTheme.typography.bodySmall,
            color = c.dim
        )

        PALETTES.forEach { p ->
            val on = p.id == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.surface2 else c.surface)
                    .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(10.dp))
                    .clickable { onPick(p.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(p.bg, p.surface2, p.text, p.accent).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(swatch)
                                .border(1.dp, c.border, RoundedCornerShape(4.dp))
                        )
                    }
                }
                Text(
                    text = p.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) c.accent else c.text,
                    modifier = Modifier.weight(1f)
                )
                if (on) {
                    Text(text = "✓", color = c.accent)
                }
            }
        }
    }
}