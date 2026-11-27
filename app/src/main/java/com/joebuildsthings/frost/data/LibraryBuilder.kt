package com.joebuildsthings.frost.data

/** Everything the library tabs show, derived in one pass from the raw MediaStore scan. */
class LibrarySnapshot(
    val tracks: List<Track>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val folders: List<FolderItem>,
    val allFolders: List<FolderItem>
)

object SortMode {
    const val TITLE = 0
    const val RECENTLY_ADDED = 1
    const val ARTIST = 2
}

/** Pure and side effect free, so it runs on Dispatchers.Default and is unit tested on the JVM. */
object LibraryBuilder {

    fun build(raw: List<Track>, excluded: Set<String>, minSeconds: Int, sort: Int): LibrarySnapshot {
        val minMs = minSeconds * 1000L
        val longEnough = raw.filter { it.durationMs >= minMs }
        val filtered = longEnough.filter { it.folder !in excluded }
        val kept = when (sort) {
            SortMode.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt }
            SortMode.ARTIST -> filtered.sortedWith(
                compareBy<Track>({ it.artist.lowercase() }, { it.title.lowercase() })
            )
            else -> filtered.sortedBy { it.title.lowercase() }
        }

        val albums = kept.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy<Track>({ it.trackNo }, { it.title.lowercase() }))
            val names = sorted.map { it.artist }.distinct()
            AlbumItem(id, sorted.first().album, if (names.size > 1) "Various artists" else names.first(), sorted)
        }.sortedBy { it.name.lowercase() }

        val artists = kept.groupBy { it.artist }.map { (name, list) ->
            ArtistItem(name, list.map { it.albumId }.distinct().size, list)
        }.sortedBy { it.name.lowercase() }

        return LibrarySnapshot(kept, albums, artists, folderItems(kept), folderItems(longEnough))
    }

    private fun folderItems(src: List<Track>): List<FolderItem> =
        src.groupBy { it.folder }.map { (path, list) ->
            FolderItem(
                path,
                path.substringAfterLast('/').ifEmpty { "Root" },
                list.sortedBy { it.title.lowercase() }
            )
        }.sortedBy { it.name.lowercase() }
}
