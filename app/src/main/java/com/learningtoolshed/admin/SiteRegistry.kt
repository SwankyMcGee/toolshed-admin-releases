package com.learningtoolshed.admin

import android.content.Context
import org.json.JSONObject

/* ============================================================
   The site registry.

   Everything the app needs to know about *the site* — which tools
   exist, which page belongs to which tool, what a page is called —
   comes from site-manifest.json on the site (SwankyMcGee.github.io).
   The backend serves it at action=manifest; the app caches it and
   falls back to the copy bundled in the APK.

   Before this, the same list was written out four times: here, in
   Stats, in site.js and in tools.html. A new tool meant an app
   release. Now it means one edit to the manifest.
   ============================================================ */

const val MANIFEST_ASSET = "site-manifest.json"

data class ManifestTool(
    val id: String,
    val name: String,
    val icon: String,
    val path: String,
    val repoPath: String,
    val blurb: String
)

data class ManifestPage(
    val path: String,
    val title: String,
    val tool: String,
    val kind: String
) {
    val isResource: Boolean get() = kind == "resource"
}

class SiteManifest(
    val manifestVersion: Int,
    val updated: String,
    val minSupportedVersionCode: Int,
    val features: Map<String, Boolean>,
    val tools: List<ManifestTool>,
    val pages: List<ManifestPage>
) {

    /* ---------- tools ---------- */

    val toolNames: List<String> get() = tools.map { it.name }

    fun toolName(id: String): String =
        tools.firstOrNull { it.id == id }?.name ?: id

    /** The tool a path belongs to, by id, or "" for a site page. */
    fun toolForPath(path: String): String = byPath[canonical(path)]?.tool ?: ""

    fun titleForPath(path: String): String = byPath[canonical(path)]?.title ?: path

    fun isKnownPath(path: String): Boolean = canonical(path) in byPath

    /** Resource pages, in the order the manifest lists them. */
    val resourcePages: List<ManifestPage> get() = pages.filter { it.isResource }

    /* Lookups by path, keyed canonically: "/quick-start", "/quick-start/"
       and "/index.html" all resolve to the same page. Built once per
       manifest, so a hit is a map read rather than a scan. */
    private val byPath: Map<String, ManifestPage> by lazy {
        pages.associateBy { SiteRegistry.canonicalPath(it.path) }
    }

    private fun canonical(path: String) = SiteRegistry.canonicalPath(path)

    /* ---------- feature flags ----------
       A missing flag reads as on: an older backend that doesn't send
       `features` must not switch screens off. */
    fun flag(name: String): Boolean = features[name] ?: true

    companion object {
        /** Nothing known yet — what the state starts as, before the
            bundled copy is loaded. Every flag reads as on. */
        fun empty() = SiteManifest(0, "", 0, emptyMap(), emptyList(), emptyList())
    }
}

object SiteRegistry {

    /** The copy shipped inside the APK — always available, never stale,
        always enough to draw the screens. */
    fun bundled(context: Context): SiteManifest =
        parse(context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() })
            ?: empty()

    /**
     * Reads a manifest. Returns null when the text isn't one — the
     * caller keeps whatever it already has rather than showing an empty
     * app because a JSON file moved.
     */
    fun parse(json: String): SiteManifest? {
        return try {
            val root = JSONObject(json)
            val tools = root.optJSONArray("tools").mapObjects { o ->
                ManifestTool(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    icon = o.optString("icon"),
                    path = o.optString("path"),
                    repoPath = o.optString("repoPath"),
                    blurb = o.optString("blurb")
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }

            val pages = root.optJSONArray("pages").mapObjects { o ->
                ManifestPage(
                    path = o.optString("path"),
                    title = o.optString("title"),
                    tool = o.optString("tool"),
                    kind = o.optString("kind")
                )
            }.filter { it.path.isNotBlank() }

            if (tools.isEmpty() && pages.isEmpty()) return null

            val flags = mutableMapOf<String, Boolean>()
            val flagJson = root.optJSONObject("features")
            flagJson?.keys()?.forEach { key -> flags[key] = flagJson.optBoolean(key, true) }

            SiteManifest(
                manifestVersion = root.optInt("manifestVersion", 1),
                updated = root.optString("updated"),
                minSupportedVersionCode = root.optInt("minSupportedVersionCode", 0),
                features = flags,
                tools = tools,
                pages = pages
            )
        } catch (err: Exception) {
            null
        }
    }

    private fun empty() = SiteManifest(0, "", 0, emptyMap(), emptyList(), emptyList())

    private fun <T> org.json.JSONArray?.mapObjects(body: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>()
        if (this == null) return out
        for (i in 0 until length()) out.add(body(getJSONObject(i)))
        return out
    }

    /**
     * "/index.html" and "/" are the same page; "/math-playground" and
     * "/math-playground/" are the same page. Mirrors canonicalPath_() in
     * ideas-engine/Code.gs — change one, change both.
     *
     * Both forms collapse to the slash-free one (except the site root,
     * which stays "/"), because that's the form the manifest uses.
     */
    fun canonicalPath(path: String): String {
        var p = path.trim()
        if (p.startsWith("http://") || p.startsWith("https://")) {
            p = "/" + p.substringAfter("://").substringAfter("/")
        }
        if (p.endsWith("/index.html")) p = p.removeSuffix("index.html")
        if (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
        return p.ifBlank { "/" }
    }

    /** The tool id behind a path, canonicalised first. */
    fun toolForPath(manifest: SiteManifest, path: String): String =
        manifest.toolForPath(canonicalPath(path))

    fun titleForPath(manifest: SiteManifest, path: String): String =
        manifest.titleForPath(canonicalPath(path))
}