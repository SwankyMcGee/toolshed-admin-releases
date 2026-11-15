package com.learningtoolshed.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/* ============================================================
   Update check against a public GitHub repo's releases.

   The repo (SwankyMcGee/toolshed-admin-releases) holds one
   release per app version, tagged "v1.1", "v1.2", etc. Each
   release's body contains a line like:

       versionCode: 2
       versionName: 1.1

   The app reads the latest release, parses the versionCode, and
   compares it to its own. If the remote is higher, it shows an
   update banner.

   No token, no auth: the repo is public, and the APK is just a
   download URL. The repo name is not linked from the site, so
   nobody stumbles on it.
   ============================================================ */

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

object UpdateChecker {

    private const val REPO = "SwankyMcGee/toolshed-admin-releases"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    fun check(currentVersionCode: Int): UpdateInfo? {
        return try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ToolshedAdmin/$currentVersionCode")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(text)
            val body = json.optString("body", "")
            val tagName = json.optString("tag_name", "")

            /* Parse "versionCode: 2" from the release body. Fall back
               to the tag name ("v1.1" → 1, "v1.2" → 2) if the body
               doesn't have it. */
            val remoteCode = Regex("""versionCode:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: parseCodeFromTag(tagName)
                ?: return null

            if (remoteCode <= currentVersionCode) return null

            /* The first APK asset is the one to download. */
            val assets = json.optJSONArray("assets")
            val downloadUrl = if (assets != null && assets.length() > 0) {
                assets.getJSONObject(0).optString("browser_download_url", "")
            } else {
                ""
            }
            if (downloadUrl.isBlank()) return null

            val remoteName = Regex("""versionName:\s*(\S+)""").find(body)?.groupValues?.get(1)
                ?: tagName.removePrefix("v")

            UpdateInfo(
                versionCode = remoteCode,
                versionName = remoteName,
                downloadUrl = downloadUrl
            )
        } catch (err: Exception) {
            null   // offline, rate-limited, no release yet — stay quiet
        }
    }

    /** "v1.1" -> 1, "v1.2" -> 2, "v2.0" -> 2 (major). */
    private fun parseCodeFromTag(tag: String): Int? {
        val m = Regex("""v?(\d+)\.(\d+)""").find(tag) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        return major * 1000 + minor
    }
}