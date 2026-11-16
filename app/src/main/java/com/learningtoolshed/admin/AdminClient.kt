package com.learningtoolshed.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* ============================================================
   A tiny client for the Ideas Engine admin API.

   Deliberately dependency-free: one file, HttpURLConnection, and the
   org.json that ships with Android. No Retrofit, no Moshi, no
   coroutines-adapter — one APK, one less thing to break.

    Every call needs the same two settings: the Apps Script /exec URL
    and something the server will accept as an admin — a Google ID
    token (the normal case) or, on an older deployment, the shared key
    (ideas-engine/ADMIN.md). They live in Prefs.

    API version 6 (this build) — every admin call is a POST:

      POST action=state            -> { ok, admin, api, minSupportedVersionCode, auth, email }
      POST action=ideas            -> { ok, ideas }
      POST action=changes&since=   -> { ok, ideas, serverTime }
      POST action=replies&id=      -> { ok, replies }
      POST action=usage&days=      -> { ok, source, tools, resources, pages, … }
      POST action=manifest         -> { ok, manifest }
      POST action=pageviews&days=  -> { ok, rows }
      POST action=read   ids=1,2,3
      POST action=status id, status, note
      POST action=reply  id, body

    POST, not GET, for the one reason that matters: a credential in a
    query string ends up in history, in proxy logs, and in the Referer
    header of anything the URL touches. In the body it travels once,
    over TLS, and stops there. Version 4 was GET for reads; a v5
    backend refuses GET on admin actions and says so.

    The one public action is authconfig: the server says which Google
    client to ask for, so the app does not have to be built with one
    baked in. See GoogleAuth.

    Every read is cached to disk as it arrives. The app paints from the
    cache first and then refreshes, so it opens instantly and still says
    something useful on a train.
    ============================================================ */

const val DEFAULT_ENDPOINT =
    "https://script.google.com/macros/s/AKfycbzZG7bLh8EQTsnZN8pfkHiry_gQMT2PFggl7M4Ku1RAUjDgpHDGczpla_dsEXhmIA4jDw/exec"

/* The five statuses the sheet accepts; anything else reads as new. */
val STATUSES = listOf("new", "reading", "planned", "done", "shelved")

data class Idea(
    val id: Int,
    val type: String,      // Tool | Feature
    val whenIso: String,
    val idea: String,
    val name: String,
    val tool: String,
    val read: Boolean,
    val status: String,
    val note: String
) {
    val isFeature: Boolean get() = type.equals("Feature", ignoreCase = true)
}

class AdminClient(private val app: Context) {

    private val prefs = app.getSharedPreferences("toolshed_admin", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value.trim()).apply()

    var key: String
        get() = prefs.getString(KEY_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KEY, value.trim()).apply()

    /* ---------- who is asking ----------

       The ID token from Google, and the address it names. Both are kept
       because the 15-minute background sync has no screen to show: it
       runs with the app closed, so it cannot ask anyone to sign in and
       has to already know who it is.

       The token is the reason this is acceptable where a key was not. It
       expires in about an hour, so a copy on disk is a copy with a
       deadline; the server re-checks it with Google and only ever caches
       the answer until that same deadline. Sign-out clears both fields,
       and the server's allowlist is the other half — see GoogleAuth. */
    var idToken: String
        get() = prefs.getString(KEY_ID_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ID_TOKEN, value).apply()

    var accountEmail: String
        get() = prefs.getString(KEY_ACCOUNT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value).apply()

    /* The web OAuth client the server told us to ask for. Cached so a
       launch that is offline can still sign in — the value changes about
       as often as the app does. */
    var authClientId: String
        get() = prefs.getString(KEY_AUTH_CLIENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_CLIENT, value).apply()

    /* Palette id, mirroring localStorage["workCycleTimer.settings.v1"].theme.
       The app has its own copy of that setting — the browser can't read
       an app's storage — so the picker writes here. */
    var paletteId: String
        get() = prefs.getString(KEY_PALETTE, "calm") ?: "calm"
        set(value) = prefs.edit().putString(KEY_PALETTE, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /* The backend's clock at the last sync. Rows touched after this are
       what action=changes returns, so it's the high-water mark for the
       delta read — not the local clock, which a phone can have wrong. */
    var lastServerTime: Long
        get() = prefs.getLong(KEY_SERVER_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_SERVER_TIME, value).apply()

    /* What the backend says about itself, from action=state. */
    var apiVersion: Int
        get() = prefs.getInt(KEY_API, 0)
        set(value) = prefs.edit().putInt(KEY_API, value).apply()

    var minSupportedVersionCode: Int
        get() = prefs.getInt(KEY_MIN_SUPPORTED, 0)
        set(value) = prefs.edit().putInt(KEY_MIN_SUPPORTED, value).apply()

    /** True when the backend is too old for the delta/usage actions. */
    val supportsV4: Boolean get() = apiVersion >= 4

    /** True when this build is older than the backend will support. */
    fun isTooOld(): Boolean =
        minSupportedVersionCode > 0 && BuildConfig.VERSION_CODE < minSupportedVersionCode

    /* True when there is somewhere to send a request and something the
       server will accept. Either credential will do; the token wins
       when both are present. */
    fun isConfigured(): Boolean =
        endpoint.isNotBlank() && (idToken.isNotBlank() || key.isNotBlank())

    /** True when the Google session is the credential in use. */
    val usesGoogle: Boolean get() = idToken.isNotBlank()

    /** Forget the Google session. The allowlist on the server is the
        other half; this only stops the app offering. */
    fun signOut() {
        idToken = ""
        accountEmail = ""
    }

    /* ---------- requests ---------- */

    /**
     * Every admin call, reads included. The credential — a Google ID
     * token where there is one, the shared key otherwise — goes in the
     * body only.
     *
     * A single request style also means the backend needs one door for
     * admin traffic rather than two that can drift apart — and it means
     * a credential can never reach a query string by accident.
     */
    private fun post(action: String, params: Map<String, String> = emptyMap()): JSONObject {
        val body = formBody(idToken, key, action, params).toByteArray()
        return request(body)
    }

    /* ---------- the one public action ----------

       `authconfig` answers which Google client to ask for and whether
       the server will take a key at all. It is a POST like everything
       else — the rule about credentials in URLs does not stop applying
       just because this one has no credential — and it is cached, so
       opening Settings twice is one call, not two. */
    fun authConfig(force: Boolean = false): AuthConfig {
        if (!force && authClientId.isNotBlank()) {
            return AuthConfig(clientId = authClientId, google = true, key = key.isNotBlank())
        }
        val obj = request(actionBody("authconfig").toByteArray())
        val clientId = obj.optString("clientId")
        if (clientId.isNotBlank()) authClientId = clientId
        return AuthConfig(
            clientId = clientId,
            google = obj.optBoolean("google", clientId.isNotBlank()),
            key = obj.optBoolean("key", key.isNotBlank())
        )
    }

    private fun request(body: ByteArray): JSONObject {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body) }
        return readJson(conn)
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw AdminException("HTTP $code")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /* ---------- on-disk cache ----------
       Three small files in filesDir. Anything a read returns is written
       here before it reaches the screen; if the next read fails, the
       app still has a screen to show. */

    private fun cacheFile(name: String) = java.io.File(app.filesDir, "cache_$name.json")

    private fun readCache(name: String): String? =
        runCatching { cacheFile(name).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun writeCache(name: String, text: String) {
        runCatching { cacheFile(name).writeText(text) }
    }

    fun cachedIdeas(): List<Idea> {
        val text = readCache(CACHE_IDEAS) ?: return emptyList()
        return runCatching {
            val arr = JSONObject(text).optJSONArray("ideas") ?: JSONArray()
            (0 until arr.length()).map { parseIdea(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveIdeas(list: List<Idea>) {
        val root = JSONObject().put("ideas", JSONArray().apply {
            list.forEach { put(encodeIdea(it)) }
        })
        writeCache(CACHE_IDEAS, root.toString())
    }

    /** Write a merged list back, so the next launch has it. */
    fun cacheIdeas(list: List<Idea>) = saveIdeas(list)

    fun cachedManifest(): SiteManifest? {
        val text = readCache(CACHE_MANIFEST) ?: return null
        return SiteRegistry.parse(text)
    }

    private fun saveManifest(manifest: SiteManifest) {
        writeCache(CACHE_MANIFEST, encodeManifest(manifest))
    }

    fun cachedUsage(): JSONObject? {
        val text = readCache(CACHE_USAGE) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun saveUsage(payload: JSONObject) {
        writeCache(CACHE_USAGE, payload.toString())
    }

    /* ---------- the actions ---------- */

    /** Connection test. Also picks up the backend's API level and the
        oldest app build it still supports. */
    fun check(): JSONObject {
        val obj = post("state")
        apiVersion = obj.optInt("api", apiVersion)
        minSupportedVersionCode = obj.optInt("minSupportedVersionCode", minSupportedVersionCode)
        /* The server says which Google client it expects, so the app
           never needs one baked in, and it says who it took the request
           from — the address behind the token, which is what the
           Settings screen shows. */
        obj.optJSONObject("auth")
            ?.optString("clientId")
            ?.takeIf { it.isNotBlank() }
            ?.let { authClientId = it }
        obj.optString("email").takeIf { it.isNotBlank() }?.let { accountEmail = it }
        return obj
    }

    /** The site registry, from the backend. Falls back to the bundled
        copy when the backend is old, offline, or the site is down. */
    fun manifest(fallback: SiteManifest): SiteManifest {
        val obj = runCatching { post("manifest") }.getOrNull() ?: return fallback
        if (!obj.optBoolean("ok")) return fallback
        val json = obj.optJSONObject("manifest")?.toString() ?: return fallback
        val parsed = SiteRegistry.parse(json) ?: return fallback
        saveManifest(parsed)
        return parsed
    }

    fun ideas(): List<Idea> {
        val obj = post("ideas")
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("ideas") ?: JSONArray()
        val list = (0 until arr.length()).map { parseIdea(arr.getJSONObject(it)) }
        saveIdeas(list)
        return list
    }

    /**
     * Rows touched since `since` (backend clock, ms). On API 4+ this is
     * the cheap path; on an older backend it reads everything, which is
     * what v1.6 did anyway.
     */
    fun changes(since: Long): List<Idea> {
        val obj = if (supportsV4 && since > 0) {
            post("changes", mapOf("since" to since.toString()))
        } else {
            post("ideas")
        }
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("ideas") ?: JSONArray()
        val list = (0 until arr.length()).map { parseIdea(arr.getJSONObject(it)) }
        /* The server's clock is the high-water mark for the next call. */
        obj.optLong("serverTime", 0L).takeIf { it > 0 }?.let { lastServerTime = it }
        return list
    }

    /** The full list, for when the delta read can't be trusted. */
    fun ideasFull(): List<Idea> {
        lastServerTime = 0
        return ideas()
    }

    /** The changed rows plus what we already had, newest first. Shared
        by the ViewModel and the background worker so both stay in step. */
    fun syncChanges(): List<Idea> {
        val merged = IdeaMerger.merge(cachedIdeas(), changes(lastServerTime))
        cacheIdeas(merged)
        return merged
    }

    fun markRead(ids: List<Int>): Int {
        if (ids.isEmpty()) return 0
        val obj = post("read", mapOf("ids" to ids.joinToString(",")))
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        return obj.optInt("changed")
    }

    fun setStatus(id: Int, status: String, note: String? = null): Boolean {
        val params = buildMap {
            put("id", id.toString())
            put("status", status)
            if (note != null) put("note", note)
        }
        val obj = post("status", params)
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        return true
    }

    /** All replies on one idea, oldest first. */
    fun replies(ideaId: Int): List<Reply> {
        val obj = post("replies", mapOf("id" to ideaId.toString()))
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("replies") ?: JSONArray()
        return (0 until arr.length()).map { parseReply(arr.getJSONObject(it)) }
    }

    /** Post a reply. Returns the new reply as the sheet stored it. */
    fun addReply(ideaId: Int, body: String): Reply {
        val obj = post("reply", mapOf("id" to ideaId.toString(), "body" to body))
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        /* The backend echoes the reply back; if it doesn't, build one
           from what we sent so the UI can show it immediately. */
        return obj.optJSONObject("reply")?.let { parseReply(it) }
            ?: Reply(
                id = -1, ideaId = ideaId, whenIso = "",
                author = "Admin", body = body
            )
    }

    /** Usage rows for the last `days` days: path, day, views, seconds. */
    fun pageviews(days: Int): List<PageView> {
        val obj = post("pageviews", mapOf("days" to days.toString()))
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("rows") ?: JSONArray()
        return (0 until arr.length()).map { parsePageView(arr.getJSONObject(it)) }
    }

    /**
     * Usage, already rolled up per tool and page by the backend.
     * Returns null on an older backend (API < 4) so the caller can fall
     * back to pageviews() and aggregate on-device.
     */
    fun usage(days: Int): JSONObject? {
        if (!supportsV4) return null
        val obj = post("usage", mapOf("days" to days.toString()))
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        saveUsage(obj)
        return obj
    }

    private fun parseIdea(o: JSONObject) = Idea(
        id = o.optInt("id"),
        type = o.optString("type"),
        whenIso = o.optString("when"),
        idea = o.optString("idea"),
        name = o.optString("name"),
        tool = o.optString("tool"),
        read = o.optBoolean("read"),
        status = o.optString("status").ifBlank { "new" },
        note = o.optString("note")
    )

    private fun encodeIdea(i: Idea) = JSONObject().apply {
        put("id", i.id)
        put("type", i.type)
        put("when", i.whenIso)
        put("idea", i.idea)
        put("name", i.name)
        put("tool", i.tool)
        put("read", i.read)
        put("status", i.status)
        put("note", i.note)
    }

    /* The manifest is kept as the JSON we were given, not as objects —
       re-encoding it would mean two places to keep the field list in
       step, and the parser is already tolerant of missing keys. */
    private fun encodeManifest(manifest: SiteManifest): String = JSONObject().apply {
        put("manifestVersion", manifest.manifestVersion)
        put("updated", manifest.updated)
        put("minSupportedVersionCode", manifest.minSupportedVersionCode)
        put("features", JSONObject().apply {
            manifest.features.forEach { (k, v) -> put(k, v) }
        })
        put("tools", JSONArray().apply {
            manifest.tools.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("icon", t.icon)
                    put("path", t.path)
                    put("repoPath", t.repoPath)
                    put("blurb", t.blurb)
                })
            }
        })
        put("pages", JSONArray().apply {
            manifest.pages.forEach { p ->
                put(JSONObject().apply {
                    put("path", p.path)
                    put("title", p.title)
                    put("tool", p.tool)
                    put("kind", p.kind)
                })
            }
        })
    }.toString()

    private fun parseReply(o: JSONObject) = Reply(
        id = o.optInt("id"),
        ideaId = o.optInt("ideaId", o.optInt("idea_id")),
        whenIso = o.optString("when"),
        author = o.optString("author"),
        body = o.optString("body")
    )

    private fun parsePageView(o: JSONObject) = PageView(
        path = o.optString("path"),
        day = o.optString("day"),
        views = o.optInt("views"),
        seconds = o.optInt("seconds")
    )

    companion object {
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_KEY = "key"
        const val KEY_ID_TOKEN = "idToken"
        const val KEY_ACCOUNT = "account"
        const val KEY_AUTH_CLIENT = "authClient"
        const val KEY_PALETTE = "palette"
        const val KEY_LAST_SYNC = "lastSync"
        const val KEY_SERVER_TIME = "serverTime"
        const val KEY_API = "apiVersion"
        const val KEY_MIN_SUPPORTED = "minSupported"

        private const val CACHE_IDEAS = "ideas"
        private const val CACHE_MANIFEST = "manifest"
        private const val CACHE_USAGE = "usage"
    }
}

/** What the server said about how it wants to be signed in to. */
data class AuthConfig(
    val clientId: String,
    val google: Boolean,
    val key: Boolean
) {
    /** True when there is nothing here to offer: no Google, no key. */
    val empty: Boolean get() = !google && !key
}

/* ============================================================
   The body of every admin request.

   It is a top-level function, not a private method, for one reason: the
   credential has to be in here and must never reach a URL, and that is
   worth a test. A key containing '&' or '=' or '+' is perfectly
   possible — it's whatever the admin pasted in — and an unescaped one
   silently becomes two parameters, which reads as "forbidden" with no
   clue why. An ID token has the same problem: it is three base64 parts
   joined by dots, and base64 is full of '=' padding.

   The token is sent when there is one; the key is the fallback for a
   deployment that has not moved over. The server prefers the token
   when both arrive, so an app mid-migration is never judged on the half
   it is leaving behind.
   ============================================================ */

internal fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

/** action, then the action's own fields. No credential, so this is
    also what the public authconfig call is made of. */
internal fun actionBody(
    action: String,
    params: Map<String, String> = emptyMap()
): String = buildString {
    append("action=").append(enc(action))
    params.forEach { (k, v) -> append("&").append(enc(k)).append("=").append(enc(v)) }
}

internal fun formBody(
    idToken: String,
    key: String,
    action: String,
    params: Map<String, String> = emptyMap()
): String {
    val credential =
        if (idToken.isNotBlank()) "idToken=" + enc(idToken) else "key=" + enc(key)
    return credential + "&" + actionBody(action, params)
}

class AdminException(message: String) : Exception(message)

/* ============================================================
   Folding the delta read back into what we already have.

   The backend returns only rows touched since the last sync; this is
   the other half — overlay them on the cached list. A row that has
   vanished from the sheet is not detectable this way (the sheet has no
   deletions, and a deleted row keeps its neighbours' numbers stable
   only until something below it moves), so a full read every so often
   is what settles it.
   ============================================================ */

object IdeaMerger {

    fun merge(cached: List<Idea>, delta: List<Idea>): List<Idea> {
        if (delta.isEmpty()) return sort(cached)
        val byId = cached.associateBy { it.id }.toMutableMap()
        delta.forEach { byId[it.id] = it }
        return sort(byId.values.toList())
    }

    /** Newest first. ISO-8601 sorts as text, so this is chronological
        without parsing anything. */
    fun sort(list: List<Idea>): List<Idea> =
        list.sortedWith(compareByDescending<Idea> { it.whenIso }.thenByDescending { it.id })
}