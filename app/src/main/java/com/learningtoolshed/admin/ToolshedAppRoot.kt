package com.learningtoolshed.admin

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MarkEmailUnread

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
    onSaveConnection: (String) -> Unit,
    /* Google sign-in. The button lives in Settings; the work needs an
       Activity, so MainActivity owns it and this only carries the call. */
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissMessage: () -> Unit,
    onLoadUsage: () -> Unit,
    onLoadManifest: () -> Unit,
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

    /* The site registry, once: bundled copy immediately, backend answer
       when it arrives. Everything downstream reads state.manifest. */
    LaunchedEffect(Unit) { onLoadManifest() }

    /* Usage rows are fetched the first time the tab is opened, then
       only when the user asks — visits accumulate slowly. */
    var usageLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Usage && !usageLoaded) {
            usageLoaded = true
            onLoadUsage()
        }
    }

    /* The tabs the manifest allows. A flag off means the screen isn't
       offered at all, rather than opening onto an empty panel. */
    val tabs = Tab.values().filter { candidate ->
        when (candidate) {
            Tab.Usage -> state.showUsage
            Tab.Overview -> state.showAnalytics
            Tab.Inbox -> true
        }
    }

    /* If the tab we're on has been switched off from the backend, fall
       back to the inbox rather than showing nothing. */
    LaunchedEffect(tabs, tab) {
        if (tab !in tabs) tab = Tab.Inbox
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
            /* Tabs cross-fade. The three screens are different shapes,
               and a hard cut between them reads as a glitch; a short
               fade keeps the chrome still and says "same app, other
               view". */
            Crossfade(
                targetState = tab,
                animationSpec = tween(durationMillis = 180),
                modifier = Modifier.fillMaxSize(),
                label = "tab"
            ) { current ->
                when (current) {
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
                        manifest = state.manifest,
                        onRefresh = onRefresh
                    )

                    Tab.Usage -> UsageScreen(
                        state = state,
                        onRefresh = onLoadUsage
                    )
                }
            }

            /* A failure message floats above the list, not in it, so
               it never pushes the content down the page. */
            state.message?.let { msg ->
                MessageBar(
                    text = msg,
                    onDismiss = onDismissMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }

        banner?.invoke()

        BottomTabs(
            tabs = tabs,
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
            onShareReply = { body -> onShareReply(body) },
            showReplies = state.showReplies
        )
    }

    when (dialog) {
        Dialog.Settings -> SettingsSheet(
            onDismiss = { dialog = Dialog.None },
            onSave = { url -> onSaveConnection(url); dialog = Dialog.None },
            signedInEmail = state.signedInEmail,
            googleReady = state.authGoogle,
            signingIn = state.signingIn,
            signInError = state.signInError,
            onSignIn = onSignIn,
            onSignOut = onSignOut
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
    val c = MaterialTheme.toolshed

    /* Filtering the inbox is the one piece of real work a screen does:
       it lowercases every searchable field of every idea. Doing that on
       each recomposition — every keystroke, every mark-read — is how a
       list that takes a millisecond starts to feel slow. Keyed on the
       two things the result depends on, so it runs when the data or the
       filters change and not otherwise. */
    val visible = remember(state.ideas, state.filters) { state.visible }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterRow(
            filters = state.filters,
            total = state.ideas.size,
            shown = visible.size,
            unread = state.unreadCount,
            onQueryChange = onQueryChange,
            onToggleUnread = onToggleUnread,
            onToggleType = onToggleType,
            onClearFilters = onClearFilters,
            onMarkAllRead = onMarkAllRead
        )

        /* The backend has asked for a newer build. Say so once, at the
           top, rather than letting an old app misread a newer shape. */
        if (state.tooOld) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Card(primary = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "This build is out of date",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        Text(
                            text = "The Ideas Engine now needs a newer version of this app. Update from the releases page; everything you see here is the older format.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                    }
                }
            }
        }

        when {
            state.load == LoadState.NotConfigured ->
                CenteredMessage(
                    title = "Not connected yet",
                    body = "Add the Ideas Engine URL and sign in with Google to start reading ideas.",
                    action = "Open settings",
                    onAction = onOpenSettings,
                    showLogo = true
                )

            state.ideas.isEmpty() && state.load == LoadState.Loading ->
                CenteredMessage(
                    title = "Looking…",
                    body = "Reading the Ideas Engine sheet.",
                    showLogo = true,
                    logoLoading = true
                )

            state.ideas.isEmpty() && state.load == LoadState.Failed ->
                CenteredMessage(
                    title = "Couldn't read the ideas",
                    body = state.message ?: "Check the connection and try again.",
                    action = "Try again",
                    onAction = onRefresh,
                    showLogo = true
                )

            state.ideas.isEmpty() ->
                CenteredMessage(
                    title = "Nothing in the shed yet",
                    body = "Ideas from the site land here the moment someone sends one.",
                    showLogo = true
                )

            visible.isEmpty() ->
                CenteredMessage(
                    title = "No ideas match",
                    body = "Try clearing the filters, or search for something else.",
                    action = "Clear filters",
                    onAction = onClearFilters
                )

            else -> WideContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { MetaLine(state) }
                    items(visible, key = { it.id }) { idea ->
                        IdeaCard(idea = idea, onClick = { onOpenIdea(idea) })
                    }
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
            /* The site's logo, not a text title — the logo already
               carries the words, so the heading was saying them twice. */
            BrandMarkH(height = 30.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Admin",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
                if (unread > 0) {
                    UnreadPill(count = unread)
                }
            }
        }

        /* While a sync is running the refresh icon itself becomes the
           logo, turning — the same mark, doing the work. */
        HeaderIconButton(label = "Refresh", onClick = onRefresh) {
            if (syncing) BrandSpinner() else IconRefresh()
        }
        HeaderIconButton(label = "Palette", onClick = onPalette) { IconPalette() }
        HeaderIconButton(label = "Settings", onClick = onSettings) { IconGear() }
    }
    Hairline()
}

/** The unread count, as a soft pill. A count is only interesting while
    it is small; past 99 the words carry it better than three digits. */
@Composable
private fun UnreadPill(count: Int) {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.accent.copy(alpha = 0.14f))
            .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (count > 99) "99+ new" else "$count new",
            style = MaterialTheme.typography.labelMedium,
            color = c.accent
        )
    }
}

/* ---------- bottom navigation ---------- */

@Composable
private fun BottomTabs(
    tabs: List<Tab>,
    selected: Tab,
    unread: Int,
    onSelect: (Tab) -> Unit
) {
    val c = MaterialTheme.toolshed
    NavigationBar(
        containerColor = c.surface,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { tab ->
            /* A count on Inbox rather than a dot: a dot says "something",
               a number says how much, and the whole point of the inbox is
               the size of the pile waiting. */
            val badge = if (tab == Tab.Inbox) unread else 0
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
                        if (badge > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(c.accent)
                                    .border(1.5.dp, c.surface, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (badge > 9) "9+" else badge.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.accentText
                                )
                            }
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
    unread: Int,
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SearchField(query = filters.query, onQueryChange = onQueryChange)

        /* FlowRow, not Row: on a portrait phone three labelled chips
           do not fit on one line, and a plain Row clipped the last.
           The count on Unread is the one number worth carrying into the
           chip — the pile is the point of the filter. */
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                text = if (unread > 0) "Unread $unread" else "Unread",
                selected = filters.onlyUnread,
                icon = Icons.Outlined.MarkEmailUnread,
                onClick = onToggleUnread
            )
            FilterChip(
                text = "Tool ideas",
                selected = filters.onlyTool,
                icon = Icons.Outlined.Construction,
                onClick = { onToggleType("tool") }
            )
            FilterChip(
                text = "Feature ideas",
                selected = filters.onlyFeature,
                icon = Icons.Outlined.Lightbulb,
                onClick = { onToggleType("feature") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (shown == total) "$total ideas" else "$shown of $total",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim,
                modifier = Modifier.weight(1f)
            )
            val filtering = filters.query.isNotEmpty() ||
                filters.onlyUnread || filters.onlyTool || filters.onlyFeature

            /* Actions are text, not pills: two small verbs in the corner
               of a list should not weigh more than the list. */
            if (filtering) TextAction(text = "Clear", onClick = onClearFilters)
            if (total > 0) TextAction(text = "All read", onClick = onMarkAllRead)
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
        Row(modifier = Modifier.fillMaxWidth()) {
            /* An unread idea carries a rail down its leading edge. It
               divides the list into seen and not-yet without a badge on
               every row, and it survives a long list better than a
               tint does — a column of coloured cards stops meaning
               anything by the fourth one. */
            if (!idea.read) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.accent)
                )
                Spacer(Modifier.width(11.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                /* Meta line: type, tool, status — wraps, never clips, so
                   a long tool name doesn't push the status off. */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaChip(
                        text = idea.type.uppercase(),
                        accent = !idea.read,
                        icon = if (idea.isFeature) Icons.Outlined.Lightbulb
                        else Icons.Outlined.Construction
                    )
                    if (idea.isFeature && idea.tool.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
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
                    MetaChip(
                        text = idea.status.replaceFirstChar { it.uppercase() },
                        icon = STATUS_ICONS[idea.status]
                    )
                }

                Text(
                    text = idea.idea,
                    /* The idea is the headline of its own card, so an
                       unread one is set as one. */
                    style = if (idea.read) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleSmall,
                    color = c.text,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                /* Footer: who sent it, and how long ago. */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        text = Clock.relative(idea.whenIso),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim,
                        maxLines = 1
                    )
                }
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
    onAction: (() -> Unit)? = null,
    showLogo: Boolean = false,
    logoLoading: Boolean = false
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
                /* The logo, not a spinner, on the states that are about
                   the app itself rather than about the ideas: not
                   connected, waiting, failed. "No ideas match" is about
                   the filter, so it stays text. */
                if (showLogo) {
                    BrandMarkH(
                        height = if (logoLoading) 54.dp else 42.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        loading = logoLoading
                    )
                }
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.accent.copy(alpha = 0.55f), shape)
            /* A little lift: the bar floats over the list, and a hairline
               border alone doesn't say so. */
            .shadow(6.dp, shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
        /* A close, not a button: the bar is a notice, and "Dismiss" made
           it look like something that needed confirming. */
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(c.surface2)
                .clickableNoRipple(onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = c.dim,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}