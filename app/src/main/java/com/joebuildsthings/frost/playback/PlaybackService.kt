package com.joebuildsthings.frost.playback

import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joebuildsthings.frost.widget.FrostWidgetProvider

private const val TAG = "FrostService"

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            PlaybackPrefs.KEEP_PLAYING -> {
                val keep = prefs.getBoolean(PlaybackPrefs.KEEP_PLAYING, true)
                exo?.setAudioAttributes(audioAttributes, !keep)
            }
            PlaybackPrefs.EQ_ON, PlaybackPrefs.EQ_LEVELS, PlaybackPrefs.BASS -> applyEffects(prefs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE)
        val keep = prefs.getBoolean(PlaybackPrefs.KEEP_PLAYING, true)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, !keep)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachEffects(audioSessionId)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED
                    )
                ) {
                    publishWidget(player)
                }
            }
        })
        attachEffects(player.audioSessionId)
        session = MediaSession.Builder(this, player)
            .setCallback(ResolveCallback())
            .build()
    }

    private fun publishWidget(player: Player) {
        val meta = player.mediaMetadata
        getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE).edit()
            .putString(PlaybackPrefs.W_TITLE, meta.title?.toString())
            .putString(PlaybackPrefs.W_ARTIST, meta.artist?.toString())
            .putBoolean(PlaybackPrefs.W_PLAYING, player.playWhenReady)
            .apply()
        FrostWidgetProvider.push(this)
    }

    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        val prefs = getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE)
        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val freqs = (0 until bands).joinToString(";") { (eq.getCenterFreq(it.toShort()) / 1000).toString() }
            prefs.edit().putString(PlaybackPrefs.EQ_INFO, "${range[0]},${range[1]},$freqs").apply()
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer unavailable", e)
        }
        try {
            bassBoost = BassBoost(0, sessionId)
        } catch (e: Exception) {
            Log.w(TAG, "Bass boost unavailable", e)
        }
        applyEffects(prefs)
    }

    private fun applyEffects(prefs: SharedPreferences) {
        val on = prefs.getBoolean(PlaybackPrefs.EQ_ON, false)
        val levels = prefs.getString(PlaybackPrefs.EQ_LEVELS, "")
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
        val bass = prefs.getInt(PlaybackPrefs.BASS, 0)
        try {
            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                for (i in 0 until eq.numberOfBands.toInt()) {
                    val level = (levels.getOrNull(i) ?: 0).coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(i.toShort(), level.toShort())
                }
                eq.setEnabled(on)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) bb.setStrength(bass.coerceIn(0, 1000).toShort())
                bb.setEnabled(bass > 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Effects failed", e)
        }
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Effects release failed", e)
        }
        equalizer = null
        bassBoost = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
        releaseEffects()
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        exo = null
        super.onDestroy()
    }
}

private class ResolveCallback : MediaSession.Callback {
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val resolved = mediaItems.map { item ->
            val id = item.mediaId.toLongOrNull()
            if (item.localConfiguration == null && id != null) {
                item.buildUpon()
                    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                    .build()
            } else {
                item
            }
        }.toMutableList()
        return Futures.immediateFuture(resolved)
    }
}
