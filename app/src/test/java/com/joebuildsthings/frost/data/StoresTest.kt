package com.joebuildsthings.frost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoresTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun userDataRoundTrips() {
        val store = UserStore(tmp.root.resolve("user.json"))
        val data = UserData(
            favorites = setOf(1L, 2L),
            playlists = listOf(Playlist("p1", "Gym", listOf(3L, 1L))),
            excluded = setOf("/storage/emulated/0/Recordings"),
            minSeconds = 45,
            recent = listOf(2L, 1L),
            plays = mapOf(1L to 7, 2L to 1),
            sortMode = SortMode.ARTIST
        )
        store.save(data)
        val back = store.load()
        assertEquals(data.favorites, back.favorites)
        assertEquals(data.playlists, back.playlists)
        assertEquals(data.excluded, back.excluded)
        assertEquals(45, back.minSeconds)
        assertEquals(data.recent, back.recent)
        assertEquals(data.plays, back.plays)
        assertEquals(SortMode.ARTIST, back.sortMode)
    }

    @Test
    fun missingOrCorruptUserFileGivesDefaults() {
        val file = tmp.root.resolve("user.json")
        assertEquals(30, UserStore(file).load().minSeconds)
        file.writeText("{not json")
        assertEquals(emptySet<Long>(), UserStore(file).load().favorites)
    }

    @Test
    fun sessionRoundTrips() {
        val store = SessionStore(tmp.root.resolve("session.json"))
        assertNull(store.load())
        store.save(listOf(5L, 6L, 7L), 1, 42_000L)
        val back = store.load()!!
        assertEquals(listOf(5L, 6L, 7L), back.ids)
        assertEquals(1, back.index)
        assertEquals(42_000L, back.positionMs)
    }

    @Test
    fun libraryCacheRoundTrips() {
        val cache = LibraryCache(tmp.root.resolve("library.json"))
        val tracks = listOf(Track(9L, "Title", "Artist", "Album", 3L, 4, 200_000L, "/music", 1234L))
        cache.save(tracks)
        assertEquals(tracks, cache.load())
    }
}
