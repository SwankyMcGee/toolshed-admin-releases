package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* Stats is pure: ideas in, numbers out. These tests are the ones that
   would catch a registry change quietly moving the charts. */

class StatsTest {

    private fun manifest() = SiteManifest(
        manifestVersion = 1,
        updated = "2026-11-16",
        minSupportedVersionCode = 0,
        features = emptyMap(),
        tools = listOf(
            ManifestTool("timer", "Work Cycle Timer", "timer", "", "", ""),
            ManifestTool("math", "Math Playground", "calculator", "", "", "")
        ),
        pages = emptyList()
    )

    private fun idea(
        id: Int,
        type: String = "Feature",
        tool: String = "Work Cycle Timer",
        read: Boolean = false,
        status: String = "new",
        body: String = "an idea"
    ) = Idea(
        id = id, type = type, whenIso = "2026-11-15T09:00:00",
        idea = body, name = "", tool = tool, read = read, status = status, note = ""
    )

    @Test
    fun `totals count everything`() {
        val stats = Stats.of(
            listOf(idea(1), idea(2, read = true), idea(3, type = "Tool", tool = "")),
            manifest()
        )
        assertEquals(3, stats.total)
        /* Two of the three are unread: the first, and the tool idea. */
        assertEquals(2, stats.unread)
    }

    @Test
    fun `the type split is tool versus feature`() {
        val stats = Stats.of(
            listOf(idea(1, type = "Feature"), idea(2, type = "Tool", tool = "")),
            manifest()
        )
        assertEquals(1, stats.byType[0].count)   // Tool
        assertEquals(1, stats.byType[1].count)   // Feature
    }

    @Test
    fun `every status has a bucket`() {
        val stats = Stats.of(listOf(idea(1, status = "done")), manifest())
        assertEquals(STATUSES.size, stats.byStatus.size)
        assertEquals(1, stats.byStatus.first { it.label == "Done" }.count)
    }

    @Test
    fun `each tool in the registry gets a row, even at zero`() {
        val stats = Stats.of(
            listOf(idea(1, tool = "Work Cycle Timer"), idea(2, tool = "Work Cycle Timer")),
            manifest()
        )
        assertEquals(2, stats.byTool.size)
        assertEquals(
            listOf("Work Cycle Timer", "Math Playground"),
            stats.byTool.map { it.label }
        )
        assertEquals(2, stats.byTool[0].count)
        assertEquals(0, stats.byTool[1].count)
    }

    @Test
    fun `a tool idea counts for the tool it names in its text`() {
        val stats = Stats.of(
            listOf(idea(1, type = "Tool", tool = "", body = "I'd like a Math Playground for fractions")),
            manifest()
        )
        assertEquals(1, stats.byTool.first { it.label == "Math Playground" }.count)
        assertEquals(0, stats.byTool.first { it.label == "Work Cycle Timer" }.count)
    }

    @Test
    fun `a feature idea for an unknown tool is unmapped, not dropped`() {
        val stats = Stats.of(
            listOf(idea(1, tool = "Reading Race"), idea(2, tool = "Reading Race")),
            manifest()
        )
        assertEquals(listOf("Reading Race" to 2), stats.unmapped.map { it.label to it.count })
        assertTrue(stats.hasUnmapped)
    }

    @Test
    fun `an idea for a known tool is not unmapped`() {
        val stats = Stats.of(listOf(idea(1, tool = "Math Playground")), manifest())
        assertFalse(stats.hasUnmapped)
    }

    @Test
    fun `the week series always has twelve buckets`() {
        val stats = Stats.of(emptyList(), manifest())
        assertEquals(12, stats.perWeek.size)
    }

    @Test
    fun `the busiest tools lead`() {
        val stats = Stats.of(
            listOf(
                idea(1, tool = "Work Cycle Timer"),
                idea(2, tool = "Math Playground"),
                idea(3, tool = "Math Playground"),
                idea(4, tool = "Math Playground")
            ),
            manifest()
        )
        assertEquals("Math Playground", stats.topTools.first().label)
        assertEquals(3, stats.topTools.first().count)
    }
}