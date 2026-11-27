package com.joebuildsthings.frost.playback

import android.media.AudioManager
import com.joebuildsthings.frost.playCountThresholdMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLogicTest {

    @Test
    fun callModesPauseMusic() {
        assertTrue(CallGuard.isCallMode(AudioManager.MODE_RINGTONE))
        assertTrue(CallGuard.isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(CallGuard.isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(CallGuard.isCallMode(AudioManager.MODE_NORMAL))
    }

    @Test
    fun playCountsAfterThirtySecondsOrHalf() {
        assertEquals(30_000L, playCountThresholdMs(240_000L))
        assertEquals(20_000L, playCountThresholdMs(40_000L))
        assertEquals(30_000L, playCountThresholdMs(0L))
    }

    @Test
    fun keepPlayingIsOffByDefault() {
        assertFalse(PlaybackPrefs.KEEP_PLAYING_DEFAULT)
    }
}
