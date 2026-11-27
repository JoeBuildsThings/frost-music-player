package com.joebuildsthings.frost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreRepositoryTest {

    @Test
    fun stripsDownloadSiteTags() {
        assertEquals("Essence", MediaStoreRepository.clean("Essence (www.example.com)", "x"))
        assertEquals("Essence", MediaStoreRepository.clean("Essence [example.ng]", "x"))
        assertEquals("Essence", MediaStoreRepository.clean("Essence | example.net", "x"))
    }

    @Test
    fun keepsTitlesThatAreNotTags() {
        assertEquals("Love (Remix)", MediaStoreRepository.clean("Love (Remix)", "x"))
        assertEquals("Mr. Brightside", MediaStoreRepository.clean("Mr. Brightside", "x"))
    }

    @Test
    fun fallsBackForMissingValues() {
        assertEquals("Unknown artist", MediaStoreRepository.clean(null, "Unknown artist"))
        assertEquals("Unknown artist", MediaStoreRepository.clean("   ", "Unknown artist"))
        assertEquals("Unknown artist", MediaStoreRepository.clean("<unknown>", "Unknown artist"))
    }

    @Test
    fun folderPrefersDataPath() {
        assertEquals(
            "/storage/emulated/0/Music",
            MediaStoreRepository.folderOf("/storage/emulated/0/Music/a.mp3", "Music/", "/storage/emulated/0")
        )
    }

    @Test
    fun folderFallsBackToRelativePath() {
        assertEquals(
            "/storage/emulated/0/Music/Live",
            MediaStoreRepository.folderOf(null, "Music/Live/", "/storage/emulated/0")
        )
        assertEquals("", MediaStoreRepository.folderOf(null, null, "/storage/emulated/0"))
    }
}
