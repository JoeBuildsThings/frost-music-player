package com.joebuildsthings.frost.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object MediaStoreRepository {

    private val siteTag = Regex(
        """\s*(\|{1,2}|\(|\[)\s*(www\.)?\S+?\.(com|net|org|ng|info|ws|co|biz)\S*\s*[)\]]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Strips "(www.site.com)" style download site tags. Falls back when the result is empty. */
    internal fun clean(raw: String?, fallback: String, unknown: String = "<unknown>"): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == unknown) return fallback
        val cleaned = trimmed.replace(siteTag, "").trim()
        return cleaned.ifEmpty { trimmed }
    }

    /**
     * Folder for a track. DATA is deprecated but still populated on every Android release so far,
     * and existing hidden folder settings are keyed by it, so it stays the primary source.
     * RELATIVE_PATH covers devices or volumes where DATA comes back empty.
     */
    internal fun folderOf(dataPath: String?, relativePath: String?, primaryRoot: String): String {
        if (!dataPath.isNullOrBlank()) return dataPath.substringBeforeLast('/', "")
        if (!relativePath.isNullOrBlank()) return "$primaryRoot/${relativePath.trimEnd('/')}"
        return ""
    }

    fun loadTracks(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val hasRelative = Build.VERSION.SDK_INT >= 29
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DURATION)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (hasRelative) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        @Suppress("DEPRECATION")
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        val found = ArrayList<Track>()

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            @Suppress("DEPRECATION")
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val relCol = if (hasRelative) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                val relative = if (relCol >= 0) c.getString(relCol) else null
                found.add(
                    Track(
                        id = c.getLong(idCol),
                        title = clean(c.getString(titleCol), "Untitled", MediaStore.UNKNOWN_STRING),
                        artist = clean(c.getString(artistCol), "Unknown artist", MediaStore.UNKNOWN_STRING),
                        album = clean(c.getString(albumCol), "Unknown album", MediaStore.UNKNOWN_STRING),
                        albumId = c.getLong(albumIdCol),
                        trackNo = c.getInt(trackCol) % 1000,
                        durationMs = c.getLong(durationCol),
                        folder = folderOf(c.getString(dataCol), relative, primaryRoot),
                        addedAt = c.getLong(addedCol)
                    )
                )
            }
        }
        return found
    }
}
