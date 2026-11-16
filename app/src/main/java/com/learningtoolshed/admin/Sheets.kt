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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
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
        /* The box is a fraction of the screen rather than a fixed
           top gap, and the column is bottom-aligned inside it. The
           column can therefore be taller than the screen (a long
           idea with several replies) and scroll, instead of pushing
           its buttons off the bottom where they can't be reached.
           imePadding keeps the field above the keyboard. */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(top = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clip(sheetShape())
                    .background(c.surface)
                    .border(1.dp, c.border, sheetShape())
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
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
    onShareReply: (String) -> Unit,
    /* The manifest can switch replies off (features.replies). The rest
       of the sheet still works; only the thread is hidden. */
    showReplies: Boolean = true
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
            MetaChip(
                text = idea.type.uppercase(),
                accent = true,
                icon = if (idea.isFeature) Icons.Outlined.Lightbulb else Icons.Outlined.Construction
            )
            if (idea.isFeature && idea.tool.isNotBlank()) {
                MetaChip(text = idea.tool)
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = Clock.relative(idea.whenIso),
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }

        /* The idea is what the sheet was opened for, so it leads at the
           size a headline would. */
        Text(
            text = idea.idea,
            style = MaterialTheme.typography.titleMedium,
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

        if (showReplies && email != null) {
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = reply.author,
                                style = MaterialTheme.typography.labelMedium,
                                color = c.accent,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = Clock.relative(reply.whenIso),
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
                    TextAction(
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
    onSave: (String) -> Unit,
    signedInEmail: String? = null,
    googleReady: Boolean = false,
    signingIn: Boolean = false,
    signInError: String? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext
    val client = remember { AdminClient(app) }
    var url by remember { mutableStateOf(client.endpoint) }
    val c = MaterialTheme.toolshed

    SheetFrame(onDismiss = onDismiss) {
        BrandMarkH(height = 36.dp)
        Text(
            text = "Connection",
            style = MaterialTheme.typography.titleMedium,
            color = c.text
        )

        /* ---------- Google sign-in ----------
           The only way in. The button is offered only when the server
           says it will accept one — a deployment that has not set up
           Google gets an explanation instead of a dead button. */
        if (googleReady) {
            Text(
                text = "SIGN IN",
                style = MaterialTheme.typography.labelMedium,
                color = c.dim
            )

            if (signedInEmail != null) {
                /* The account as a card: an initial in a tinted circle,
                   the address beside it, and sign-out as a quiet verb
                   rather than a button that competes with the address. */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = signedInEmail.first().uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.accent
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Signed in as",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                        Text(
                            text = signedInEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.text,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextAction(text = "Sign out", onClick = onSignOut)
                }
            } else {
                Text(
                    text = "Your Google account is sent as a short-lived token, not stored. The server checks it against its list of allowed addresses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
                PillButton(
                    text = if (signingIn) "Signing in…" else "Sign in with Google",
                    filled = true,
                    onClick = onSignIn
                )
                if (signingIn) {
                    /* The picker is Google's own, over the top of this
                       sheet, so there is nothing to draw here — only the
                       line that says the app is waiting. */
                    Text(
                        text = "Waiting for Google…",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim
                    )
                }
            }

            signInError?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.accent.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.text
                    )
                }
            }
        } else {
            Text(
                text = "This server has no Google sign-in configured. The Apps Script needs OAUTH_CLIENT_ID and an allowlist before it will let anyone in.",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }

        Text(
            text = "ENDPOINT",
            style = MaterialTheme.typography.labelMedium,
            color = c.dim
        )
        TextFieldLike(
            value = url,
            onValueChange = { url = it },
            minLines = 2
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillButton(
                text = "Save",
                filled = true,
                onClick = { onSave(url.trim()) }
            )
            TextAction(text = "Cancel", onClick = onDismiss)

            /* The build, quietly, at the end: the one thing to read out
               when someone reports a problem. */
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
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