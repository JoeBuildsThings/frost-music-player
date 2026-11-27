package com.joebuildsthings.frost.playback

object PlaybackPrefs {
    const val FILE = "frost_prefs"
    const val KEEP_PLAYING = "keep_playing"

    /**
     * Off by default: with it off ExoPlayer owns audio focus, so calls, alarms, navigation and
     * other players pause or duck Frost the way users expect.
     */
    const val KEEP_PLAYING_DEFAULT = false
    const val EQ_ON = "eq_on"
    const val EQ_LEVELS = "eq_levels"
    const val EQ_INFO = "eq_info"
    const val BASS = "bass"
    const val DARK_MODE = "dark_mode"
    const val W_TITLE = "widget_title"
    const val W_ARTIST = "widget_artist"
    const val W_ALBUM = "widget_album"
    const val W_ALBUM_ID = "widget_album_id"
    const val W_PLAYING = "widget_playing"
    const val W_SHUFFLE = "widget_shuffle"
    const val W_REPEAT = "widget_repeat"
}
