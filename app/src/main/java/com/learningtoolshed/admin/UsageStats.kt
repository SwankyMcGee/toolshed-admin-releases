package com.learningtoolshed.admin

import org.json.JSONObject
import java.util.Calendar

/* ============================================================
   Usage statistics.

   Two ways in, one shape out:

     fromPayload() — the backend has already rolled the rows up per
       tool and per page (action=usage). Small, fast, and the only
       approach that survives a busy site.

     of() — the app rolls up raw PageViews rows itself, using the site
       registry. Used when the backend is older than API 4, and by the
       tests, because it's pure.

   Either way the shape is Usage, and the screen doesn't care which
   produced it — except for `source`, which it shows, because "live" and
   "rollup" are different claims about freshness.

   A path the registry doesn't know shows up in `unmapped` rather than
   vanishing. That list is the to-do: each row is a page waiting for an
   entry in site-manifest.json.
   ============================================================ */

/** One tool's (or one resource page's, or one unknown path's) totals. */
data class UsageStat(
    val label: String,
    val views: Int,
    val seconds: Int,
    val key: String = "",
    val isTool: Boolean = false
) {
    val averageSeconds: Int get() = if (views == 0) 0 else seconds / views
}

data class Usage(
    val views: Int,
    val seconds: Int,
    val perDay: List<Bucket>,          // oldest first
    val byTool: List<UsageStat>,       // biggest first, tools only
    val byResource: List<UsageStat>,   // biggest first, resource pages
    val topPages: List<UsageStat>,     // biggest first, every page
    val unmapped: List<UsageStat>,     // paths the registry doesn't know
    val firstDay: String,
    val lastDay: String,
    val source: String                 // "rollup" | "live" | "local"
) {
    val maxDay: Int get() = perDay.maxOfOrNull { it.count } ?: 1
    val hasUnmapped: Boolean get() = unmapped.isNotEmpty()

    val sourceLabel: String
        get() = when (source) {
            "rollup" -> "Nightly totals"
            "live" -> "Live from the sheet"
            else -> "Computed on this device"
        }
}

object UsageStats {

    fun empty(): Usage = Usage(
        views = 0, seconds = 0,
        perDay = emptyList(),
        byTool = emptyList(),
        byResource = emptyList(),
        topPages = emptyList(),
        unmapped = emptyList(),
        firstDay = "", lastDay = "", source = "local"
    )

    /* ---------- from the backend (action=usage) ---------- */

    fun fromPayload(root: JSONObject, manifest: SiteManifest): Usage {
        val tools = root.optJSONArray("tools").objects().map { o ->
            val id = o.optString("id")
            UsageStat(
                label = o.optString("name").ifBlank { manifest.toolName(id).ifBlank { id } },
                views = o.optInt("views"),
                seconds = o.optInt("seconds"),
                key = id,
                isTool = true
            )
        }.sortedByDescending { it.views }

        /* The payload carries its own titles. The registry is the
           fallback for a backend that sent a path but no title — and
           it's what keeps a renamed page reading the same in both
           places. */
        val resources = root.optJSONArray("resources").objects().map { o ->
            val path = o.optString("path")
            UsageStat(
                label = o.optString("title").ifBlank {
                    manifest.titleForPath(path).ifBlank { path }
                },
                views = o.optInt("views"),
                seconds = o.optInt("seconds"),
                key = path
            )
        }.sortedByDescending { it.views }

        val pages = root.optJSONArray("pages").objects().map { o ->
            UsageStat(
                label = o.optString("title").ifBlank { o.optString("path") },
                views = o.optInt("views"),
                seconds = o.optInt("seconds"),
                key = o.optString("path")
            )
        }.sortedByDescending { it.views }

        val unmapped = root.optJSONArray("unmapped").objects().map { o ->
            UsageStat(
                label = o.optString("path"),
                views = o.optInt("views"),
                seconds = o.optInt("seconds"),
                key = o.optString("path")
            )
        }.sortedByDescending { it.views }

        /* The backend sends a day label per bucket; turn it into the
           short axis label the chart draws. */
        val perDay = root.optJSONArray("perDay").objects().map { o ->
            Bucket(shortDay(o.optString("day")), o.optInt("views"))
        }

        return Usage(
            views = root.optInt("totalViews"),
            seconds = root.optInt("totalSeconds"),
            perDay = perDay,
            byTool = tools,
            byResource = resources,
            topPages = pages.take(20),
            unmapped = unmapped,
            firstDay = root.optString("firstDay"),
            lastDay = root.optString("lastDay"),
            source = root.optString("source").ifBlank { "rollup" }
        )
    }

    /* ---------- from raw rows (older backend, tests) ---------- */

    fun of(rows: List<PageView>, manifest: SiteManifest): Usage {
        if (rows.isEmpty()) return empty()

        val views = rows.sumOf { it.views }
        val seconds = rows.sumOf { it.seconds }

        /* Per canonical page, summed over every day. */
        val byPath = rows.groupBy { SiteRegistry.canonicalPath(it.path) }
            .map { (path, group) ->
                Triple(
                    path,
                    group.sumOf { it.views },
                    group.sumOf { it.seconds }
                )
            }

        /* Tools: every page the registry tags with that tool. */
        val byTool = manifest.tools.map { tool ->
            val mine = byPath.filter { manifest.toolForPath(it.first) == tool.id }
            UsageStat(
                label = tool.name,
                views = mine.sumOf { it.second },
                seconds = mine.sumOf { it.third },
                key = tool.id,
                isTool = true
            )
        }.filter { it.views > 0 }.sortedByDescending { it.views }

        /* Resource pages: one row each, so a rarely-opened guide still
           shows a zero and doesn't silently vanish. */
        val byResource = manifest.resourcePages.map { page ->
            val hit = byPath.firstOrNull { it.first == SiteRegistry.canonicalPath(page.path) }
            UsageStat(
                label = page.title,
                views = hit?.second ?: 0,
                seconds = hit?.third ?: 0,
                key = page.path
            )
        }.sortedByDescending { it.views }

        /* Every page, most-used first. */
        val allPages = byPath.map { (path, v, s) ->
            UsageStat(
                label = manifest.titleForPath(path),
                views = v,
                seconds = s,
                key = path
            )
        }.sortedByDescending { it.views }

        /* Paths the registry doesn't know — the to-do list. */
        val unmapped = byPath
            .filter { (path, _, _) -> !manifest.isKnownPath(path) && path != "/" }
            .map { (path, v, s) ->
                UsageStat(label = path, views = v, seconds = s, key = path)
            }
            .sortedByDescending { it.views }

        val perDay = perDay(rows, 30)

        val days = rows.map { it.day }.filter { it.isNotBlank() }.sorted()
        return Usage(
            views = views,
            seconds = seconds,
            perDay = perDay,
            byTool = byTool,
            byResource = byResource,
            topPages = allPages.take(12),
            unmapped = unmapped,
            firstDay = days.firstOrNull() ?: "",
            lastDay = days.lastOrNull() ?: "",
            source = "local"
        )
    }

    /** Sum views per day for the last `days` days, oldest first.
        Labels are short ("3 Nov") so the axis fits on a phone. */
    private fun perDay(rows: List<PageView>, days: Int): List<Bucket> {
        val byDay = rows.groupBy { it.day }
            .map { (day, group) -> day to group.sumOf { it.views } }
            .toMap()

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.UK)
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        return (0 until days).map { offset ->
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -(days - 1 - offset))
            val k = key.format(c.time)
            Bucket(fmt.format(c.time), byDay[k] ?: 0)
        }
    }

    private fun shortDay(iso: String): String {
        if (iso.isBlank()) return ""
        return runCatching {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso)
                ?: return iso
            java.text.SimpleDateFormat("d MMM", java.util.Locale.UK).format(d)
        }.getOrDefault(iso)
    }

    private fun org.json.JSONArray?.objects(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        if (this == null) return out
        for (i in 0 until length()) out.add(getJSONObject(i))
        return out
    }
}

/** "4m 12s", "38s", "1h 04m" — whatever reads best for the size. */
fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0s"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}