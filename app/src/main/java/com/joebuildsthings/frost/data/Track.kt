package com.joebuildsthings.frost.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val trackNo: Int,
    val durationMs: Long,
    val folder: String,
    val addedAt: Long = 0L
) {
    /** Derived from the id, so it never goes stale and stays out of equals and hashCode. */
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

@Immutable
data class AlbumItem(val id: Long, val name: String, val artist: String, val tracks: List<Track>)

@Immutable
data class ArtistItem(val name: String, val albumCount: Int, val tracks: List<Track>)

@Immutable
data class FolderItem(val path: String, val name: String, val tracks: List<Track>)

@Immutable
data class Playlist(val id: String, val name: String, val trackIds: List<Long>)
