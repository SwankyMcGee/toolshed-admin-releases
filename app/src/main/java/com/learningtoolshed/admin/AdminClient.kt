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
   and the admin key (ideas-engine/ADMIN.md). They live in Prefs.

   API version 1:
     GET  ?key=…&action=state
     GET  ?key=…&action=ideas
     POST  key, action=read    ids=1,2,3
     POST  key, action=status  id, status, note
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

    /* Palette id, mirroring localStorage["workCycleTimer.settings.v1"].theme.
       The app has its own copy of that setting — the browser can't read
       an app's storage — so the picker writes here. */
    var paletteId: String
        get() = prefs.getString(KEY_PALETTE, "calm") ?: "calm"
        set(value) = prefs.edit().putString(KEY_PALETTE, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun isConfigured(): Boolean = endpoint.isNotBlank() && key.isNotBlank()

    /* ---------- requests ---------- */

    private fun get(action: String, params: Map<String, String> = emptyMap()): JSONObject {
        val query = buildString {
            append("key=").append(enc(key))
            append("&action=").append(enc(action))
            params.forEach { (k, v) -> append("&").append(enc(k)).append("=").append(enc(v)) }
        }
        val url = "$endpoint?$query"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        return readJson(conn)
    }

    private fun post(action: String, params: Map<String, String>): JSONObject {
        val body = buildString {
            append("key=").append(enc(key))
            append("&action=").append(enc(action))
            params.forEach { (k, v) -> append("&").append(enc(k)).append("=").append(enc(v)) }
        }.toByteArray()

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

    /* ---------- the four actions ---------- */

    /** Connection test: refuses politely when the key is wrong. */
    fun check(): JSONObject = get("state")

    fun ideas(): List<Idea> {
        val obj = get("ideas")
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("ideas") ?: JSONArray()
        return (0 until arr.length()).map { parseIdea(arr.getJSONObject(it)) }
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
        val obj = get("replies", mapOf("id" to ideaId.toString()))
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

    /** Usage rows for the last 90 days: path, day, views, seconds. */
    fun pageviews(): List<PageView> {
        val obj = get("pageviews")
        if (!obj.optBoolean("ok")) throw AdminException(obj.optString("error", "unknown error"))
        val arr = obj.optJSONArray("rows") ?: JSONArray()
        return (0 until arr.length()).map { parsePageView(arr.getJSONObject(it)) }
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

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_KEY = "key"
        const val KEY_PALETTE = "palette"
        const val KEY_LAST_SYNC = "lastSync"
    }
}

class AdminException(message: String) : Exception(message)