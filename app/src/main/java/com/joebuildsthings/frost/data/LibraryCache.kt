package com.joebuildsthings.frost.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryCache(context: Context) {

    private val file = File(context.filesDir, "frost_library.json")

    fun load(): List<Track>? {
        if (!file.exists()) return null
        return try {
            val arr = JSONArray(file.readText())
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val out = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getLong("id")
                out.add(
                    Track(
                        id = id,
                        title = o.getString("t"),
                        artist = o.getString("a"),
                        album = o.getString("al"),
                        albumId = o.getLong("ai"),
                        trackNo = o.getInt("n"),
                        durationMs = o.getLong("d"),
                        folder = o.getString("f"),
                        uri = ContentUris.withAppendedId(base, id),
                        addedAt = o.optLong("da", 0L)
                    )
                )
            }
            out
        } catch (e: Exception) {
            null
        }
    }

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
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
