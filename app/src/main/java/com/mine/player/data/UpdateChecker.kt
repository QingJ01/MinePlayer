package com.mine.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases for a newer version of the app. */
object UpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/QingJ01/MinePlayer/releases/latest"
    const val RELEASES_PAGE = "https://github.com/QingJ01/MinePlayer/releases"

    data class Release(val tag: String, val name: String, val url: String, val notes: String)

    sealed interface Result {
        /** A published release was found. */
        data class Found(val release: Release) : Result
        /** The repo has no published releases yet (HTTP 404). */
        data object NoReleases : Result
        /** Network / parse error. */
        data object Failed : Result
    }

    // Release info changes at most once per release: cache it so re-entering the About page
    // doesn't burn network round-trips / the 60-req-per-hour unauthenticated GitHub limit.
    private const val CACHE_TTL_MS = 60 * 60 * 1000L
    @Volatile private var cached: Result? = null
    @Volatile private var cachedAtMs = 0L

    suspend fun check(force: Boolean = false): Result {
        val hit = cached
        if (!force && hit != null &&
            android.os.SystemClock.elapsedRealtime() - cachedAtMs < CACHE_TTL_MS
        ) {
            return hit
        }
        val result = fetch()
        if (result != Result.Failed) {
            cached = result
            cachedAtMs = android.os.SystemClock.elapsedRealtime()
        }
        return result
    }

    private suspend fun fetch(): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MinePlayer-Android")
            }
            when (conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    Result.Found(
                        Release(
                            tag = json.optString("tag_name"),
                            name = json.optString("name"),
                            url = json.optString("html_url", RELEASES_PAGE),
                            notes = json.optString("body"),
                        ),
                    )
                }
                404 -> Result.NoReleases
                else -> Result.Failed
            }
        } catch (_: Throwable) {
            Result.Failed
        } finally {
            conn?.disconnect()
        }
    }

    /** True when [tag] (e.g. "v0.2.0") represents a version newer than [current] (e.g. "0.1.0"). */
    fun isNewer(tag: String, current: String): Boolean {
        val (a, aPre) = parse(tag)
        val (b, bPre) = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // Same core version: a stable release supersedes a pre-release (0.2.0 > 0.2.0-beta2);
        // anything else (equal, or both pre-releases) is not offered as an update.
        return aPre.isEmpty() && bPre.isNotEmpty()
    }

    /** Split "v1.2.3-beta2+build" into numeric core parts [1,2,3] and pre-release suffix "beta2". */
    private fun parse(v: String): Pair<List<Int>, String> {
        val cleaned = v.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val core = cleaned.substringBefore('-')
        val pre = cleaned.substringAfter('-', "")
        return core.split('.', ' ').mapNotNull { it.toIntOrNull() } to pre
    }
}
