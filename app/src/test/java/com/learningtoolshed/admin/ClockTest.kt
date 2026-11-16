package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/* The sheet writes "2026-11-15T09:04:11.000Z" in some locales and
   "2026-11-15T09:04:11" in others; a date that doesn't parse used to
   turn into the raw string on screen. */

class ClockTest {

    private fun yearOf(date: java.util.Date): Int =
        java.util.Calendar.getInstance().apply { time = date }.get(java.util.Calendar.YEAR)

    @Test
    fun `it reads an ISO timestamp with a zone`() {
        val parsed = Clock.parse("2026-11-15T09:04:11.000Z")
        assertEquals(2026, yearOf(parsed!!))
    }

    @Test
    fun `it reads an ISO timestamp without a zone`() {
        val parsed = Clock.parse("2026-11-15T09:04:11")
        assertEquals(2026, yearOf(parsed!!))
    }

    @Test
    fun `it refuses nonsense`() {
        assertNull(Clock.parse(""))
        assertNull(Clock.parse("not a date"))
    }

    @Test
    fun `an unparseable idea timestamp reads back as itself`() {
        assertEquals("whenever", Clock.formatIdea("whenever"))
    }

    /* ---------- how long ago ---------- */

    private fun iso(millisAgo: Long): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        return stamp.format(java.util.Date(System.currentTimeMillis() - millisAgo))
    }

    @Test
    fun `a fresh idea reads as just now`() {
        assertEquals("just now", Clock.relative(iso(30_000L)))
    }

    @Test
    fun `an idea counts minutes`() {
        assertEquals("12m ago", Clock.relative(iso(12 * 60_000L + 5_000L)))
    }

    @Test
    fun `an idea counts hours`() {
        assertEquals("3h ago", Clock.relative(iso(3 * 3_600_000L + 60_000L)))
    }

    @Test
    fun `yesterday is a word, not a number`() {
        assertEquals("yesterday", Clock.relative(iso(30 * 3_600_000L)))
    }

    @Test
    fun `anything older falls back to the full date`() {
        val old = "2024-03-02T11:05:00"
        assertEquals(Clock.formatIdea(old), Clock.relative(old))
    }

    @Test
    fun `a timestamp that cannot be read stays itself`() {
        assertEquals("whenever", Clock.relative("whenever"))
    }
}