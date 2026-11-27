package com.joebuildsthings.frost.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SessionData(val ids: List<Long>, val index: Int, val positionMs: Long)

class SessionStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    fun load(): SessionData? {
        val text = file.readTextAtomically() ?: return null
        return try {
            val root = JSONObject(text)
            val arr = root.getJSONArray("ids")
            val ids = ArrayList<Long>(arr.length())
            for (i in 0 until arr.length()) ids.add(arr.getLong(i))
            SessionData(ids, root.optInt("i", 0), root.optLong("p", 0L))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun save(ids: List<Long>, index: Int, positionMs: Long) {
        val root = JSONObject()
        root.put("ids", JSONArray(ids))
        root.put("i", index)
        root.put("p", positionMs)
        file.writeTextAtomically(root.toString())
    }

    companion object {
        const val FILE_NAME = "frost_session.json"
    }
}

/**
 * Picks which slice of a long queue gets persisted. Keeps up to [max] items and makes sure the
 * current item and some history before it are inside the window.
 */
fun queueWindow(currentIndex: Int, count: Int, max: Int, history: Int = 100): IntRange {
    if (count <= 0 || max <= 0) return IntRange.EMPTY
    val current = currentIndex.coerceIn(0, count - 1)
    val start = if (current < max - history) 0 else (current - history).coerceAtLeast(0)
    val end = minOf(count, start + max)
    return start until end
}
