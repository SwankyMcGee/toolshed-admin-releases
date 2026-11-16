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
}