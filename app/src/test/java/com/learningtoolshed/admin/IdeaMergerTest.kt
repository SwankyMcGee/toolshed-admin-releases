package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/* The delta read is only half the mechanism: these are the other half.
   A merge that drops a row, or that stops sorting newest-first, would
   show up as an idea missing from the inbox. */

class IdeaMergerTest {

    private fun idea(id: Int, whenIso: String, read: Boolean = false, status: String = "new") = Idea(
        id = id, type = "Feature", whenIso = whenIso, idea = "idea $id",
        name = "", tool = "Work Cycle Timer", read = read, status = status, note = ""
    )

    @Test
    fun `an empty delta leaves the cached list alone`() {
        val cached = listOf(idea(2, "2026-11-15T09:00:00"), idea(1, "2026-11-14T09:00:00"))
        assertEquals(cached, IdeaMerger.merge(cached, emptyList()))
    }

    @Test
    fun `changed rows replace their cached copies`() {
        val cached = listOf(idea(2, "2026-11-15T09:00:00", read = false))
        val delta = listOf(idea(2, "2026-11-15T09:00:00", read = true))

        val merged = IdeaMerger.merge(cached, delta)

        assertEquals(1, merged.size)
        assertEquals(true, merged.first().read)
    }

    @Test
    fun `new rows are added`() {
        val merged = IdeaMerger.merge(
            listOf(idea(2, "2026-11-15T09:00:00")),
            listOf(idea(3, "2026-11-16T09:00:00"))
        )
        assertEquals(listOf(3, 2), merged.map { it.id })
    }

    @Test
    fun `the result is always newest first`() {
        val merged = IdeaMerger.merge(
            listOf(idea(5, "2026-11-10T09:00:00"), idea(4, "2026-11-12T09:00:00")),
            listOf(idea(6, "2026-11-16T09:00:00"), idea(3, "2026-11-09T09:00:00"))
        )
        assertEquals(listOf(6, 4, 5, 3), merged.map { it.id })
    }

    @Test
    fun `rows in the same second order by id, newest first`() {
        val merged = IdeaMerger.merge(
            emptyList(),
            listOf(idea(7, "2026-11-16T09:00:00"), idea(8, "2026-11-16T09:00:00"))
        )
        assertEquals(listOf(8, 7), merged.map { it.id })
    }
}