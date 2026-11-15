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
    val loadingReplies: Boolean = false
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
}

class InboxViewModel(app: Application) : AndroidViewModel(app) {

    private val client = AdminClient(app)

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    var paletteId: String
        get() = client.paletteId
        set(value) { client.paletteId = value }

    val endpoint: String get() = client.endpoint
    val key: String get() = client.key

    fun saveConnection(endpoint: String, key: String) {
        client.endpoint = endpoint
        client.key = key
        refresh()
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
                    val list = client.ideas()
                    /* Only notify for rows that appeared since the last
                       successful sync — not on the very first load, when
                       "new to me" just means "I haven't opened it yet". */
                    val knownIds = before.ideas.mapTo(HashSet()) { it.id }
                    val baseline = client.lastSyncAt > 0L && before.ideas.isNotEmpty()
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
                    lastSyncAt = client.lastSyncAt
                )
                if (newOnes.isNotEmpty()) notifyNew(newOnes)
            } catch (err: Exception) {
                _state.value = _state.value.copy(
                    load = if (_state.value.ideas.isEmpty()) LoadState.Failed else LoadState.Ready,
                    message = describe(err)
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
            "The key was refused. Check it in Settings."
        err is java.net.UnknownHostException ->
            "No connection. Check the internet and try again."
        err is java.net.SocketTimeoutException ->
            "The sheet took too long to answer. Try again."
        else -> err.message ?: "Something went wrong."
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}