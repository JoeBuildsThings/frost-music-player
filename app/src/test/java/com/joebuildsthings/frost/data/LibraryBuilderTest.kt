package com.joebuildsthings.frost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBuilderTest {

    private fun track(
        id: Long,
        title: String,
        artist: String = "A",
        album: String = "Album",
        albumId: Long = 1L,
        trackNo: Int = 0,
        durationMs: Long = 180_000L,
        folder: String = "/storage/emulated/0/Music",
        addedAt: Long = 0L
    ) = Track(id, title, artist, album, albumId, trackNo, durationMs, folder, addedAt)

    @Test
    fun dropsShortTracksAndHiddenFolders() {
        val raw = listOf(
            track(1, "Song"),
            track(2, "Ringtone", durationMs = 5_000L),
            track(3, "Voice note", folder = "/storage/emulated/0/Recordings")
        )
        val lib = LibraryBuilder.build(raw, setOf("/storage/emulated/0/Recordings"), 30, SortMode.TITLE)
        assertEquals(listOf(1L), lib.tracks.map { it.id })
        // Hidden folders still show up in the folder manager so they can be unhidden.
        assertEquals(2, lib.allFolders.size)
        assertEquals(1, lib.folders.size)
    }

    @Test
    fun sortsByTitleCaseInsensitively() {
        val raw = listOf(track(1, "beta"), track(2, "Alpha"), track(3, "gamma"))
        assertEquals(listOf(2L, 1L, 3L), LibraryBuilder.build(raw, emptySet(), 0, SortMode.TITLE).tracks.map { it.id })
    }

    @Test
    fun sortsByRecentlyAdded() {
        val raw = listOf(track(1, "a", addedAt = 10), track(2, "b", addedAt = 30), track(3, "c", addedAt = 20))
        assertEquals(
            listOf(2L, 3L, 1L),
            LibraryBuilder.build(raw, emptySet(), 0, SortMode.RECENTLY_ADDED).tracks.map { it.id }
        )
    }

    @Test
    fun sortsByArtistThenTitle() {
        val raw = listOf(track(1, "z", artist = "b"), track(2, "y", artist = "A"), track(3, "x", artist = "b"))
        assertEquals(listOf(2L, 3L, 1L), LibraryBuilder.build(raw, emptySet(), 0, SortMode.ARTIST).tracks.map { it.id })
    }

    @Test
    fun albumsUseTrackOrderAndDetectVariousArtists() {
        val raw = listOf(
            track(1, "Second", artist = "X", trackNo = 2),
            track(2, "First", artist = "Y", trackNo = 1)
        )
        val album = LibraryBuilder.build(raw, emptySet(), 0, SortMode.TITLE).albums.single()
        assertEquals(listOf(2L, 1L), album.tracks.map { it.id })
        assertEquals("Various artists", album.artist)
    }

    @Test
    fun artistsCountDistinctAlbums() {
        val raw = listOf(track(1, "a", albumId = 1), track(2, "b", albumId = 2), track(3, "c", albumId = 2))
        assertEquals(2, LibraryBuilder.build(raw, emptySet(), 0, SortMode.TITLE).artists.single().albumCount)
    }

    @Test
    fun folderNamesUseLastPathSegment() {
        val raw = listOf(track(1, "a", folder = "/storage/emulated/0/Music/Afrobeats"), track(2, "b", folder = ""))
        val names = LibraryBuilder.build(raw, emptySet(), 0, SortMode.TITLE).folders.map { it.name }
        assertEquals(listOf("Afrobeats", "Root"), names)
    }
}
