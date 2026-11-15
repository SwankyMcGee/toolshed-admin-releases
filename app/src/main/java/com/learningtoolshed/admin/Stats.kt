package com.learningtoolshed.admin

import java.util.Calendar

/* ============================================================
   Analytics, computed on-device from the ideas already in memory.

   Everything here is pure: give it a list of ideas, get back
   numbers and series. The Compose screen just draws them.

   v1 answers "what's in the inbox now":
     - ideas per week (last 12 weeks)
     - tool vs feature split
     - status breakdown
     - top tools by idea count
     - unread count

   It cannot answer "how fast do we close things" — that needs
   status history (see expanding_the_project.md, Option C).
   ============================================================ */

data class Bucket(val label: String, val count: Int)

data class Analytics(
    val total: Int,
    val unread: Int,
    val perWeek: List<Bucket>,        // oldest first, last 12 weeks
    val byType: List<Bucket>,         // Tool / Feature
    val byStatus: List<Bucket>,       // new / reading / planned / done / shelved
    val topTools: List<Bucket>        // by feature ideas, biggest first
) {
    val maxWeek: Int get() = perWeek.maxOfOrNull { it.count } ?: 1
    val maxTool: Int get() = topTools.maxOfOrNull { it.count } ?: 1
    val maxType: Int get() = byType.maxOfOrNull { it.count } ?: 1
    val maxStatus: Int get() = byStatus.maxOfOrNull { it.count } ?: 1
}

object Stats {

    fun of(ideas: List<Idea>): Analytics {
        val total = ideas.size
        val unread = ideas.count { !it.read }

        val byType = listOf(
            Bucket("Tool", ideas.count { it.type.equals("Tool", true) }),
            Bucket("Feature", ideas.count { it.type.equals("Feature", true) })
        )

        val byStatus = STATUSES.map { status ->
            Bucket(status.replaceFirstChar { it.uppercase() }, ideas.count { it.status == status })
        }

        val topTools = ideas
            .filter { it.isFeature && it.tool.isNotBlank() }
            .groupingBy { it.tool }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { Bucket(it.key, it.value) }

        return Analytics(
            total = total,
            unread = unread,
            perWeek = perWeek(ideas, 12),
            byType = byType,
            byStatus = byStatus,
            topTools = topTools
        )
    }

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
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.UK)
        val labels = Array(weeks) { fmt.format(java.util.Date(starts[it])) }

        ideas.forEach { idea ->
            val t = parseWhen(idea.whenIso) ?: return@forEach
            /* Find which bucket it falls in: the last start <= t. */
            for (i in starts.indices.reversed()) {
                if (t >= starts[i]) {
                    counts[i]++
                    break
                }
            }
        }

        return (0 until weeks).map { Bucket(labels[it], counts[it]) }
    }

    private fun parseWhen(iso: String): Long? {
        if (iso.isBlank()) return null
        return try {
            val cleaned = iso.replace("Z", "").let {
                if (it.contains('.')) it.substringBefore('.') else it
            }
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(cleaned)?.time
        } catch (err: Exception) {
            null
        }
    }
}