package com.joebuildsthings.frost.pro

import com.joebuildsthings.frost.BuildConfig

/** Features planned for Frost Pro. Nothing is locked today; this is where gating will hook in. */
enum class ProFeature {
    GAPLESS_CROSSFADE,
    REPLAY_GAIN,
    LYRICS,
    THEMES,
    WIDGET_STYLES,
    TAG_EDITOR,
    BACKUP_RESTORE,
    ANDROID_AUTO
}

/**
 * Off switch for Pro. BuildConfig.PRO_GATING is false in every build (Gradle property
 * frost.proGating), so isAvailable() is always true and no UI mentions Pro.
 */
object Pro {
    val gatingEnabled: Boolean = BuildConfig.PRO_GATING

    /** Set by the purchase flow once it exists. */
    @Volatile
    var unlocked: Boolean = false
        internal set

    fun isAvailable(feature: ProFeature): Boolean = !gatingEnabled || unlocked
}
