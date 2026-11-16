package com.learningtoolshed.admin

import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================
   One ViewModel for the whole app.

   The screen is a filtered view of one list, held in memory, so
   "All / Unread / Tool / Feature" are instant and work offline.
   Writes (mark read, status) are optimistic: the list changes
   first, then the sheet is told, and a failure is reported as a
   small warning rather than losing the change.

   The list is the inbox; the notification is what makes it worth
   carrying. See notifyNew_.
   ============================================================ */

data class Filters(
    val query: String = "",
    val onlyUnread: Boolean = false,
    val onlyTool: Boolean = false,
    val onlyFeature: Boolean = false
)

enum class LoadState { Idle, Loading, Ready, Failed, NotConfigured }

data class InboxState(
    val ideas: List<Idea> = emptyList(),
    val filters: Filters = Filters(),
    val load: LoadState = LoadState.Idle,
    val message: String? = null,
    val lastSyncAt: Long = 0L,
    /* Replies for the idea currently open in the sheet. Empty when
       no idea is open, or when the open idea has no replies. */
    val replies: List<Reply> = emptyList(),
    val loadingReplies: Boolean = false,
    /* The site registry — which tools exist, which page is which.
       Arrives from the backend, falls back to the copy in the APK. */
    val manifest: SiteManifest = SiteManifest.empty(),
    /* Usage, already rolled up (or computed here when the backend is
       older than API 4). */
    val usage: Usage = UsageStats.empty(),
    val loadingUsage: Boolean = false,
    val usageError: String? = null,
    /* The oldest app build the backend still supports. */
    val minSupportedVersionCode: Int = 0,
    /* Who is signed in, and what the server will accept. Both come from
       the server (action=state), so the Settings screen can offer Google
       sign-in only where it actually works. */
    val signedInEmail: String? = null,
    val authClientId: String = "",
    val authGoogle: Boolean = false,
    val signingIn: Boolean = false,
    val signInError: String? = null
) {
    val visible: List<Idea>
        get() {
            val q = filters.query.trim().lowercase()
            return ideas.filter { idea ->
                if (filters.onlyUnread && idea.read) return@filter false
                if (filters.onlyTool && !idea.isFeature && !idea.type.equals("Tool", true)) return@filter false
                if (filters.onlyFeature && !idea.isFeature) return@filter false
                if (q.isNotEmpty()) {
                    val hay = listOf(idea.idea, idea.name, idea.tool, idea.note, idea.type)
                        .joinToString(" ").lowercase()
                    if (!hay.contains(q)) return@filter false
                }
                true
            }
        }

    val unreadCount: Int get() = ideas.count { !it.read }

    /** True when this build is older than the backend asks for. */
    val tooOld: Boolean
        get() = minSupportedVersionCode > 0 &&
            BuildConfig.VERSION_CODE < minSupportedVersionCode

    /* Feature flags, straight from the manifest. A screen the backend
       has switched off simply isn't offered. */
    val showUsage: Boolean get() = manifest.flag("usage")
    val showAnalytics: Boolean get() = manifest.flag("analytics")
    val showReplies: Boolean get() = manifest.flag("replies")
}

class InboxViewModel(app: Application) : AndroidViewModel(app) {

    private val client = AdminClient(app)

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    var paletteId: String
        get() = client.paletteId
        set(value) { client.paletteId = value }

    val endpoint: String get() = client.endpoint

    fun saveConnection(endpoint: String) {
        client.endpoint = endpoint
        refresh()
    }

    /* ---------- signing in ----------

       The credential lives in AdminClient; this is the part that talks
       to the screen. Sign-in itself needs a button and an Activity, so
       MainActivity runs GoogleAuth and hands the result back here. */

    /** True when the app already holds a Google session to send. */
    fun hasStoredSession(): Boolean = client.usesGoogle

    /** What the server will accept. Safe to call offline: it returns
        the last answer it had. */
    fun authConfigNow(): AuthConfig? = runCatching { client.authConfig(force = true) }.getOrNull()

    /** Called on launch. Returns what the server will accept, so the
        caller can decide whether a silent sign-in is worth trying
        without asking the server a second time. */
    suspend fun loadAuth(): AuthConfig? {
        val cfg = withContext(Dispatchers.IO) { authConfigNow() }
        _state.value = _state.value.copy(
            signedInEmail = client.accountEmail.takeIf { it.isNotBlank() },
            authClientId = cfg?.clientId ?: client.authClientId,
            authGoogle = cfg?.google ?: client.authClientId.isNotBlank()
        )
        return cfg
    }

    fun markSigningIn() {
        _state.value = _state.value.copy(signingIn = true, signInError = null)
    }

    /** A token from Google. The server is asked who it thinks this is —
        the token's own claims would need a JWT parser to read, and the
        server has already checked them. */
    fun onSignedIn(idToken: String) {
        client.idToken = idToken
        viewModelScope.launch {
            _state.value = _state.value.copy(signingIn = false, signInError = null)
            try {
                val info = withContext(Dispatchers.IO) { client.check() }
                _state.value = _state.value.copy(
                    signedInEmail = info.optString("email").takeIf { it.isNotBlank() },
                    authClientId = client.authClientId,
                    authGoogle = true
                )
            } catch (err: Exception) {
                /* Signed in at Google, refused by the server. That is
                   almost always the allowlist, and the sentence says so. */
                _state.value = _state.value.copy(signInError = describe(err))
            }
            /* The inbox, and the registry: the first of these is the
               point of signing in, the second was skipped on launch
               because there was no credential to ask with. */
            loadManifest()
            refresh(showProgress = true)
        }
    }

    fun onSignInFailed(message: String) {
        _state.value = _state.value.copy(signingIn = false, signInError = message)
    }

    /** Forget the Google session on this device. The server's allowlist
        is the other half — removing an address there is what actually
        shuts someone out. */
    fun signOut() {
        val app = getApplication<Application>()
        /* The local half is immediate: the screen must not keep saying
           "signed in" while Google's own state is being cleared. */
        client.signOut()
        _state.value = _state.value.copy(
            signedInEmail = null,
            load = if (client.isConfigured()) LoadState.Ready else LoadState.NotConfigured
        )
        viewModelScope.launch {
            runCatching { GoogleAuth.signOut(app) }
        }
    }

    fun setPalette(id: String) {
        client.paletteId = id
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(query = q))
    }

    fun toggleUnread() {
        val f = _state.value.filters
        _state.value = _state.value.copy(
            filters = f.copy(onlyUnread = !f.onlyUnread, onlyTool = false, onlyFeature = false)
        )
    }

    fun toggleType(type: String) {
        val f = _state.value.filters
        val onlyTool = if (type == "tool") !f.onlyTool else false
        val onlyFeature = if (type == "feature") !f.onlyFeature else false
        _state.value = _state.value.copy(
            filters = f.copy(onlyUnread = false, onlyTool = onlyTool, onlyFeature = onlyFeature)
        )
    }

    fun clearFilters() {
        _state.value = _state.value.copy(filters = Filters())
    }

    fun refresh() {
        refresh(showProgress = true)
    }

    /** The one that does the work. `showProgress` is false for the
        once-on-launch fetch, so the first screen isn't an empty spinner. */
    fun refresh(showProgress: Boolean) {
        if (!client.isConfigured()) {
            _state.value = _state.value.copy(load = LoadState.NotConfigured)
            return
        }
        viewModelScope.launch {
            val before = _state.value
            _state.value = before.copy(
                load = if (showProgress) LoadState.Loading else before.load,
                message = null
            )
            try {
                val (ideas, newOnes) = withContext(Dispatchers.IO) {
                    /* The cache is the screen's starting point: whatever
                       the last sync left behind, so the app opens with
                       content instead of a spinner. */
                    val cached = client.cachedIdeas()
                    val baseline = client.lastSyncAt > 0L && cached.isNotEmpty()

                    /* One read: the rows touched since the last sync,
                       merged onto the cache. On an API 4 backend that's
                       a few rows; on an older one it's everything, which
                       is what v1.6 did anyway. */
                    val list = client.syncChanges()

                    /* Only notify for rows that appeared since the last
                       successful sync — not on the very first load, when
                       "new to me" just means "I haven't opened it yet". */
                    val knownIds = cached.mapTo(HashSet()) { it.id }
                    val fresh = if (baseline) {
                        list.filter { !it.read && !knownIds.contains(it.id) }
                    } else {
                        emptyList()
                    }
                    list to fresh
                }

                client.lastSyncAt = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    ideas = ideas,
                    load = LoadState.Ready,
                    lastSyncAt = client.lastSyncAt,
                    message = null
                )
                if (newOnes.isNotEmpty()) notifyNew(newOnes)
            } catch (err: Exception) {
                /* A failed sync is not an empty inbox: keep what's on
                   screen (or in the cache) and say what happened. */
                _state.value = _state.value.copy(
                    ideas = if (_state.value.ideas.isEmpty()) client.cachedIdeas()
                    else _state.value.ideas,
                    load = if (_state.value.ideas.isEmpty() && client.cachedIdeas().isEmpty()) {
                        LoadState.Failed
                    } else {
                        LoadState.Ready
                    },
                    message = describe(err)
                )
            }
        }
    }

    /**
     * The site registry: which tools exist, which page belongs to which.
     * Loads the copy bundled in the APK first (so the very first screen
     * is already right), then asks the backend and remembers the answer.
     */
    fun loadManifest() {
        val app = getApplication<Application>()
        val bundled = runCatching { SiteRegistry.bundled(app) }
            .getOrDefault(SiteManifest.empty())

        val cached = client.cachedManifest()
        _state.value = _state.value.copy(
            manifest = cached ?: bundled,
            minSupportedVersionCode = client.minSupportedVersionCode
        )
        if (!client.isConfigured()) return

        viewModelScope.launch {
            try {
                /* Ask the backend what it knows about itself first: the
                   API level decides whether usage can be rolled up, and
                   the minimum decides whether this build is too old. */
                withContext(Dispatchers.IO) { client.check() }
                val fresh = withContext(Dispatchers.IO) { client.manifest(bundled) }
                _state.value = _state.value.copy(
                    manifest = fresh,
                    minSupportedVersionCode = client.minSupportedVersionCode
                )
            } catch (err: Exception) {
                /* Offline, or an old backend. The bundled copy is a
                   complete registry, so the app carries on. */
                _state.value = _state.value.copy(
                    manifest = _state.value.manifest,
                    minSupportedVersionCode = client.minSupportedVersionCode
                )
            }
        }
    }

    /** Mark one idea read, both on screen and in the sheet. */
    fun markRead(id: Int) {
        val before = _state.value
        _state.value = before.copy(
            ideas = before.ideas.map { if (it.id == id) it.copy(read = true) else it }
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.markRead(listOf(id)) }
            } catch (err: Exception) {
                _state.value = _state.value.copy(message = "Couldn't save that as read — " + describe(err))
            }
        }
    }

    /** Mark everything currently listed as read. */
    fun markAllRead() {
        val ids = _state.value.ideas.filter { !it.read }.map { it.id }
        if (ids.isEmpty()) return
        _state.value = _state.value.copy(
            ideas = _state.value.ideas.map { it.copy(read = true) }
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.markRead(ids) }
            } catch (err: Exception) {
                _state.value = _state.value.copy(message = "Couldn't save those as read — " + describe(err))
            }
        }
    }

    fun setStatus(id: Int, status: String) {
        val before = _state.value
        _state.value = before.copy(
            ideas = before.ideas.map { if (it.id == id) it.copy(status = status) else it }
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.setStatus(id, status) }
            } catch (err: Exception) {
                _state.value = _state.value.copy(
                    ideas = before.ideas,
                    message = "Couldn't change the status — " + describe(err)
                )
            }
        }
    }

    fun saveNote(id: Int, note: String) {
        val before = _state.value
        _state.value = before.copy(
            ideas = before.ideas.map { if (it.id == id) it.copy(note = note) else it }
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.setStatus(id, statusOf(id), note) }
            } catch (err: Exception) {
                _state.value = _state.value.copy(
                    ideas = before.ideas,
                    message = "Couldn't save the note — " + describe(err)
                )
            }
        }
    }

    /* ---------- replies ---------- */

    fun loadReplies(ideaId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingReplies = true, replies = emptyList())
            try {
                val list = withContext(Dispatchers.IO) { client.replies(ideaId) }
                _state.value = _state.value.copy(replies = list, loadingReplies = false)
            } catch (err: Exception) {
                _state.value = _state.value.copy(
                    loadingReplies = false,
                    message = "Couldn't load the replies — " + describe(err)
                )
            }
        }
    }

    /** Post a reply. Optimistic: the new reply appears at once, and a
        failed save is reported without losing what was typed. */
    fun addReply(ideaId: Int, body: String, onDone: () -> Unit = {}) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return

        val local = Reply(
            id = -1, ideaId = ideaId, whenIso = "",
            author = "Admin", body = trimmed
        )
        _state.value = _state.value.copy(replies = _state.value.replies + local)

        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { client.addReply(ideaId, trimmed) }
                /* Replace the optimistic placeholder with the stored
                   one (it has a real id and timestamp). */
                _state.value = _state.value.copy(
                    replies = _state.value.replies.map {
                        if (it.id == -1 && it.body == trimmed) saved else it
                    }
                )
                onDone()
            } catch (err: Exception) {
                /* Drop the optimistic one — better to show nothing than
                   a reply the submitter will never get. */
                _state.value = _state.value.copy(
                    replies = _state.value.replies.filter { !(it.id == -1 && it.body == trimmed) },
                    message = "Couldn't send the reply — " + describe(err)
                )
            }
        }
    }

    private fun statusOf(id: Int): String =
        _state.value.ideas.firstOrNull { it.id == id }?.status ?: "new"

    /* ---------- usage ---------- */

    /**
     * Load usage for the Usage tab.
     *
     * Preferred: action=usage, already rolled up per tool and page by
     * the backend — a small answer that stays small as the site grows.
     * Fallback: action=pageviews plus on-device aggregation, for a
     * backend older than API 4.
     *
     * Both paths write through the cache, so the tab has something to
     * draw even when the read fails.
     */
    fun loadUsage() {
        if (!client.isConfigured()) {
            _state.value = _state.value.copy(usageError = "Not connected yet.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingUsage = true, usageError = null)
            val manifest = _state.value.manifest
            try {
                val usage = withContext(Dispatchers.IO) {
                    /* Prefer the backend's rollup. Anything other than
                       a usable answer — an older backend, no manifest on
                       the server, a slow network — falls through to
                       reading raw rows and summing them here. */
                    val payload = runCatching { client.usage(USAGE_DAYS) }.getOrNull()
                    payload
                        ?.let { UsageStats.fromPayload(it, manifest) }
                        ?: UsageStats.of(client.pageviews(USAGE_DAYS), manifest)
                }
                _state.value = _state.value.copy(
                    usage = usage,
                    loadingUsage = false,
                    usageError = if (usage.views == 0) "No visits recorded yet." else null
                )
            } catch (err: Exception) {
                /* Keep the cached rollup on screen if there is one. */
                val cached = client.cachedUsage()?.let { UsageStats.fromPayload(it, manifest) }
                _state.value = _state.value.copy(
                    usage = cached ?: _state.value.usage,
                    loadingUsage = false,
                    usageError = describe(err)
                )
            }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /* ---------- notification ---------- */

    private fun notifyNew(ideas: List<Idea>) {
        val app = getApplication<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)

        val first = ideas.first()
        val title = if (ideas.size == 1) {
            when {
                first.isFeature && first.tool.isNotBlank() -> "New idea for ${first.tool}"
                else -> "A new idea arrived"
            }
        } else {
            "${ideas.size} new ideas"
        }

        val text = if (ideas.size == 1) {
            first.idea.take(120)
        } else {
            ideas.take(4).joinToString("\n") { "• " + it.idea.take(60) }
        }

        val intent = android.app.PendingIntent.getActivity(
            app,
            0,
            android.content.Intent(app, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, ToolshedApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            /* The small icon stays the monochrome bell — Android tints
               it, and a coloured logo would arrive as a blob. The large
               one is the launcher plate, so the notification carries the
               brand the app does. */
            .setLargeIcon(android.graphics.drawable.Icon.createWithResource(app, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun describe(err: Exception): String = when {
        err is AdminException && err.message == "forbidden" ->
            "Sign-in was refused. Open Settings and sign in with Google."
        /* The Google path, in the server's own words. Each one is a
           different job for the person holding the phone, which is why
           v6 stopped answering "forbidden" to all of them. */
        err is AdminException && err.message == "not allowed" ->
            "This Google account isn't on the list. Ask whoever runs the sheet to add it to ADMIN_EMAILS."
        err is AdminException && err.message == "token expired" ->
            "Your sign-in has expired. Open Settings and sign in again."
        err is AdminException && err.message == "bad token" ->
            "Google didn't accept that sign-in. Sign out and back in from Settings."
        err is AdminException && err.message == "token check failed" ->
            "Couldn't check your sign-in with Google. Try again in a moment."
        err is AdminException && err.message == "google sign-in not configured" ->
            "This server has Google sign-in switched off. The Apps Script needs OAUTH_CLIENT_ID and an allowlist in its properties."
        err is AdminException && err.message == "no credential" ->
            "Nothing was sent to sign in with. Open Settings and sign in with Google."
        err is AdminException && err.message == "key sign-in was removed; update the app" ->
            "This build is too old to sign in. Update from the releases page."
        /* The server answered — with the one sentence an Ideas Engine
           older than v5 says about a POST it doesn't route. Reads moved
           from GET to POST in v5, so an older deployment has nothing to
           do with action=ideas or action=usage. The redeploy is the fix;
           this is how you know. */
        err is AdminException && err.message == "unknown action" ->
            "The server is running an older Ideas Engine: it doesn't answer admin reads over POST. Redeploy the Apps Script — Deploy → Manage deployments → edit → New version → Deploy."
        err is java.net.UnknownHostException ->
            "No connection. Check the internet and try again."
        err is java.net.SocketTimeoutException ->
            "The sheet took too long to answer. Try again."
        else -> err.message ?: "Something went wrong."
    }

    companion object {
        private const val NOTIFICATION_ID = 4201

        /* How much usage to ask for. 90 days is the sheet's retention
           window, so anything longer would just be empty. */
        const val USAGE_DAYS = 90
    }
}