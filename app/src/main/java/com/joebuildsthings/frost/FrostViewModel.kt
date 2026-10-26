package com.joebuildsthings.frost

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.joebuildsthings.frost.data.AlbumItem
import com.joebuildsthings.frost.data.ArtistItem
import com.joebuildsthings.frost.data.FolderItem
import com.joebuildsthings.frost.data.LibraryCache
import com.joebuildsthings.frost.data.MediaStoreRepository
import com.joebuildsthings.frost.data.Playlist
import com.joebuildsthings.frost.data.SessionStore
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.data.UserData
import com.joebuildsthings.frost.data.UserStore
import com.joebuildsthings.frost.playback.PlaybackPrefs
import com.joebuildsthings.frost.playback.PlaybackService
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Frost"

private class Library(
    val tracks: List<Track>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val folders: List<FolderItem>,
    val allFolders: List<FolderItem>
)

class FrostViewModel(app: Application) : AndroidViewModel(app) {

    private val store = UserStore(app)
    private val libraryCache = LibraryCache(app)
    private val sessionStore = SessionStore(app)
    private val ioSerial = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val userReady = CompletableDeferred<Unit>()
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var controller: MediaController? = null
    private var rawTracks: List<Track> = emptyList()
    private var byId: Map<Long, Track> = emptyMap()
    private var sleepJob: Job? = null
    private var tickJob: Job? = null
    private var observerJob: Job? = null
    private var restored = false
    private var lastCounted = -1L

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
    var keepPlaying by mutableStateOf(true)
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
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) {
                noteStarted(player)
                saveSession()
            } else if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                saveSession()
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackPrefs.EQ_INFO) loadEq()
    }

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = viewModelScope.launch {
                delay(1500L)
                scan()
            }
        }
    }

    init {
        keepPlaying = prefs().getBoolean(PlaybackPrefs.KEEP_PLAYING, true)
        loadEq()
        prefs().registerOnSharedPreferenceChangeListener(prefsListener)
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
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
                applyLibrary(withContext(Dispatchers.Default) { buildLibrary(cached, ex, min, sort) })
                scanned = true
                controller?.let {
                    rebuildQueue(it)
                    syncPlayer(it)
                }
                maybeRestore()
            }
        }
        connect()
    }

    private fun prefs(): SharedPreferences =
        getApplication<Application>().getSharedPreferences(PlaybackPrefs.FILE, Context.MODE_PRIVATE)

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                rebuildQueue(c)
                syncPlayer(c)
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
                    folder = "",
                    uri = Uri.EMPTY
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
        if (player.isPlaying) {
            if (tickJob == null) {
                tickJob = viewModelScope.launch {
                    while (true) {
                        delay(10_000L)
                        saveSession()
                    }
                }
            }
        } else {
            tickJob?.cancel()
            tickJob = null
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        if (albumId > 0L) {
            meta.setArtworkUri(
                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            )
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setMediaMetadata(meta.build())
            .build()
    }

    // ---------- history and session ----------

    private fun noteStarted(player: Player) {
        if (!player.isPlaying) return
        val t = queue.getOrNull(player.currentMediaItemIndex) ?: return
        if (t.id < 0L || t.id == lastCounted) return
        lastCounted = t.id
        recent = (listOf(t.id) + recent.filter { it != t.id }).take(100)
        plays = plays + (t.id to ((plays[t.id] ?: 0) + 1))
        persist()
    }

    fun saveSession() {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (n == 0) return
        val cur = c.currentMediaItemIndex
        val start = if (cur >= 1500) cur - 100 else 0
        val end = minOf(n, start + 2000)
        val ids = ArrayList<Long>(end - start)
        for (i in start until end) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { ids.add(it) }
        }
        val index = cur - start
        val pos = c.currentPosition
        viewModelScope.launch(ioSerial) { sessionStore.save(ids, index, pos) }
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
                applyLibrary(withContext(Dispatchers.Default) { buildLibrary(raw, ex, min, sort) })
                controller?.let {
                    rebuildQueue(it)
                    syncPlayer(it)
                }
                scanned = true
                withContext(ioSerial) { libraryCache.save(raw) }
                maybeRestore()
            } else if (raw == null && !scanned) {
                scanned = true
            }
            refreshing = false
        }
    }

    private fun refilter() {
        val ex = excluded
        val min = minSeconds
        val sort = sortMode
        viewModelScope.launch {
            applyLibrary(withContext(Dispatchers.Default) { buildLibrary(rawTracks, ex, min, sort) })
        }
    }

    private fun applyLibrary(lib: Library) {
        tracks = lib.tracks
        albums = lib.albums
        artists = lib.artists
        folders = lib.folders
        allFolders = lib.allFolders
    }

    private fun buildLibrary(raw: List<Track>, excluded: Set<String>, minSeconds: Int, sort: Int): Library {
        val minMs = minSeconds * 1000L
        val filtered = raw.filter { it.durationMs >= minMs && it.folder !in excluded }
        val kept = when (sort) {
            1 -> filtered.sortedByDescending { it.addedAt }
            2 -> filtered.sortedWith(compareBy<Track>({ it.artist.lowercase() }, { it.title.lowercase() }))
            else -> filtered.sortedBy { it.title.lowercase() }
        }

        val albums = kept.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy<Track>({ it.trackNo }, { it.title.lowercase() }))
            val names = sorted.map { it.artist }.distinct()
            AlbumItem(id, sorted.first().album, if (names.size > 1) "Various artists" else names.first(), sorted)
        }.sortedBy { it.name.lowercase() }

        val artists = kept.groupBy { it.artist }.map { (name, list) ->
            ArtistItem(name, list.map { it.albumId }.distinct().size, list)
        }.sortedBy { it.name.lowercase() }

        fun folderItems(src: List<Track>): List<FolderItem> =
            src.groupBy { it.folder }.map { (path, list) ->
                FolderItem(
                    path,
                    path.substringAfterLast('/').ifEmpty { "Root" },
                    list.sortedBy { it.title.lowercase() }
                )
            }.sortedBy { it.name.lowercase() }

        return Library(
            kept,
            albums,
            artists,
            folderItems(kept),
            folderItems(raw.filter { it.durationMs >= minMs })
        )
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
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else if (c.mediaItemCount == 0) {
            play(tracks, 0)
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun cycleSpeed() {
        controller?.let {
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

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes = minutes
        if (minutes <= 0) {
            sleepEndsAt = 0L
            return
        }
        val ms = minutes * 60_000L
        sleepEndsAt = System.currentTimeMillis() + ms
        sleepJob = viewModelScope.launch {
            delay(ms)
            controller?.pause()
            sleepEndsAt = 0L
            sleepMinutes = 0
        }
    }

    fun updateKeepPlaying(enabled: Boolean) {
        keepPlaying = enabled
        prefs().edit().putBoolean(PlaybackPrefs.KEEP_PLAYING, enabled).apply()
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
        viewModelScope.launch(ioSerial) { store.save(snapshot) }
    }

    override fun onCleared() {
        sleepJob?.cancel()
        tickJob?.cancel()
        observerJob?.cancel()
        prefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
        controller?.release()
        ioSerial.close()
        super.onCleared()
    }
}
