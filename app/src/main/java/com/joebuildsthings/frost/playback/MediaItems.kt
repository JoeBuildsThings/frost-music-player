package com.joebuildsthings.frost.playback

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.joebuildsthings.frost.data.Track

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

fun audioUriFor(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/**
 * Builds the MediaItem for a track. Controllers send items without a URI and the service
 * resolves it from the numeric media id, see PlaybackService.onAddMediaItems.
 */
fun Track.toMediaItem(withUri: Boolean = false): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setExtras(Bundle().apply { putLong(FrostSession.EXTRA_ALBUM_ID, albumId) })
    if (albumId > 0L) meta.setArtworkUri(ContentUris.withAppendedId(ALBUM_ART_BASE, albumId))
    val builder = MediaItem.Builder()
        .setMediaId(id.toString())
        .setMediaMetadata(meta.build())
    if (withUri) builder.setUri(audioUriFor(id))
    return builder.build()
}

/** A bare item for an id the library cache does not know. ExoPlayer reads the tags from the file. */
fun bareMediaItem(id: Long): MediaItem =
    MediaItem.Builder().setMediaId(id.toString()).setUri(audioUriFor(id)).build()

fun MediaItem.albumIdOrZero(): Long {
    mediaMetadata.extras?.getLong(FrostSession.EXTRA_ALBUM_ID, 0L)?.let { if (it > 0L) return it }
    return mediaMetadata.artworkUri?.lastPathSegment?.toLongOrNull() ?: 0L
}
