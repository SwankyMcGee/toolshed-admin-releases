package com.learningtoolshed.admin

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* ============================================================
   The shell. Top bar like the site's .site-head (white, thin
   bottom border, bold brand), a filter row, then the inbox as a
   lazy list of cards. A teacher's thumb lives on "refresh" and
   "unread", so those are the two things that are never buried.
   ============================================================ */

private enum class Dialog { None, Settings, Palette }

private enum class Tab { Inbox, Overview }

@Composable
fun ToolshedAppRoot(
    state: InboxState,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onMarkRead: (Int) -> Unit,
    onMarkAllRead: () -> Unit,
    onSetStatus: (Int, String) -> Unit,
    onSaveNote: (Int, String) -> Unit,
    onLoadReplies: (Int) -> Unit,
    onSendReply: (Int, String) -> Unit,
    onShareReply: (String) -> Unit,
    onSaveConnection: (String, String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var dialog by remember { mutableStateOf(Dialog.None) }
    var openIdea by remember { mutableStateOf<Idea?>(null) }
    var tab by remember { mutableStateOf(Tab.Inbox) }

    /* When an idea is opened, load its replies once. */
    androidx.compose.runtime.LaunchedEffect(openIdea?.id) {
        val id = openIdea?.id
        if (id != null) onLoadReplies(id)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            TopBar(
                unread = state.unreadCount,
                syncing = state.load == LoadState.Loading,
                tab = tab,
                onTabChange = { tab = it },
                onRefresh = onRefresh,
                onSettings = { dialog = Dialog.Settings },
                onPalette = { dialog = Dialog.Palette }
            )

            FilterRow(
                filters = state.filters,
                total = state.ideas.size,
                shown = state.visible.size,
                onQueryChange = onQueryChange,
                onToggleUnread = onToggleUnread,
                onToggleType = onToggleType,
                onClearFilters = onClearFilters,
                onMarkAllRead = onMarkAllRead
            )

            if (tab == Tab.Overview) {
                AnalyticsScreen(
                    ideas = state.ideas,
                    onRefresh = onRefresh
                )
            } else when {
                state.load == LoadState.NotConfigured ->
                    CenteredMessage(
                        title = "Not connected yet",
                        body = "Add the Ideas Engine URL and the admin key to start reading ideas.",
                        action = "Open settings",
                        onAction = { dialog = Dialog.Settings }
                    )

                state.ideas.isEmpty() && state.load == LoadState.Loading ->
                    CenteredMessage(title = "Looking…", body = "Reading the Ideas Engine sheet.")

                state.ideas.isEmpty() && state.load == LoadState.Failed ->
                    CenteredMessage(
                        title = "Couldn't read the ideas",
                        body = state.message ?: "Check the connection and try again.",
                        action = "Try again",
                        onAction = onRefresh
                    )

                state.ideas.isEmpty() ->
                    CenteredMessage(
                        title = "Nothing in the shed yet",
                        body = "Ideas from the site land here the moment someone sends one."
                    )

                state.visible.isEmpty() ->
                    CenteredMessage(
                        title = "No ideas match",
                        body = "Try clearing the filters, or search for something else.",
                        action = "Clear filters",
                        onAction = onClearFilters
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { MetaLine(state) }
                    items(state.visible, key = { it.id }) { idea ->
                        IdeaCard(
                            idea = idea,
                            onClick = {
                                openIdea = idea
                                if (!idea.read) onMarkRead(idea.id)
                            }
                        )
                    }
                }
            }

            if (state.message != null) {
                MessageBar(text = state.message!!, onDismiss = onDismissMessage)
            }
        }
    }

    /* The sheet swaps its content in place, so opening an idea doesn't
       animate a new screen in — gentler, and closer to the site. */
    openIdea?.let { idea ->
        IdeaSheet(
            idea = idea,
            replies = state.replies,
            loadingReplies = state.loadingReplies,
            onClose = { openIdea = null },
            onSetStatus = { s -> onSetStatus(idea.id, s); openIdea = idea.copy(status = s) },
            onSaveNote = { n -> onSaveNote(idea.id, n) },
            onSendReply = { body -> onSendReply(idea.id, body) },
            onShareReply = { body -> onShareReply(body) }
        )
    }

    when (dialog) {
        Dialog.Settings -> SettingsSheet(
            onDismiss = { dialog = Dialog.None },
            onSave = { url, key -> onSaveConnection(url, key); dialog = Dialog.None }
        )

        Dialog.Palette -> PaletteSheet(
            selected = paletteId,
            onPick = { onPaletteChange(it); dialog = Dialog.None },
            onDismiss = { dialog = Dialog.None }
        )

        Dialog.None -> Unit
    }
}

/* ---------- top bar (the site's .site-head) ---------- */

@Composable
private fun TopBar(
    unread: Int,
    syncing: Boolean,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onPalette: () -> Unit
) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "The Learning Toolshed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Text(
                text = "Admin" + if (unread > 0) " · $unread unread" else "",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }

        /* Inbox / Overview tabs — the two things the app does. */
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(c.bg)
                .border(1.dp, c.border, RoundedCornerShape(999.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TabPill(text = "Inbox", selected = tab == Tab.Inbox, onClick = { onTabChange(Tab.Inbox) })
            TabPill(text = "Overview", selected = tab == Tab.Overview, onClick = { onTabChange(Tab.Overview) })
        }

        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = c.accent
            )
        }

        HeaderIconButton(label = "Refresh", onClick = onRefresh) { IconRefresh() }
        HeaderIconButton(label = "Palette", onClick = onPalette) { IconPalette() }
        HeaderIconButton(label = "Settings", onClick = onSettings) { IconGear() }
    }

    Hairline()
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.surface else Color.Transparent)
            .border(1.dp, if (selected) c.accent else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.accent else c.dim
        )
    }
}

@Composable
private fun Hairline() {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.border)
    )
}

/* ---------- filters ---------- */

@Composable
private fun FilterRow(
    filters: Filters,
    total: Int,
    shown: Int,
    onQueryChange: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    val c = MaterialTheme.toolshed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchField(query = filters.query, onQueryChange = onQueryChange)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = if (filters.onlyUnread) "Unread ✓" else "Unread",
                filled = filters.onlyUnread,
                onClick = onToggleUnread
            )
            PillButton(
                text = "Tool ideas",
                filled = filters.onlyTool,
                onClick = { onToggleType("tool") }
            )
            PillButton(
                text = "Feature ideas",
                filled = filters.onlyFeature,
                onClick = { onToggleType("feature") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (shown == total) "$total ideas" else "$shown of $total",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim,
                modifier = Modifier.weight(1f)
            )
            if (filters.query.isNotEmpty() || filters.onlyUnread || filters.onlyTool || filters.onlyFeature) {
                PillButton(text = "Clear", onClick = onClearFilters)
                Spacer(Modifier.width(8.dp))
            }
            if (total > 0) {
                PillButton(text = "All read", onClick = onMarkAllRead)
            }
        }
    }
    Hairline()
}

/* ---------- one idea ---------- */

@Composable
private fun MetaLine(state: InboxState) {
    val c = MaterialTheme.toolshed
    val at = state.lastSyncAt
    Text(
        text = if (at == 0L) "Not synced yet"
        else "Synced " + Clock.format(at),
        style = MaterialTheme.typography.bodySmall,
        color = c.dim,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun IdeaCard(idea: Idea, onClick: () -> Unit) {
    val c = MaterialTheme.toolshed
    Card(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!idea.read) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.accent)
                    )
                }
                Text(
                    text = idea.type.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = c.accent
                )
                if (idea.isFeature && idea.tool.isNotBlank()) {
                    Text(
                        text = "· " + idea.tool,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = idea.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (idea.status == "new") c.accent else c.dim
                )
            }

            Text(
                text = idea.idea,
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (idea.name.isNotBlank()) {
                Text(
                    text = "— " + idea.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
            }
        }
    }
}

/* ---------- messages ---------- */

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = MaterialTheme.toolshed
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(20.dp), primary = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text
                )
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = c.dim)
                if (action != null && onAction != null) {
                    PillButton(text = action, filled = true, onClick = onAction)
                }
            }
        }
    }
}

@Composable
private fun MessageBar(text: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.toolshed
    /* Anchored under the top bar rather than at the bottom, so the
       error never covers the idea being read. */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface2)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
        PillButton(text = "Dismiss", onClick = onDismiss)
    }
}

/* ---------- top bar (the site's .site-head) ---------- */