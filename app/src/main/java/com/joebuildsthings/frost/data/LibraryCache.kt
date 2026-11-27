package com.joebuildsthings.frost.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    fun load(): List<Track>? {
        val text = file.readTextAtomically() ?: return null
        return try {
            val arr = JSONArray(text)
            val out = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Track(
                        id = o.getLong("id"),
                        title = o.getString("t"),
                        artist = o.getString("a"),
                        album = o.getString("al"),
                        albumId = o.getLong("ai"),
                        trackNo = o.getInt("n"),
                        durationMs = o.getLong("d"),
                        folder = o.getString("f"),
                        addedAt = o.optLong("da", 0L)
                    )
                )
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun save(tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("t", t.title)
                    .put("a", t.artist)
                    .put("al", t.album)
                    .put("ai", t.albumId)
                    .put("n", t.trackNo)
                    .put("d", t.durationMs)
                    .put("f", t.folder)
                    .put("da", t.addedAt)
            )
        }
        file.writeTextAtomically(arr.toString())
    }

    companion object {
        const val FILE_NAME = "frost_library.json"
    }
}
