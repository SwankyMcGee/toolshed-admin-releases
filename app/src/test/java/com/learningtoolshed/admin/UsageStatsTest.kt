package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* Usage rolls raw page-view rows into the per-tool and per-page numbers
   the Usage tab draws. A change here moves real numbers, so it's worth
   pinning the interesting cases: a page belonging to no tool, and a
   resource page nobody has opened. */

class UsageStatsTest {

    private fun manifest() = SiteManifest(
        manifestVersion = 1,
        updated = "2026-11-16",
        minSupportedVersionCode = 0,
        features = emptyMap(),
        tools = listOf(
            ManifestTool("timer", "Work Cycle Timer", "timer", "/work-cycle-timer/timer.html", "", ""),
            ManifestTool("math", "Math Playground", "calculator", "/math-playground/", "", "")
        ),
        pages = listOf(
            ManifestPage("/", "Home", "", "home"),
            ManifestPage("/work-cycle-timer/timer.html", "Work Cycle Timer", "timer", "tool"),
            ManifestPage("/math-playground/", "Math Playground", "math", "tool"),
            ManifestPage("/quick-start.html", "Quick start", "timer", "resource"),
            ManifestPage("/glossary.html", "Glossary", "timer", "resource")
        )
    )

    private fun view(path: String, day: String, views: Int, seconds: Int) =
        PageView(path = path, day = day, views = views, seconds = seconds)

    @Test
    fun `empty rows give an empty usage, not a crash`() {
        val usage = UsageStats.of(emptyList(), manifest())
        assertEquals(0, usage.views)
        assertEquals("local", usage.source)
        assertFalse(usage.hasUnmapped)
    }

    @Test
    fun `a tool sums every page that belongs to it`() {
        val usage = UsageStats.of(
            listOf(
                view("/work-cycle-timer/timer.html", "2026-11-14", 10, 100),
                view("/quick-start.html", "2026-11-14", 5, 50)
            ),
            manifest()
        )
        val timer = usage.byTool.first { it.key == "timer" }
        assertEquals(15, timer.views)
        assertEquals(150, timer.seconds)
        assertEquals("Work Cycle Timer", timer.label)
    }

    @Test
    fun `a tool with no visits does not appear in the bars`() {
        val usage = UsageStats.of(
            listOf(view("/quick-start.html", "2026-11-14", 5, 50)),
            manifest()
        )
        assertEquals(listOf("timer"), usage.byTool.map { it.key })
    }

    @Test
    fun `a resource page nobody opened still gets a row`() {
        val usage = UsageStats.of(
            listOf(view("/quick-start.html", "2026-11-14", 5, 50)),
            manifest()
        )
        val glossary = usage.byResource.first { it.label == "Glossary" }
        assertEquals(0, glossary.views)
        assertEquals(2, usage.byResource.size)
    }

    @Test
    fun `a path the registry does not know is unmapped, not dropped`() {
        val usage = UsageStats.of(
            listOf(view("/reading-race.html", "2026-11-14", 7, 70)),
            manifest()
        )
        assertEquals(1, usage.unmapped.size)
        assertEquals("/reading-race.html", usage.unmapped.first().label)
        assertEquals(7, usage.unmapped.first().views)
        assertTrue(usage.hasUnmapped)
    }

    @Test
    fun `the home page is not unmapped`() {
        val usage = UsageStats.of(
            listOf(view("/", "2026-11-14", 3, 30)),
            manifest()
        )
        assertFalse(usage.hasUnmapped)
    }

    @Test
    fun `index and its directory are one page`() {
        val usage = UsageStats.of(
            listOf(
                view("/math-playground/", "2026-11-14", 2, 20),
                view("/math-playground/index.html", "2026-11-15", 3, 30)
            ),
            manifest()
        )
        val math = usage.byTool.first { it.key == "math" }
        assertEquals(5, math.views)
        assertFalse(usage.hasUnmapped)
    }

    @Test
    fun `the average is per view`() {
        val usage = UsageStats.of(
            listOf(view("/quick-start.html", "2026-11-14", 4, 200)),
            manifest()
        )
        val quickStart = usage.byResource.first { it.label == "Quick start" }
        assertEquals(50, quickStart.averageSeconds)
    }

    @Test
    fun `the daily series has one bucket per day and adds up`() {
        val usage = UsageStats.of(
            listOf(view("/quick-start.html", "2026-11-14", 5, 50)),
            manifest()
        )
        /* The series is a fixed window ending today, so the visit lands
           in whichever bucket covers it — the total is what matters. */
        assertEquals(30, usage.perDay.size)
        assertEquals(usage.views, usage.perDay.sumOf { it.count })
    }

    @Test
    fun `durations read in the largest sensible unit`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("38s", formatDuration(38))
        assertEquals("4m 12s", formatDuration(252))
        assertEquals("1h 04m", formatDuration(3840))
    }
}