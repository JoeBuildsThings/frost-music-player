package com.joebuildsthings.frost.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** The contract between PlaybackService and its controllers (app UI, widgets). */
object FrostSession {
    const val ACTION_SET_SLEEP = "com.joebuildsthings.frost.SET_SLEEP"

    /** Minutes until playback pauses. 0 turns the timer off, [SLEEP_END_OF_TRACK] waits for the song to end. */
    const val ARG_MINUTES = "minutes"
    const val SLEEP_END_OF_TRACK = -1

    /** Session extras published by the service so every controller sees the same timer. */
    const val EXTRA_SLEEP_END_AT = "sleep_end_at"
    const val EXTRA_SLEEP_MINUTES = "sleep_minutes"

    /** Stored on each MediaItem's metadata so the service can find album art without the library. */
    const val EXTRA_ALBUM_ID = "album_id"

    val SET_SLEEP = SessionCommand(ACTION_SET_SLEEP, Bundle.EMPTY)
}
