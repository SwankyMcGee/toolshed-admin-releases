package com.learningtoolshed.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* The site registry is what stops the app needing a release for every
   new tool, so its lookups are worth pinning down — a wrong path here
   is a page that silently lands in "not in the registry" instead of
   under its tool. */

class SiteRegistryTest {

    private fun manifest() = SiteManifest(
        manifestVersion = 1,
        updated = "2026-11-16",
        minSupportedVersionCode = 0,
        features = mapOf("usage" to true, "analytics" to false),
        tools = listOf(
            ManifestTool("timer", "Work Cycle Timer", "timer", "/work-cycle-timer/timer.html", "/work-cycle-timer/", ""),
            ManifestTool("math", "Math Playground", "calculator", "/math-playground/", "/math-playground/", "")
        ),
        pages = listOf(
            ManifestPage("/", "Home", "", "home"),
            ManifestPage("/quick-start.html", "Quick start", "timer", "resource"),
            ManifestPage("/work-cycle-timer/timer.html", "Work Cycle Timer", "timer", "tool"),
            ManifestPage("/math-playground/counters.html", "Counters", "math", "tool")
        )
    )

    @Test
    fun `canonical paths collapse the duplicates`() {
        assertEquals("/", SiteRegistry.canonicalPath("/"))
        assertEquals("/", SiteRegistry.canonicalPath(""))
        assertEquals("/", SiteRegistry.canonicalPath("/index.html"))
        assertEquals("/quick-start", SiteRegistry.canonicalPath("/quick-start"))
        assertEquals("/quick-start", SiteRegistry.canonicalPath("/quick-start/"))
        assertEquals("/math-playground", SiteRegistry.canonicalPath("/math-playground"))
        assertEquals("/math-playground", SiteRegistry.canonicalPath("/math-playground/"))
    }

    @Test
    fun `canonical paths accept a full url`() {
        /* A page's own name is kept — "/counters.html" is the page. Only
           the "/index.html" form collapses, because a directory and its
           index are the same page. */
        assertEquals(
            "/math-playground/counters.html",
            SiteRegistry.canonicalPath("https://swankymcgee.github.io/math-playground/counters.html")
        )
        assertEquals(
            "/math-playground",
            SiteRegistry.canonicalPath("https://swankymcgee.github.io/math-playground/")
        )
    }

    @Test
    fun `a path resolves to its tool`() {
        assertEquals("timer", SiteRegistry.toolForPath(manifest(), "/quick-start.html"))
        assertEquals("timer", SiteRegistry.toolForPath(manifest(), "/work-cycle-timer/timer.html"))
        assertEquals("math", SiteRegistry.toolForPath(manifest(), "/math-playground/counters.html"))
    }

    @Test
    fun `a site page has no tool`() {
        assertEquals("", SiteRegistry.toolForPath(manifest(), "/"))
        assertEquals("", SiteRegistry.toolForPath(manifest(), "/somewhere/else"))
    }

    @Test
    fun `a page resolves to its title, or to its path`() {
        assertEquals("Quick start", SiteRegistry.titleForPath(manifest(), "/quick-start.html"))
        assertEquals("/nope", SiteRegistry.titleForPath(manifest(), "/nope"))
    }

    @Test
    fun `known paths are known`() {
        assertTrue(manifest().isKnownPath("/quick-start.html"))
        assertFalse(manifest().isKnownPath("/does-not-exist"))
    }

    @Test
    fun `resource pages are the resource kind`() {
        assertEquals(listOf("Quick start"), manifest().resourcePages.map { it.title })
    }

    @Test
    fun `a missing flag reads as on`() {
        assertTrue(manifest().flag("usage"))
        assertTrue(manifest().flag("replies"))     // not in the manifest at all
        assertFalse(manifest().flag("analytics"))
    }

    @Test
    fun `tool names come from the manifest in order`() {
        assertEquals(
            listOf("Work Cycle Timer", "Math Playground"),
            manifest().toolNames
        )
    }

    @Test
    fun `tool name falls back to the id`() {
        assertEquals("Work Cycle Timer", manifest().toolName("timer"))
        assertEquals("unknown", manifest().toolName("unknown"))
    }
}