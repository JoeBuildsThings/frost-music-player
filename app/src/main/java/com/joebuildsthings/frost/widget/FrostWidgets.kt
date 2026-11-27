package com.joebuildsthings.frost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.joebuildsthings.frost.MainActivity
import com.joebuildsthings.frost.R
import com.joebuildsthings.frost.data.ArtCache
import com.joebuildsthings.frost.playback.PlaybackPrefs
import com.joebuildsthings.frost.playback.PlaybackService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** What the widgets show. Written by PlaybackService, read here, so widgets render without a player. */
internal class WidgetState(
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val playing: Boolean,
    val shuffle: Boolean,
    val repeat: Int
) {
    companion object {
        fun read(context: Context): WidgetState {
            val p = context.getSharedPreferences(PlaybackPrefs.FILE, Context.MODE_PRIVATE)
            return WidgetState(
                title = p.getString(PlaybackPrefs.W_TITLE, null) ?: "Frost",
                artist = p.getString(PlaybackPrefs.W_ARTIST, null).orEmpty(),
                album = p.getString(PlaybackPrefs.W_ALBUM, null).orEmpty(),
                albumId = p.getLong(PlaybackPrefs.W_ALBUM_ID, 0L),
                playing = p.getBoolean(PlaybackPrefs.W_PLAYING, false),
                shuffle = p.getBoolean(PlaybackPrefs.W_SHUFFLE, false),
                repeat = p.getInt(PlaybackPrefs.W_REPEAT, Player.REPEAT_MODE_OFF)
            )
        }
    }
}

object FrostWidgets {
    const val ACTION_PLAY_PAUSE = "com.joebuildsthings.frost.widget.PLAY_PAUSE"
    const val ACTION_NEXT = "com.joebuildsthings.frost.widget.NEXT"
    const val ACTION_PREV = "com.joebuildsthings.frost.widget.PREV"
    const val ACTION_SHUFFLE = "com.joebuildsthings.frost.widget.SHUFFLE"
    const val ACTION_REPEAT = "com.joebuildsthings.frost.widget.REPEAT"

    private val ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_SHUFFLE, ACTION_REPEAT)
    private const val ART_PX = 256
    private const val COMMAND_TIMEOUT_MS = 8_000L

    private val artLoader = Executors.newSingleThreadExecutor()

    /** Redraws every Frost widget on the home screen. Cheap when none are placed. */
    fun pushAll(context: Context) {
        val app = context.applicationContext
        val state = WidgetState.read(app)
        renderCompact(app, state)
        renderLarge(app, state)
    }

    // ---------- rendering ----------

    private fun ids(context: Context, provider: Class<out AppWidgetProvider>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    internal fun renderCompact(context: Context, state: WidgetState) {
        val ids = ids(context, FrostWidgetProvider::class.java)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_artist, state.artist)
        views.setImageViewResource(R.id.widget_play, playIcon(state.playing))
        views.setContentDescription(R.id.widget_play, if (state.playing) "Pause" else "Play")
        val target = FrostWidgetProvider::class.java
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, target, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.widget_play, action(context, target, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_next, action(context, target, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widget_info, openApp(context))
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    internal fun renderLarge(context: Context, state: WidgetState) {
        val ids = ids(context, FrostWidgetLargeProvider::class.java)
        if (ids.isEmpty()) return
        val cached = if (state.albumId > 0L) ArtCache.peek(state.albumId, ART_PX) else null
        AppWidgetManager.getInstance(context).updateAppWidget(ids, largeViews(context, state, cached))
        if (cached == null && state.albumId > 0L) {
            // Decode off the main thread, then redraw if the song has not changed meanwhile.
            artLoader.execute {
                val art = ArtCache.load(context, state.albumId, ART_PX) ?: return@execute
                val latest = WidgetState.read(context)
                if (latest.albumId != state.albumId) return@execute
                val fresh = ids(context, FrostWidgetLargeProvider::class.java)
                if (fresh.isNotEmpty()) {
                    AppWidgetManager.getInstance(context).updateAppWidget(fresh, largeViews(context, latest, art))
                }
            }
        }
    }

    private fun largeViews(context: Context, state: WidgetState, art: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player_large)
        views.setTextViewText(R.id.wl_title, state.title)
        val subtitle = listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString("  •  ")
        views.setTextViewText(R.id.wl_subtitle, subtitle.ifEmpty { "Tap play to pick up where you left off" })
        if (art != null) {
            views.setImageViewBitmap(R.id.wl_art, art)
        } else {
            views.setImageViewResource(R.id.wl_art, R.drawable.widget_art_placeholder)
        }
        views.setImageViewResource(R.id.wl_play, playIcon(state.playing))
        views.setContentDescription(R.id.wl_play, if (state.playing) "Pause" else "Play")

        views.setInt(R.id.wl_shuffle, "setImageAlpha", if (state.shuffle) 255 else 110)
        views.setContentDescription(R.id.wl_shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off")
        views.setImageViewResource(
            R.id.wl_repeat,
            if (state.repeat == Player.REPEAT_MODE_ONE) R.drawable.ic_w_repeat_one else R.drawable.ic_w_repeat
        )
        views.setInt(R.id.wl_repeat, "setImageAlpha", if (state.repeat == Player.REPEAT_MODE_OFF) 110 else 255)
        views.setContentDescription(
            R.id.wl_repeat,
            when (state.repeat) {
                Player.REPEAT_MODE_ONE -> "Repeat one"
                Player.REPEAT_MODE_ALL -> "Repeat all"
                else -> "Repeat off"
            }
        )

        val target = FrostWidgetLargeProvider::class.java
        views.setOnClickPendingIntent(R.id.wl_shuffle, action(context, target, ACTION_SHUFFLE))
        views.setOnClickPendingIntent(R.id.wl_prev, action(context, target, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.wl_play, action(context, target, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.wl_next, action(context, target, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.wl_repeat, action(context, target, ACTION_REPEAT))
        views.setOnClickPendingIntent(R.id.wl_info, openApp(context))
        views.setOnClickPendingIntent(R.id.wl_art, openApp(context))
        return views
    }

    private fun playIcon(playing: Boolean) = if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(context: Context, target: Class<out BroadcastReceiver>, name: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            Intent(context, target).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    // ---------- commands ----------

    /** Returns true when the intent was a widget button press and has been handled. */
    fun handleCommand(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean {
        val action = intent.action ?: return false
        if (action !in ACTIONS) return false

        val pending = receiver.goAsync()
        val finished = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val finish = Runnable {
            if (finished.compareAndSet(false, true)) {
                MediaController.releaseFuture(future)
                pending.finish()
            }
        }
        // Never hold the broadcast open forever if the service fails to bind.
        main.postDelayed(finish, COMMAND_TIMEOUT_MS)

        future.addListener({
            try {
                val c = future.get()
                when (action) {
                    // With an empty queue play() triggers onPlaybackResumption in the service.
                    ACTION_PLAY_PAUSE -> if (c.playWhenReady && c.mediaItemCount > 0) c.pause() else c.play()
                    ACTION_NEXT -> if (c.mediaItemCount > 0) c.seekToNextMediaItem()
                    ACTION_PREV -> if (c.mediaItemCount > 0) c.seekToPrevious()
                    ACTION_SHUFFLE -> c.shuffleModeEnabled = !c.shuffleModeEnabled
                    ACTION_REPEAT -> c.repeatMode = when (c.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            } catch (e: Exception) {
                // The widget keeps showing the last known state.
            }
            // Give the command time to reach the service before the controller disconnects.
            main.removeCallbacks(finish)
            main.postDelayed(finish, 800L)
        }, ContextCompat.getMainExecutor(context))
        return true
    }
}
