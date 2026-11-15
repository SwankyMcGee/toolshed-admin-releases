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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.InsertChart

/* ============================================================
   The shell.

   Two fixed zones and a scrolling middle:
     - a slim top bar: brand, unread count, refresh, palette, settings
     - a bottom navigation bar: Inbox / Overview / Usage

   The tabs live at the bottom rather than the top because on a
   portrait phone a top row holding a title + three tabs + three
   icons is wider than the screen, so it either clipped or
   collapsed the title. Bottom navigation is also where a thumb
   rests, and it leaves the full width for the title.
   ============================================================ */

private enum class Dialog { None, Settings, Palette }

private enum class Tab(val label: String) {
    Inbox("Inbox"),
    Overview("Overview"),
    Usage("Usage")
}

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
    onDismissMessage: () -> Unit,
    onLoadUsage: () -> Unit,
    /* Drawn above the navigation bar, in the layout rather than
       floating over it, so it can never cover the tabs. */
    banner: (@Composable () -> Unit)? = null
) {
    var dialog by remember { mutableStateOf(Dialog.None) }
    var openIdea by remember { mutableStateOf<Idea?>(null) }
    var tab by remember { mutableStateOf(Tab.Inbox) }

    /* When an idea is opened, load its replies once. */
    LaunchedEffect(openIdea?.id) {
        openIdea?.id?.let(onLoadReplies)
    }

    /* Usage rows are fetched the first time the tab is opened, then
       only when the user asks — visits accumulate slowly. */
    var usageLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Usage && !usageLoaded) {
            usageLoaded = true
            onLoadUsage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar(
            unread = state.unreadCount,
            syncing = state.load == LoadState.Loading,
            onRefresh = onRefresh,
            onSettings = { dialog = Dialog.Settings },
            onPalette = { dialog = Dialog.Palette }
        )

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.Inbox -> InboxScreen(
                    state = state,
                    onQueryChange = onQueryChange,
                    onToggleUnread = onToggleUnread,
                    onToggleType = onToggleType,
                    onClearFilters = onClearFilters,
                    onMarkAllRead = onMarkAllRead,
                    onRefresh = onRefresh,
                    onOpenIdea = { idea ->
                        openIdea = idea
                        if (!idea.read) onMarkRead(idea.id)
                    },
                    onOpenSettings = { dialog = Dialog.Settings }
                )

                Tab.Overview -> AnalyticsScreen(
                    ideas = state.ideas,
                    onRefresh = onRefresh
                )

                Tab.Usage -> UsageScreen(
                    state = state,
                    onRefresh = onLoadUsage
                )
            }

            /* A failure message floats above the list, not in it, so
               it never pushes the content down the page. */
            state.message?.let { msg ->
                MessageBar(
                    text = msg,
                    onDismiss = onDismissMessage,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        banner?.invoke()

        BottomTabs(
            selected = tab,
            unread = state.unreadCount,
            onSelect = { tab = it }
        )
    }

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

/* ---------- the inbox ---------- */

@Composable
private fun InboxScreen(
    state: InboxState,
    onQueryChange: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleType: (String) -> Unit,
    onClearFilters: () -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onOpenIdea: (Idea) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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

        when {
            state.load == LoadState.NotConfigured ->
                CenteredMessage(
                    title = "Not connected yet",
                    body = "Add the Ideas Engine URL and the admin key to start reading ideas.",
                    action = "Open settings",
                    onAction = onOpenSettings
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
                    start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { MetaLine(state) }
                items(state.visible, key = { it.id }) { idea ->
                    IdeaCard(idea = idea, onClick = { onOpenIdea(idea) })
                }
            }
        }
    }
}

/* ---------- top bar (the site's .site-head) ---------- */

@Composable
private fun TopBar(
    unread: Int,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onPalette: () -> Unit
) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "The Learning Toolshed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Admin",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
                if (unread > 0) {
                    UnreadBadge(count = unread)
                }
            }
        }

        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
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

/** The unread count, as a small filled pill. */
@Composable
private fun UnreadBadge(count: Int) {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.accent)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.accentText
        )
    }
}

/* ---------- bottom navigation ---------- */

@Composable
private fun BottomTabs(
    selected: Tab,
    unread: Int,
    onSelect: (Tab) -> Unit
) {
    val c = MaterialTheme.toolshed
    NavigationBar(containerColor = c.surface) {
        Tab.values().forEach { tab ->
            /* A dot on Inbox when there are unread ideas, so the
               count is visible from any tab without reading the
               title line. */
            val showDot = tab == Tab.Inbox && unread > 0
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Box {
                        Icon(
                            imageVector = tab.icon(),
                            contentDescription = tab.label,
                            tint = if (selected == tab) c.accent else c.dim
                        )
                        if (showDot) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(c.accent)
                                    .border(1.dp, c.surface, RoundedCornerShape(50))
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected == tab) c.accent else c.dim
                    )
                },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = c.accent,
                    selectedTextColor = c.accent,
                    indicatorColor = c.surface2,
                    unselectedIconColor = c.dim,
                    unselectedTextColor = c.dim
                )
            )
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Inbox -> Icons.Outlined.Inbox
    Tab.Overview -> Icons.Outlined.Insights
    Tab.Usage -> Icons.Outlined.InsertChart
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.toolshed.border)
    )
}

/* ---------- filters ---------- */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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

        /* FlowRow, not Row: on a portrait phone three labelled chips
           do not fit on one line, and a plain Row clipped the last. */
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            val filtering = filters.query.isNotEmpty() ||
                filters.onlyUnread || filters.onlyTool || filters.onlyFeature
            if (filtering) {
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
    Text(
        text = if (state.lastSyncAt == 0L) "Not synced yet"
        else "Synced " + Clock.format(state.lastSyncAt),
        style = MaterialTheme.typography.bodySmall,
        color = c.dim,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun IdeaCard(idea: Idea, onClick: () -> Unit) {
    val c = MaterialTheme.toolshed
    Card(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            /* Meta line: type, tool, status, timestamp — wraps, never
               clips, so a long tool name doesn't push the status off. */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!idea.read) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.accent)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TypeTag(idea.type)
                if (idea.isFeature && idea.tool.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = idea.tool,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusTag(idea.status)
            }

            Text(
                text = idea.idea,
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            /* Footer: who sent it, and when. */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (idea.name.isNotBlank()) {
                    Text(
                        text = "— " + idea.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Clock.formatIdea(idea.whenIso),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim,
                    maxLines = 1
                )
            }
        }
    }
}

/** Type as a small uppercase label — the sheet's own wording. */
@Composable
private fun TypeTag(type: String) {
    val c = MaterialTheme.toolshed
    Text(
        text = type.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = c.accent
    )
}

/** Status as a soft chip, so it scans without shouting. */
@Composable
private fun StatusTag(status: String) {
    val c = MaterialTheme.toolshed
    val label = status.replaceFirstChar { it.uppercase() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (status == "new") c.accent else c.dim
        )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(primary = true) {
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
private fun MessageBar(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(
                width = 1.dp,
                color = c.accent,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            )
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