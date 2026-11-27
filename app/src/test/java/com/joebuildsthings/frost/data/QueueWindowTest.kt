package com.joebuildsthings.frost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueWindowTest {

    @Test
    fun smallQueueIsSavedWhole() {
        assertEquals(0 until 50, queueWindow(currentIndex = 10, count = 50, max = 2000))
    }

    @Test
    fun emptyQueueSavesNothing() {
        assertTrue(queueWindow(0, 0, 2000).isEmpty())
    }

    @Test
    fun longQueueAlwaysContainsCurrentWithHistory() {
        for (current in listOf(0, 1899, 1900, 2500, 4999)) {
            val w = queueWindow(current, count = 5000, max = 2000)
            assertTrue("current $current in $w", current in w)
            assertTrue(w.count() <= 2000)
            assertTrue("history kept for $current", current - w.first >= minOf(current, 100))
        }
    }

    @Test
    fun outOfRangeIndexIsClamped() {
        assertTrue(queueWindow(99, 10, 2000) == 0 until 10)
    }
}
