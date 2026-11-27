package com.joebuildsthings.frost.playback

import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.joebuildsthings.frost.data.LibraryCache
import com.joebuildsthings.frost.data.SessionStore
import com.joebuildsthings.frost.data.queueWindow
import com.joebuildsthings.frost.widget.FrostWidgets
import java.util.concurrent.Executors

private const val TAG = "FrostService"
private const val SESSION_SAVE_INTERVAL_MS = 10_000L
private const val MAX_SAVED_QUEUE_SIZE = 2000
private const val MAX_ERROR_STREAK = 10

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var sessionStore: SessionStore
    private lateinit var callGuard: CallGuard
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var destroying = false

    private var keepPlaying = PlaybackPrefs.KEEP_PLAYING_DEFAULT
    private var pausedForCall = false
    private var errorStreak = 0

    // Sleep timer. Lives here, not in the UI, so it survives the app being closed.
    private var sleepEndAtWall = 0L
    private var sleepEndAtElapsed = 0L
    private var sleepMinutes = 0

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val periodicSave = object : Runnable {
        override fun run() {
            val player = exo ?: return
            saveSession(player)
            if (!destroying && player.isPlaying) handler.postDelayed(this, SESSION_SAVE_INTERVAL_MS)
        }
    }

    private val sleepFire = Runnable {
        exo?.pause()
        clearSleep()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            PlaybackPrefs.KEEP_PLAYING -> {
                keepPlaying = p.getBoolean(PlaybackPrefs.KEEP_PLAYING, PlaybackPrefs.KEEP_PLAYING_DEFAULT)
                exo?.setAudioAttributes(audioAttributes, !keepPlaying)
                exo?.let { updateCallGuard(it) }
            }
            PlaybackPrefs.EQ_ON, PlaybackPrefs.EQ_LEVELS, PlaybackPrefs.BASS -> applyEffects()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PlaybackPrefs.FILE, MODE_PRIVATE)
        sessionStore = SessionStore(this)
        keepPlaying = prefs.getBoolean(PlaybackPrefs.KEEP_PLAYING, PlaybackPrefs.KEEP_PLAYING_DEFAULT)
        callGuard = CallGuard(this, ::onCallStarted, ::onCallEnded)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, !keepPlaying)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        player.addListener(PlayerEvents(player))
        attachEffects(player.audioSessionId)
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
        publishSleep()
    }

    private inner class PlayerEvents(private val player: ExoPlayer) : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)

        override fun onPlayerError(error: PlaybackException) = skipBrokenItem(player, error)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorStreak = 0
            rescheduleSleep()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                sleepMinutes == FrostSession.SLEEP_END_OF_TRACK
            ) {
                clearSleep()
            }
            // A manual play during a call means the user wants music, so stop managing it.
            if (playWhenReady) pausedForCall = false
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED
                )
            ) {
                saveSession(player)
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED
                )
            ) {
                publishWidget(player)
            }
            updatePeriodicSave(player)
            updateCallGuard(player)
        }
    }

    // ---------- errors ----------

    /**
     * A deleted, moved or corrupt file leaves ExoPlayer idle with an error, and nothing would
     * recover it. Drop the broken item and carry on with the rest of the queue. The streak cap
     * stops a runaway loop when, say, the SD card holding the whole queue is unmounted.
     */
    private fun skipBrokenItem(player: ExoPlayer, error: PlaybackException) {
        Log.w(TAG, "Skipping unplayable item: ${error.errorCodeName}", error)
        if (player.mediaItemCount == 0) return
        errorStreak++
        val resume = player.playWhenReady
        player.removeMediaItem(player.currentMediaItemIndex)
        if (player.mediaItemCount == 0) return
        if (errorStreak >= MAX_ERROR_STREAK) {
            player.pause()
            player.prepare()
            return
        }
        player.prepare()
        if (resume) player.play()
    }

    // ---------- calls when audio focus is off ----------

    private fun updateCallGuard(player: Player) {
        if (keepPlaying && (player.playWhenReady || pausedForCall)) callGuard.start() else callGuard.stop()
        if (!keepPlaying) pausedForCall = false
    }

    private fun onCallStarted() {
        val player = exo ?: return
        if (player.playWhenReady) {
            player.pause()
            pausedForCall = true
        }
    }

    private fun onCallEnded() {
        val player = exo ?: return
        if (pausedForCall) {
            pausedForCall = false
            player.play()
        }
    }

    // ---------- sleep timer ----------

    private fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleepFire)
        exo?.pauseAtEndOfMediaItems = false
        sleepEndAtWall = 0L
        sleepEndAtElapsed = 0L
        sleepMinutes = 0
        when {
            minutes == FrostSession.SLEEP_END_OF_TRACK -> {
                sleepMinutes = minutes
                exo?.pauseAtEndOfMediaItems = true
            }
            minutes > 0 -> {
                val ms = minutes * 60_000L
                sleepMinutes = minutes
                sleepEndAtWall = System.currentTimeMillis() + ms
                sleepEndAtElapsed = SystemClock.elapsedRealtime() + ms
                rescheduleSleep()
            }
        }
        publishSleep()
    }

    /**
     * Handler time stops while the CPU sleeps, so the deadline is kept in elapsedRealtime and
     * the callback is re-aimed whenever playback state changes. Playback holds a wake lock, so
     * while music is playing the callback fires on time.
     */
    private fun rescheduleSleep() {
        handler.removeCallbacks(sleepFire)
        if (sleepEndAtElapsed == 0L) return
        val left = sleepEndAtElapsed - SystemClock.elapsedRealtime()
        if (left <= 0L) sleepFire.run() else handler.postDelayed(sleepFire, left)
    }

    private fun clearSleep() = setSleep(0)

    private fun publishSleep() {
        session?.setSessionExtras(
            Bundle().apply {
                putLong(FrostSession.EXTRA_SLEEP_END_AT, sleepEndAtWall)
                putInt(FrostSession.EXTRA_SLEEP_MINUTES, sleepMinutes)
            }
        )
    }

    // ---------- persistence ----------

    private fun updatePeriodicSave(player: Player) {
        handler.removeCallbacks(periodicSave)
        if (!destroying && player.isPlaying) handler.postDelayed(periodicSave, SESSION_SAVE_INTERVAL_MS)
    }

    private fun saveSession(player: Player) {
        if (destroying || player.mediaItemCount == 0) return
        val currentIndex = player.currentMediaItemIndex
        val window = queueWindow(currentIndex, player.mediaItemCount, MAX_SAVED_QUEUE_SIZE)
        val entries = ArrayList<Pair<Int, Long>>(window.count())
        for (index in window) {
            player.getMediaItemAt(index).mediaId.toLongOrNull()?.let { entries.add(index to it) }
        }
        if (entries.isEmpty()) return
        val savedIndex = entries.indexOfFirst { it.first == currentIndex }.coerceAtLeast(0)
        val ids = entries.map { it.second }
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        io.execute { sessionStore.save(ids, savedIndex, positionMs) }
    }

    private fun publishWidget(player: Player) {
        val meta = player.mediaMetadata
        prefs.edit()
            .putString(PlaybackPrefs.W_TITLE, meta.title?.toString())
            .putString(PlaybackPrefs.W_ARTIST, meta.artist?.toString())
            .putString(PlaybackPrefs.W_ALBUM, meta.albumTitle?.toString())
            .putLong(PlaybackPrefs.W_ALBUM_ID, player.currentMediaItem?.albumIdOrZero() ?: 0L)
            .putBoolean(PlaybackPrefs.W_PLAYING, player.playWhenReady)
            .putBoolean(PlaybackPrefs.W_SHUFFLE, player.shuffleModeEnabled)
            .putInt(PlaybackPrefs.W_REPEAT, player.repeatMode)
            .apply()
        FrostWidgets.pushAll(this)
    }

    // ---------- audio effects ----------

    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
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
        applyEffects()
    }

    private fun applyEffects() {
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

    // ---------- lifecycle ----------

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.let { saveSession(it) }
        // Default: keeps the service when playing, otherwise pauses and stops it.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.player?.let { saveSession(it) }
        destroying = true
        handler.removeCallbacks(periodicSave)
        handler.removeCallbacks(sleepFire)
        callGuard.stop()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        releaseEffects()
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        exo = null
        io.shutdown()
        super.onDestroy()
    }

    // ---------- session callback ----------

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(FrostSession.SET_SLEEP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == FrostSession.ACTION_SET_SLEEP) {
                setSleep(args.getInt(FrostSession.ARG_MINUTES, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(
                SessionResult(SessionError(SessionError.ERROR_NOT_SUPPORTED, "Unknown command"))
            )
        }

        /**
         * Only numeric MediaStore ids are accepted and the URI is always rebuilt from the id.
         * The service is exported, so any installed app can connect, and passing through a
         * caller supplied URI would let it make Frost open arbitrary content or file URIs
         * with Frost's storage permission.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                val id = item.mediaId.toLongOrNull() ?: return@mapNotNull null
                item.buildUpon().setUri(audioUriFor(id)).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /**
         * Rebuilds the last queue when play is pressed with nothing loaded: a Bluetooth or wired
         * headset button, the lockscreen or Quick Settings resume card, or the home screen widget
         * after the app process was killed.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaItemsWithStartPosition>()
            io.execute {
                try {
                    val saved = sessionStore.load()
                    if (saved == null || saved.ids.isEmpty()) {
                        result.setException(UnsupportedOperationException("No saved queue to resume"))
                        return@execute
                    }
                    val byId = LibraryCache(this@PlaybackService).load()?.associateBy { it.id }.orEmpty()
                    val items = saved.ids.map { id ->
                        byId[id]?.toMediaItem(withUri = true) ?: bareMediaItem(id)
                    }
                    val index = saved.index.coerceIn(0, items.lastIndex)
                    result.set(MediaItemsWithStartPosition(items, index, saved.positionMs))
                } catch (e: Exception) {
                    result.setException(e)
                }
            }
            return result
        }
    }
}
