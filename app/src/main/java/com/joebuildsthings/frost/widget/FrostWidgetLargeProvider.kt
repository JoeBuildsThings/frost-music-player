package com.joebuildsthings.frost.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** Now Playing 4x2 card: album art, title, artist and album, shuffle, previous, play, next, repeat. */
class FrostWidgetLargeProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        FrostWidgets.renderLarge(context, WidgetState.read(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!FrostWidgets.handleCommand(this, context, intent)) super.onReceive(context, intent)
    }
}
