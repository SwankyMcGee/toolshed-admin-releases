package com.learningtoolshed.admin

import java.text.DateFormat
import java.util.Date

/* Dates from the sheet arrive as ISO-8601 UTC. Show them the way the
   sheet's "When" column reads: day, month, time — no year unless it
   isn't this one. */
object Clock {

    fun format(epochMillis: Long): String {
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        return fmt.format(Date(epochMillis))
    }

    fun formatIdea(iso: String): String {
        val date = parse(iso) ?: return iso
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { time = date }

        val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)

        return if (sameDay) {
            "Today, " + DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        } else {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date) +
                ", " + DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        }
    }

    private fun parse(iso: String): Date? {
        if (iso.isBlank()) return null
        return try {
            /* Trim a trailing Z and any fractional seconds; the sheet
               writes "2026-11-15T09:04:11.000Z" in some locales and
               "2026-11-15T09:04:11" in others. */
            val cleaned = iso
                .replace("Z", "")
                .let { if (it.contains('.')) it.substringBefore('.') else it }
            iso_.parse(cleaned)
        } catch (err: Exception) {
            null
        }
    }

    private val iso_: DateFormat
        get() = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
}