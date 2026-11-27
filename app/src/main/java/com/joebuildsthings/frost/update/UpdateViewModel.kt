package com.joebuildsthings.frost.update

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joebuildsthings.frost.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FrostUpdate"
private const val PREFS = "frost_update"
private const val KEY_FEED = "feed"
private const val KEY_CHECKED_AT = "checked_at"
private const val KEY_SKIPPED = "skipped_tag"
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

/**
 * Checks GitHub Releases at most every six hours. The last feed is cached, so a required
 * update stays required while offline. Dev builds from CI (versionName with "-dev") never nag.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val repo = BuildConfig.UPDATE_REPO
    private val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
        ?.takeIf { it.preRelease?.startsWith("dev") != true }

    var decision by mutableStateOf<UpdateDecision>(UpdateDecision.None)
        private set

    /** "Later" hides an optional prompt until the next launch without skipping the version. */
    private var dismissedThisSession = false

    init {
        prefs.getString(KEY_FEED, null)?.let { decision = decideFrom(it) }
        refreshIfStale()
    }

    private fun decideFrom(feed: String): UpdateDecision {
        val d = UpdatePolicy.decide(installed, ReleaseFeed.parse(feed, repo), prefs.getString(KEY_SKIPPED, null))
        return if (d is UpdateDecision.Optional && dismissedThisSession) UpdateDecision.None else d
    }

    private fun refreshIfStale() {
        if (installed == null) return
        val last = prefs.getLong(KEY_CHECKED_AT, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        viewModelScope.launch {
            try {
                val feed = withContext(Dispatchers.IO) { ReleaseFeed.fetch(repo) }
                prefs.edit()
                    .putString(KEY_FEED, feed)
                    .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .apply()
                decision = decideFrom(feed)
            } catch (e: Exception) {
                // Offline or rate limited: keep the cached decision and try again next launch.
                Log.i(TAG, "Update check skipped: ${e.message}")
            }
        }
    }

    fun skip() {
        val d = decision as? UpdateDecision.Optional ?: return
        prefs.edit().putString(KEY_SKIPPED, d.latest.tag).apply()
        decision = UpdateDecision.None
    }

    fun later() {
        if (decision !is UpdateDecision.Optional) return
        dismissedThisSession = true
        decision = UpdateDecision.None
    }

    /** Opens the APK download in the browser; Android's installer takes it from there. */
    fun download(context: Context, release: Release) {
        fun open(url: String) = context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            open(release.apkUrl)
        } catch (e: ActivityNotFoundException) {
            try {
                open(release.pageUrl)
            } catch (e2: ActivityNotFoundException) {
                Log.w(TAG, "No app can open the download link", e2)
            }
        }
    }
}
