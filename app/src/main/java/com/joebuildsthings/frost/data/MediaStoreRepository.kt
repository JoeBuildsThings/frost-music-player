package com.joebuildsthings.frost.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MediaStoreRepository {

    private val siteTag = Regex(
        """\s*(\|{1,2}|\(|\[)\s*(www\.)?\S+?\.(com|net|org|ng|info|ws|co|biz)\S*\s*[)\]]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private fun clean(raw: String?, fallback: String): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == MediaStore.UNKNOWN_STRING) return fallback
        val cleaned = trimmed.replace(siteTag, "").trim()
        return cleaned.ifEmpty { trimmed }
    }

    fun loadTracks(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val found = ArrayList<Track>()

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: ""
                found.add(
                    Track(
                        id = id,
                        title = clean(c.getString(titleCol), "Untitled"),
                        artist = clean(c.getString(artistCol), "Unknown artist"),
                        album = clean(c.getString(albumCol), "Unknown album"),
                        albumId = c.getLong(albumIdCol),
                        trackNo = c.getInt(trackCol) % 1000,
                        durationMs = c.getLong(durationCol),
                        folder = path.substringBeforeLast('/', ""),
                        uri = ContentUris.withAppendedId(collection, id),
                        addedAt = c.getLong(addedCol)
                    )
                )
            }
        }
        return found
    }
}
