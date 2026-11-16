package com.learningtoolshed.admin

import java.util.Calendar

/* ============================================================
   Analytics, computed on-device from the ideas already in memory.

   Everything here is pure: give it a list of ideas (and the site
   registry), get back numbers and series. The Compose screen just
   draws them.

   Answers "what's in the inbox now":
     - ideas per week (last 12 weeks)
     - tool vs feature split
     - status breakdown
     - how many ideas each tool has
     - which ideas name a tool we don't know about

   It cannot answer "how fast do we close things" — that needs status
   history (see expanding_the_project.md, Option C).

   The tool list comes from the site registry, not from a constant here.
   Add a tool to site-manifest.json and it appears in these charts with
   no app release. Ideas that name a tool the registry doesn't know are
   counted separately instead of silently vanishing.
   ============================================================ */

data class Bucket(val label: String, val count: Int)

data class Analytics(
    val total: Int,
    val unread: Int,
    val perWeek: List<Bucket>,        // oldest first, last 12 weeks
    val byType: List<Bucket>,         // Tool / Feature
    val byStatus: List<Bucket>,       // new / reading / planned / done / shelved
    val byTool: List<Bucket>,         // every known tool, biggest first
    val topTools: List<Bucket>,       // the six busiest, by feature ideas
    val unmapped: List<Bucket>        // ideas naming a tool we don't know
) {
    val maxWeek: Int get() = perWeek.maxOfOrNull { it.count } ?: 1
    val maxType: Int get() = byType.maxOfOrNull { it.count } ?: 1
    val maxStatus: Int get() = byStatus.maxOfOrNull { it.count } ?: 1
    val maxTool: Int get() = byTool.maxOfOrNull { it.count } ?: 1
    val hasUnmapped: Boolean get() = unmapped.isNotEmpty()
}

object Stats {

    fun of(ideas: List<Idea>, manifest: SiteManifest): Analytics {
        val total = ideas.size
        val unread = ideas.count { !it.read }

        val byType = listOf(
            Bucket("Tool", ideas.count { it.type.equals("Tool", true) }),
            Bucket("Feature", ideas.count { it.type.equals("Feature", true) })
        )

        val byStatus = STATUSES.map { status ->
            Bucket(status.replaceFirstChar { it.uppercase() }, ideas.count { it.status == status })
        }

        /* Every tool in the registry gets a row, even at zero: an empty
           row says "nothing has been asked for here yet", which is a
           different fact from the tool not existing. */
        val byTool = manifest.tools
            .map { tool ->
                Bucket(
                    tool.name,
                    ideas.count { idea -> mentionsTool(idea, tool.name) }
                )
            }
            .sortedByDescending { it.count }

        val topTools = ideas
            .filter { it.isFeature && it.tool.isNotBlank() }
            .groupingBy { it.tool }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { Bucket(it.key, it.value) }

        /* An idea names a tool the site doesn't have — either a feature
           idea whose "Which tool" isn't in the registry, or a tool idea
           whose text mentions a tool that isn't. Both are the same
           signal: the site or the manifest is out of date. */
        val known = manifest.toolNames.map { it.lowercase() }.toSet()
        val unmapped = ideas
            .filter { idea ->
                when {
                    idea.isFeature -> idea.tool.isNotBlank() &&
                        idea.tool.lowercase() !in known
                    else -> false
                }
            }
            .groupingBy { it.tool.ifBlank { "(no tool named)" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { Bucket(it.key, it.value) }

        return Analytics(
            total = total,
            unread = unread,
            perWeek = perWeek(ideas, 12),
            byType = byType,
            byStatus = byStatus,
            byTool = byTool,
            topTools = topTools,
            unmapped = unmapped
        )
    }

    /**
     * Does this idea belong to `tool`?
     * A feature idea names its tool in the "Which tool" column. A tool
     * idea is the tool itself, so it counts when its text mentions it —
     * the same rule v1.6 used, kept so the numbers don't move.
     */
    private fun mentionsTool(idea: Idea, tool: String): Boolean =
        if (idea.isFeature) idea.tool.equals(tool, true)
        else idea.type.equals("Tool", true) && idea.idea.contains(tool, ignoreCase = true)

    /** Count ideas whose `When` falls in each of the last `weeks`
        weeks, oldest first. Labels are short ("3 Nov") so the axis
        fits on a phone. */
    private fun perWeek(ideas: List<Idea>, weeks: Int): List<Bucket> {
        if (ideas.isEmpty()) return List(weeks) { Bucket("", 0) }

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        /* Move to the start of this week. */
        cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))

        /* Build the week starts, oldest first. */
        val starts = (0 until weeks).map { offset ->
            val c = cal.clone() as Calendar
            c.add(Calendar.WEEK_OF_YEAR, -(weeks - 1 - offset))
            c.timeInMillis
        }

        val counts = IntArray(weeks)
        ideas.forEach { idea ->
            val t = Clock.parse(idea.whenIso) ?: return@forEach
            /* The newest bucket also catches anything after this week. */
            var index = weeks - 1
            for (i in starts.indices) {
                if (t.time < starts[i]) { index = i - 1; break }
            }
            if (index in 0 until weeks) counts[index]++
        }

        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.UK)
        return starts.mapIndexed { i, start ->
            Bucket(fmt.format(java.util.Date(start)), counts[i])
        }
    }
}