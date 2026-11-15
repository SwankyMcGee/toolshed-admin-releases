package com.learningtoolshed.admin

import android.content.Context

/* ============================================================
   A tiny persistent set of row IDs the app has already seen.

   Used by the background sync to avoid re-notifying on the same
   idea every 15 minutes, and to avoid firing one notification per
   row on the very first sync (which would be the whole backlog).

   Stored as a comma-separated string in SharedPreferences —
   there are hundreds of ideas, not millions, so this is fine.
   ============================================================ */

object NotifiedIds {

    private const val PREFS = "toolshed_admin"
    private const val KEY = "notified_ids"

    fun read(app: Context): Set<Int> {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun contains(app: Context, id: Int): Boolean = read(app).contains(id)

    fun addAll(app: Context, ids: List<Int>) {
        if (ids.isEmpty()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val merged = read(app) + ids
        prefs.edit().putString(KEY, merged.joinToString(",")).apply()
    }

    fun clear(app: Context) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}