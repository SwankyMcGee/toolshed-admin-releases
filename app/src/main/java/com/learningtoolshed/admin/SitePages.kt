package com.learningtoolshed.admin

/* ============================================================
   The site's pages, grouped the way the Tools hub and the
   Resources hub group them.

   The usage data is per path, so this registry is what turns a
   list of "/quick-start.html" rows into "Work Cycle Timer:
   412 views". It is kept in sync with the site by hand:
     - site pages live in SwankyMcGee.github.io (README.md)
     - tools live in their own repos, served at
       /work-cycle-timer/ and /math-playground/

   When a tool or resource page is added on the site, add it here
   too or its views will show up under "Other".
   ============================================================ */

/** A page on the site, tagged with what it belongs to. */
data class SitePage(
    val path: String,
    val title: String,
    val tool: String,      // "" = the Toolshed site itself
    val kind: Kind
) {
    enum class Kind { Home, Tool, Resource, Ideas }
}

val SITE_PAGES: List<SitePage> = listOf(
    /* The Toolshed site itself. */
    SitePage("/", "Home", "", SitePage.Kind.Home),
    SitePage("/index.html", "Home", "", SitePage.Kind.Home),
    SitePage("/tools.html", "Tools hub", "", SitePage.Kind.Home),
    SitePage("/resources.html", "Resources hub", "", SitePage.Kind.Home),
    SitePage("/ideas.html", "The Ideas Engine", "", SitePage.Kind.Ideas),

    /* Work Cycle Timer — app + its staff resources. */
    SitePage("/work-cycle-timer/", "Work Cycle Timer", "Work Cycle Timer", SitePage.Kind.Tool),
    SitePage("/work-cycle-timer/timer.html", "Work Cycle Timer", "Work Cycle Timer", SitePage.Kind.Tool),
    SitePage("/quick-start.html", "Quick start", "Work Cycle Timer", SitePage.Kind.Resource),
    SitePage("/user-guide.html", "User guide", "Work Cycle Timer", SitePage.Kind.Resource),
    SitePage("/settings-ref.html", "Settings reference", "Work Cycle Timer", SitePage.Kind.Resource),
    SitePage("/troubleshooting.html", "Troubleshooting", "Work Cycle Timer", SitePage.Kind.Resource),
    SitePage("/scenarios.html", "Scenarios", "Work Cycle Timer", SitePage.Kind.Resource),
    SitePage("/glossary.html", "Glossary", "Work Cycle Timer", SitePage.Kind.Resource),

    /* Math Playground — four games under one tool. */
    SitePage("/math-playground/", "Math Playground", "Math Playground", SitePage.Kind.Tool),
    SitePage("/math-playground/index.html", "Math Playground", "Math Playground", SitePage.Kind.Tool),
    SitePage("/math-playground/counters.html", "Counters", "Math Playground", SitePage.Kind.Tool),
    SitePage("/math-playground/number-line.html", "Number Line", "Math Playground", SitePage.Kind.Tool),
    SitePage("/math-playground/facts.html", "Facts", "Math Playground", SitePage.Kind.Tool),
    SitePage("/math-playground/story.html", "Story Problems", "Math Playground", SitePage.Kind.Tool)
)

/** The tools on the Tools hub, in the order the site lists them. */
val TOOL_NAMES: List<String> = listOf(
    "Work Cycle Timer",
    "Math Playground"
)

/** Every resource page, in the order the Resources hub lists them. */
val RESOURCE_PAGES: List<SitePage> = SITE_PAGES
    .filter { it.kind == SitePage.Kind.Resource }
    .sortedBy { it.title }

/** The tool a path belongs to, or "" when it's a site page or unknown. */
fun toolForPath(path: String): String =
    SITE_PAGES.firstOrNull { it.path == path }?.tool ?: ""

/** A friendly name for a path, falling back to the path itself. */
fun titleForPath(path: String): String =
    SITE_PAGES.firstOrNull { it.path == path }?.title ?: path

/** Group a path's raw name onto the page it really is. "/index.html"
    and "/" are the same page; "/math-playground" and
    "/math-playground/" are the same page. */
fun canonicalPath(path: String): String {
    var p = path.trim()
    if (p.startsWith("http://") || p.startsWith("https://")) {
        p = p.substringAfter("://").substringAfter("/")
        p = "/" + p
    }
    /* Drop a trailing "index.html" — "/foo/index.html" is "/foo/". */
    if (p.endsWith("/index.html")) p = p.removeSuffix("index.html")
    if (p.length > 1 && p.endsWith("/")) p = p.dropLast(1) + "/"
    return when (p) {
        "", "/" -> "/"
        else -> p
    }
}