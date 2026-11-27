package com.joebuildsthings.frost.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** Compact 4x1 player: title, artist, previous, play, next. */
class FrostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        FrostWidgets.renderCompact(context, WidgetState.read(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!FrostWidgets.handleCommand(this, context, intent)) super.onReceive(context, intent)
    }
}
