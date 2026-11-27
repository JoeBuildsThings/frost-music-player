package com.joebuildsthings.frost.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ArtCache {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    // newKeySet() is API 24 and crashed on Android 6; this is the API 1 equivalent.
    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun key(albumId: Long, px: Int) = "$albumId:$px"

    fun peek(albumId: Long, px: Int): Bitmap? = cache.get(key(albumId, px))

    fun load(context: Context, albumId: Long, px: Int): Bitmap? {
        if (albumId <= 0L) return null
        val k = key(albumId, px)
        cache.get(k)?.let { return it }
        if (k in missing) return null
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 29) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } else {
                loadLegacy(context, albumId, px)
            }
        } catch (e: Exception) {
            null
        }
        if (bitmap == null) {
            missing.add(k)
            return null
        }
        cache.put(k, bitmap)
        return bitmap
    }

    private fun loadLegacy(context: Context, albumId: Long, px: Int): Bitmap? {
        val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val side = minOf(bounds.outWidth, bounds.outHeight)
        while (side / (sample * 2) >= px) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
