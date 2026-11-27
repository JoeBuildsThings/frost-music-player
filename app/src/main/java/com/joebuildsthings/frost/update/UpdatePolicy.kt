package com.joebuildsthings.frost.update

/** A published release that has an installable APK attached. */
data class Release(
    val tag: String,
    val version: AppVersion,
    val apkUrl: String,
    val pageUrl: String,
    val notes: String
)

sealed interface UpdateDecision {
    data object None : UpdateDecision

    /** A newer version exists. The user may update now or skip this version. */
    data class Optional(val latest: Release, val behind: Int) : UpdateDecision

    /** The user is [UpdatePolicy.FORCE_AFTER] or more releases behind and has to update. */
    data class Required(val latest: Release, val behind: Int) : UpdateDecision
}

object UpdatePolicy {
    /** Skipping is allowed for up to two missed releases; the third one forces the update. */
    const val FORCE_AFTER = 3

    fun decide(installed: AppVersion?, releases: List<Release>, skippedTag: String?): UpdateDecision {
        if (installed == null) return UpdateDecision.None
        val newer = releases
            .filter { it.version > installed }
            .distinctBy { it.version }
            .sortedByDescending { it.version }
        val latest = newer.firstOrNull() ?: return UpdateDecision.None
        return when {
            newer.size >= FORCE_AFTER -> UpdateDecision.Required(latest, newer.size)
            latest.tag == skippedTag -> UpdateDecision.None
            else -> UpdateDecision.Optional(latest, newer.size)
        }
    }
}
