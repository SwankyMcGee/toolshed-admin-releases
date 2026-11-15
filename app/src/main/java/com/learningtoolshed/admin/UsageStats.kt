package com.learningtoolshed.admin

import java.util.Calendar

/* ============================================================
   Usage statistics, computed on-device from the PageViews
   rows the backend returns.

   The backend gives per-path, per-day totals over the last 90
   days. This rolls them up the way the admin reads them:

     - per tool (summing every page that belongs to that tool)
     - per resource page
     - top pages overall
     - a daily views series for the last 30 days

   Times are shown as minutes when they're long enough to be
   readable ("4m 12s") and seconds when they're not ("38s").
   ============================================================ */

/** One tool's (or resource page's) usage totals. */
data class UsageStat(
    val label: String,
    val views: Int,
    val seconds: Int,
    val isTool: Boolean = false
) {
    val averageSeconds: Int get() = if (views == 0) 0 else seconds / views
}

data class Usage(
    val views: Int,
    val seconds: Int,
    val perDay: List<Bucket>,          // oldest first, last 30 days
    val byTool: List<UsageStat>,       // biggest first, tools only
    val byResource: List<UsageStat>,   // biggest first, resource pages
    val topPages: List<UsageStat>,     // biggest first, every page
    val firstDay: String,              // earliest day with any views
    val lastDay: String                // latest day with any views
) {
    val maxDay: Int get() = perDay.maxOfOrNull { it.count } ?: 1
}

object UsageStats {

    fun of(rows: List<PageView>): Usage {
        if (rows.isEmpty()) {
            return Usage(
                views = 0, seconds = 0,
                perDay = emptyList(),
                byTool = emptyList(),
                byResource = emptyList(),
                topPages = emptyList(),
                firstDay = "", lastDay = ""
            )
        }

        /* Total across every row. */
        val views = rows.sumOf { it.views }
        val seconds = rows.sumOf { it.seconds }

        /* Per canonical page, summed over every day. */
        val byPage = rows.groupBy { canonicalPath(it.path) }
            .map { (path, group) ->
                UsageStat(
                    label = titleForPath(path),
                    views = group.sumOf { it.views },
                    seconds = group.sumOf { it.seconds }
                )
            }

        /* Tools: every page tagged with that tool. */
        val byTool = TOOL_NAMES.map { tool ->
            val group = byPageForTool(rows, tool)
            UsageStat(
                label = tool,
                views = group.first,
                seconds = group.second,
                isTool = true
            )
        }.filter { it.views > 0 }.sortedByDescending { it.views }

        /* Resource pages: one row each, so a rarely-opened guide
           still shows a zero and doesn't silently vanish. */
        val byResource = RESOURCE_PAGES.map { page ->
            val group = byPage.firstOrNull { it.label == page.title }
            UsageStat(
                label = page.title,
                views = group?.views ?: 0,
                seconds = group?.seconds ?: 0
            )
        }.sortedByDescending { it.views }

        /* Top pages overall — the "what's actually being used" list. */
        val topPages = byPage.sortedByDescending { it.views }.take(12)

        /* Daily series for the last 30 days. */
        val perDay = perDay(rows, 30)

        val days = rows.map { it.day }.filter { it.isNotBlank() }.sorted()
        return Usage(
            views = views,
            seconds = seconds,
            perDay = perDay,
            byTool = byTool,
            byResource = byResource,
            topPages = topPages,
            firstDay = days.firstOrNull() ?: "",
            lastDay = days.lastOrNull() ?: ""
        )
    }

    /** Views + seconds for every page belonging to `tool`. */
    private fun byPageForTool(rows: List<PageView>, tool: String): Pair<Int, Int> {
        var v = 0
        var s = 0
        rows.forEach { row ->
            if (toolForPath(canonicalPath(row.path)) == tool) {
                v += row.views
                s += row.seconds
            }
        }
        return v to s
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