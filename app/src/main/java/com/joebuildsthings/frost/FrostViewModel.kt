package com.joebuildsthings.frost

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.joebuildsthings.frost.data.AlbumItem
import com.joebuildsthings.frost.data.ArtistItem
import com.joebuildsthings.frost.data.FolderItem
import com.joebuildsthings.frost.data.LibraryBuilder
import com.joebuildsthings.frost.data.LibraryCache
import com.joebuildsthings.frost.data.LibrarySnapshot
import com.joebuildsthings.frost.data.MediaStoreRepository
import com.joebuildsthings.frost.data.Playlist
import com.joebuildsthings.frost.data.SessionStore
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.data.UserData
import com.joebuildsthings.frost.data.UserStore
import com.joebuildsthings.frost.playback.FrostSession
import com.joebuildsthings.frost.playback.PlaybackPrefs
import com.joebuildsthings.frost.playback.PlaybackService
import com.joebuildsthings.frost.playback.toMediaItem
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Frost"

/** A play counts once this much of a song has been heard, scrobbler style: 30 s or half, whichever is shorter. */
internal fun playCountThresholdMs(durationMs: Long): Long =
    if (durationMs <= 0L) 30_000L else minOf(30_000L, durationMs / 2)

class FrostViewModel(app: Application) : AndroidViewModel(app) {

    private val store = UserStore(app)
    private val libraryCache = LibraryCache(app)
    private val sessionStore = SessionStore(app)
    /**
     * Plain executor rather than a coroutine dispatcher on purpose: work queued here still runs
     * after onCleared, so the last favorite or playlist edit before leaving the app is saved.
     */
    private val io = Executors.newSingleThreadExecutor()
    private val userReady = CompletableDeferred<Unit>()
    private val cacheChecked = CompletableDeferred<Unit>()
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var controller: MediaController? = null
    private var rawTracks: List<Track> = emptyList()
    private var byId: Map<Long, Track> = emptyMap()
    private var restored = false
    private var countJob: Job? = null
    private var countedCurrent = false

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var albums by mutableStateOf<List<AlbumItem>>(emptyList())
        private set
    var artists by mutableStateOf<List<ArtistItem>>(emptyList())
        private set
    var folders by mutableStateOf<List<FolderItem>>(emptyList())
        private set
    var allFolders by mutableStateOf<List<FolderItem>>(emptyList())
        private set
    var scanned by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set

    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var excluded by mutableStateOf<Set<String>>(emptySet())
        private set
    var minSeconds by mutableStateOf(30)
        private set
    var sortMode by mutableStateOf(0)
        private set
    var recent by mutableStateOf<List<Long>>(emptyList())
        private set
    var plays by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set

    var current by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var speed by mutableStateOf(1f)
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var queueIndex by mutableStateOf(0)
        private set
    var sleepEndsAt by mutableStateOf(0L)
        private set
    var sleepMinutes by mutableStateOf(0)
        private set
    var keepPlaying by mutableStateOf(PlaybackPrefs.KEEP_PLAYING_DEFAULT)
        private set
    var darkMode by mutableStateOf(false)
        private set
    var songFilter by mutableStateOf(0)
        private set

    var eqOn by mutableStateOf(false)
        private set
    var eqLevels by mutableStateOf<List<Int>>(emptyList())
        private set
    var eqFreqs by mutableStateOf<List<Int>>(emptyList())
        private set
    var eqMin by mutableStateOf(-1500)
        private set
    var eqMax by mutableStateOf(1500)
        private set
    var bass by mutableStateOf(0)
        private set

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) rebuildQueue(player)
            syncPlayer(player)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                // Also fires on Repeat One loops, so replays count again.
                countJob?.cancel()
                countedCurrent = false
            }
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) {
                watchForPlayCount(player)
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = readSleep(extras)
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackPrefs.EQ_INFO) loadEq()
    }

    init {
        keepPlaying = prefs().getBoolean(PlaybackPrefs.KEEP_PLAYING, PlaybackPrefs.KEEP_PLAYING_DEFAULT)
        darkMode = prefs().getBoolean(PlaybackPrefs.DARK_MODE, false)
        loadEq()
        prefs().registerOnSharedPreferenceChangeListener(prefsListener)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { store.load() }
            favorites = data.favorites
            playlists = data.playlists
            excluded = data.excluded
            minSeconds = data.minSeconds
            recent = data.recent
            plays = data.plays
            sortMode = data.sortMode
            userReady.complete(Unit)

            val cached = withContext(Dispatchers.IO) { libraryCache.load() }
            if (cached != null && cached.isNotEmpty() && !scanned) {
                rawTracks = cached
                byId = cached.associateBy { it.id }
                val ex = excluded
                val min = minSeconds
                val sort = sortMode
                applyLibrary(withContext(Dispatchers.Default) { LibraryBuilder.build(cached, ex, min, sort) })
                scanned = true
                controller?.let {
                    rebuildQueue(it)
                    syncPlayer(it)
                }
                maybeRestore()
            }
            cacheChecked.complete(Unit)
        }
        connect()
    }

    private fun prefs(): SharedPreferences =
        getApplication<Application>().getSharedPreferences(PlaybackPrefs.FILE, Context.MODE_PRIVATE)

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token)
            .setListener(controllerListener)
            .buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                rebuildQueue(c)
                syncPlayer(c)
                readSleep(c.sessionExtras)
                pending.forEach { it(c) }
                pending.clear()
                maybeRestore()
            } catch (e: Exception) {
                Log.e(TAG, "Player connection failed", e)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.add(block)
    }

    private fun rebuildQueue(player: Player) {
        val list = ArrayList<Track>(player.mediaItemCount)
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            val id = item.mediaId.toLongOrNull()
            val known = if (id != null) byId[id] else null
            list.add(
                known ?: Track(
                    id = id ?: -1L,
                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    album = "",
                    albumId = 0L,
                    trackNo = 0,
                    durationMs = 0L,
                    folder = ""
                )
            )
        }
        queue = list
    }

    private fun syncPlayer(player: Player) {
        isPlaying = player.isPlaying
        playWhenReady = player.playWhenReady
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        speed = player.playbackParameters.speed
        queueIndex = player.currentMediaItemIndex
        current = queue.getOrNull(player.currentMediaItemIndex)
    }

    // ---------- history and session ----------

    /** Counts a play once the threshold has actually been heard, not the moment a song starts. */
    private fun watchForPlayCount(player: Player) {
        if (countedCurrent || !player.isPlaying || countJob?.isActive == true) return
        countJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val c = controller ?: return@launch
                if (!c.isPlaying) return@launch
                val t = queue.getOrNull(c.currentMediaItemIndex) ?: return@launch
                if (c.currentPosition >= playCountThresholdMs(durationMs())) {
                    countedCurrent = true
                    notePlayed(t)
                    return@launch
                }
            }
        }
    }

    private fun notePlayed(t: Track) {
        if (t.id < 0L) return
        recent = (listOf(t.id) + recent.filter { it != t.id }).take(100)
        plays = plays + (t.id to ((plays[t.id] ?: 0) + 1))
        persist()
    }

    private fun maybeRestore() {
        val c = controller ?: return
        if (restored || byId.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { sessionStore.load() } ?: return@launch
            val items = s.ids.mapNotNull { byId[it] }
            if (items.isEmpty()) return@launch
            val currentId = s.ids.getOrNull(s.index)
            val idx = items.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
            c.setMediaItems(items.map { it.toMediaItem() }, idx, s.positionMs)
            c.prepare()
        }
    }

    // ---------- library ----------

    fun scan() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            userReady.await()
            val raw: List<Track>? = try {
                withContext(Dispatchers.IO) { MediaStoreRepository.loadTracks(getApplication()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Library scan failed", e)
                null
            }
            if (raw != null && (!scanned || raw != rawTracks)) {
                rawTracks = raw
                byId = raw.associateBy { it.id }
                val ex = excluded
                val min = minSeconds
                val sort = sortMode
                applyLibrary(withContext(Dispatchers.Default) { LibraryBuilder.build(raw, ex, min, sort) })
                controller?.let {
                    rebuildQueue(it)
                    syncPlayer(it)
                }
                scanned = true
                io.execute { libraryCache.save(raw) }
                maybeRestore()
            } else if (raw == null && !scanned) {
                scanned = true
            }
            refreshing = false
        }
    }

    fun loadIfNeeded() {
        viewModelScope.launch {
            cacheChecked.await()
            if (!scanned) scan()
        }
    }

    private fun refilter() {
        val ex = excluded
        val min = minSeconds
        val sort = sortMode
        viewModelScope.launch {
            applyLibrary(withContext(Dispatchers.Default) { LibraryBuilder.build(rawTracks, ex, min, sort) })
        }
    }

    private fun applyLibrary(lib: LibrarySnapshot) {
        tracks = lib.tracks
        albums = lib.albums
        artists = lib.artists
        folders = lib.folders
        allFolders = lib.allFolders
    }

    // ---------- playback ----------

    fun play(list: List<Track>, startIndex: Int) {
        if (list.isEmpty()) return
        withController { c ->
            val idx = startIndex.coerceIn(0, list.lastIndex)
            c.shuffleModeEnabled = false
            val same = c.mediaItemCount == list.size &&
                list.indices.all { c.getMediaItemAt(it).mediaId == list[it].id.toString() }
            if (same) {
                c.seekTo(idx, 0L)
            } else {
                c.setMediaItems(list.map { it.toMediaItem() }, idx, 0L)
                c.prepare()
            }
            c.play()
        }
    }

    fun shuffleAll(list: List<Track>) {
        if (list.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = true
            c.setMediaItems(list.map { it.toMediaItem() }, list.indices.random(), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(listOf(track), 0)
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
            }
        }
    }

    fun addToQueue(track: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(listOf(track), 0)
            } else {
                c.addMediaItem(track.toMediaItem())
            }
        }
    }

    fun jumpTo(index: Int) {
        withController { c ->
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun togglePlay() {
        withController { c ->
            if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
                c.pause()
            } else if (c.mediaItemCount == 0) {
                val available = tracks
                if (available.isNotEmpty()) {
                    c.shuffleModeEnabled = false
                    c.setMediaItems(available.map { it.toMediaItem() }, 0, 0L)
                    c.prepare()
                    c.play()
                }
            } else {
                when (c.playbackState) {
                    // After an error the player sits idle and play() alone does nothing.
                    Player.STATE_IDLE -> c.prepare()
                    Player.STATE_ENDED -> c.seekTo(0, 0L)
                }
                c.play()
            }
        }
    }

    fun next() {
        withController { c -> c.seekToNextMediaItem() }
    }

    fun previous() {
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            if (c.currentPosition > 3_000L) {
                c.seekTo(c.currentMediaItemIndex, 0L)
            } else if (c.hasPreviousMediaItem()) {
                c.seekToPreviousMediaItem()
            } else {
                c.seekTo(c.currentMediaItemIndex, 0L)
            }
        }
    }

    fun seekTo(ms: Long) {
        withController { c -> c.seekTo(ms.coerceAtLeast(0L)) }
    }

    fun toggleShuffle() {
        withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        withController {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun cycleSpeed() {
        withController {
            val order = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
            val i = order.indexOfFirst { s -> kotlin.math.abs(s - it.playbackParameters.speed) < 0.01f }
            it.setPlaybackSpeed(order[(i + 1) % order.size])
        }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long {
        val d = controller?.duration ?: C.TIME_UNSET
        return if (d == C.TIME_UNSET || d < 0L) 0L else d
    }

    /** The timer runs in PlaybackService so it keeps working after the app is closed. */
    fun setSleepTimer(minutes: Int) {
        sleepMinutes = minutes
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        withController { c ->
            c.sendCustomCommand(
                FrostSession.SET_SLEEP,
                Bundle().apply { putInt(FrostSession.ARG_MINUTES, minutes) }
            )
        }
    }

    val sleepEndOfTrack: Boolean
        get() = sleepMinutes == FrostSession.SLEEP_END_OF_TRACK

    private fun readSleep(extras: Bundle) {
        sleepEndsAt = extras.getLong(FrostSession.EXTRA_SLEEP_END_AT, 0L)
        sleepMinutes = extras.getInt(FrostSession.EXTRA_SLEEP_MINUTES, 0)
    }

    fun updateKeepPlaying(enabled: Boolean) {
        keepPlaying = enabled
        prefs().edit().putBoolean(PlaybackPrefs.KEEP_PLAYING, enabled).apply()
    }

    fun updateDarkMode(on: Boolean) {
        darkMode = on
        prefs().edit().putBoolean(PlaybackPrefs.DARK_MODE, on).apply()
    }

    fun updateFilter(filter: Int) {
        songFilter = filter
    }

    // ---------- equalizer ----------

    private fun loadEq() {
        val p = prefs()
        eqOn = p.getBoolean(PlaybackPrefs.EQ_ON, false)
        bass = p.getInt(PlaybackPrefs.BASS, 0)
        val info = p.getString(PlaybackPrefs.EQ_INFO, null)
        if (info != null) {
            val parts = info.split(",", limit = 3)
            if (parts.size == 3) {
                eqMin = parts[0].toIntOrNull() ?: -1500
                eqMax = parts[1].toIntOrNull() ?: 1500
                eqFreqs = parts[2].split(";").mapNotNull { it.toIntOrNull() }
            }
        }
        val saved = p.getString(PlaybackPrefs.EQ_LEVELS, "")
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
        eqLevels = List(eqFreqs.size) { saved.getOrNull(it) ?: 0 }
    }

    private fun writeLevels() {
        prefs().edit().putString(PlaybackPrefs.EQ_LEVELS, eqLevels.joinToString(",")).apply()
    }

    fun updateEqOn(on: Boolean) {
        eqOn = on
        prefs().edit().putBoolean(PlaybackPrefs.EQ_ON, on).apply()
    }

    fun updateEqBand(index: Int, level: Int) {
        if (index !in eqLevels.indices) return
        eqLevels = eqLevels.toMutableList().also { it[index] = level }
        writeLevels()
    }

    fun updateBass(strength: Int) {
        bass = strength
        prefs().edit().putInt(PlaybackPrefs.BASS, strength).apply()
    }

    fun applyEqPreset(kind: Int) {
        val n = eqFreqs.size
        if (n == 0) return
        val amp = eqMax * 6 / 10
        eqLevels = List(n) { i ->
            when (kind) {
                1 -> if (i < 2) amp else if (i == 2) amp / 3 else 0
                2 -> if (i == 0) -amp / 3 else if (i in 2..3) amp else 0
                3 -> if (i >= n - 2) amp else if (i == n - 3) amp / 3 else 0
                else -> 0
            }
        }
        writeLevels()
        if (kind != 0 && !eqOn) updateEqOn(true)
    }

    // ---------- favorites, playlists, settings ----------

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        persist()
    }

    fun createPlaylist(name: String, firstTrackId: Long? = null): String {
        val clean = name.trim().ifEmpty { "New playlist" }
        val p = Playlist(UUID.randomUUID().toString(), clean, listOfNotNull(firstTrackId))
        playlists = playlists + p
        persist()
        return p.id
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        playlists = playlists.map { if (it.id == id) it.copy(name = clean) else it }
        persist()
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        persist()
    }

    fun addToPlaylist(playlistId: String, trackId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
        persist()
    }

    fun removeFromPlaylist(playlistId: String, trackId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(trackIds = it.trackIds.filter { id -> id != trackId }) else it
        }
        persist()
    }

    fun updateMinSeconds(seconds: Int) {
        minSeconds = seconds
        persist()
        refilter()
    }

    fun updateSort(mode: Int) {
        sortMode = mode
        persist()
        refilter()
    }

    fun setFolderHidden(path: String, hidden: Boolean) {
        excluded = if (hidden) excluded + path else excluded - path
        persist()
        refilter()
    }

    private fun persist() {
        val snapshot = UserData(favorites, playlists, excluded, minSeconds, recent, plays, sortMode)
        io.execute { store.save(snapshot) }
    }

    override fun onCleared() {
        countJob?.cancel()
        prefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
        controller?.release()
        // shutdown lets already queued saves finish.
        io.shutdown()
        super.onCleared()
    }
}
