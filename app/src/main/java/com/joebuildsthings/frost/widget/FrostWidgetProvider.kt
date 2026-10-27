package com.joebuildsthings.frost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.joebuildsthings.frost.MainActivity
import com.joebuildsthings.frost.R
import com.joebuildsthings.frost.playback.PlaybackPrefs
import com.joebuildsthings.frost.playback.PlaybackService

class FrostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV) {
            command(context, action)
        }
    }

    private fun command(context: Context, action: String) {
        val pending = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                if (c.mediaItemCount == 0) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else if (action == ACTION_PLAY_PAUSE) {
                    if (c.playWhenReady) c.pause() else c.play()
                } else if (action == ACTION_NEXT) {
                    c.seekToNextMediaItem()
                } else {
                    c.seekToPrevious()
                }
            } catch (e: Exception) {
                // the widget stays as it was
            }
            Handler(Looper.getMainLooper()).postDelayed({
                MediaController.releaseFuture(future)
                pending.finish()
            }, 800L)
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.joebuildsthings.frost.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.joebuildsthings.frost.widget.NEXT"
        const val ACTION_PREV = "com.joebuildsthings.frost.widget.PREV"

        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FrostWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences(PlaybackPrefs.FILE, Context.MODE_PRIVATE)
            val title = prefs.getString(PlaybackPrefs.W_TITLE, null) ?: "Frost"
            val artist = prefs.getString(PlaybackPrefs.W_ARTIST, null) ?: ""
            val playing = prefs.getBoolean(PlaybackPrefs.W_PLAYING, false)

            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play
            )
            views.setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_PLAY_PAUSE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT, 3))
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_info, open)
            manager.updateAppWidget(ids, views)
        }

        private fun action(context: Context, name: String, code: Int): PendingIntent {
            val intent = Intent(context, FrostWidgetProvider::class.java).setAction(name)
            return PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
