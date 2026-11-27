package com.joebuildsthings.frost.update

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Reads published releases from the GitHub Releases API. The only network call Frost makes. */
object ReleaseFeed {

    private const val TIMEOUT_MS = 10_000

    /** Blocking. Call off the main thread. Sends no identifiers, only standard HTTP headers. */
    fun fetch(repo: String): String {
        val conn = URL("https://api.github.com/repos/$repo/releases?per_page=20").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Frost-Music-Player-Updater")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Release feed returned HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Keeps published, non pre-release releases with a valid version and an APK asset. The APK
     * link must point at this repository's own release downloads, so a tampered feed cannot
     * send users to some other site. Android itself refuses an update signed with another key.
     */
    fun parse(json: String, repo: String): List<Release> {
        val out = ArrayList<Release>()
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return out
        }
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val tag = r.optString("tag_name")
            val version = AppVersion.parse(tag) ?: continue
            val assets = r.optJSONArray("assets") ?: continue
            var apk: String? = null
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                val url = a.optString("browser_download_url")
                if (a.optString("name").endsWith(".apk", ignoreCase = true) && isTrustedDownload(url, repo)) {
                    apk = url
                    break
                }
            }
            out.add(
                Release(
                    tag = tag,
                    version = version,
                    apkUrl = apk ?: continue,
                    pageUrl = r.optString("html_url").takeIf { isTrustedPage(it, repo) }
                        ?: "https://github.com/$repo/releases",
                    notes = r.optString("body").trim()
                )
            )
        }
        return out
    }

    internal fun isTrustedDownload(url: String, repo: String): Boolean =
        url.startsWith("https://github.com/$repo/releases/download/")

    private fun isTrustedPage(url: String, repo: String): Boolean =
        url.startsWith("https://github.com/$repo/releases")
}
