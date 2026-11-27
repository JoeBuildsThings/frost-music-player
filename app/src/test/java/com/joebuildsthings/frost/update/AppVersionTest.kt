package com.joebuildsthings.frost.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun v(s: String) = AppVersion.parse(s)!!

    @Test
    fun parsesTagsAndVersionNames() {
        assertEquals(AppVersion(0, 7, 0), v("v0.7.0"))
        assertEquals(AppVersion(1, 2, 0), v("1.2"))
        assertEquals(AppVersion(0, 7, 0, "dev.19"), v("0.7.0-dev.19"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(null))
    }

    @Test
    fun ordersNumericallyNotAlphabetically() {
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("0.7.10") > v("0.7.2"))
    }

    @Test
    fun preReleaseSortsBelowRelease() {
        assertTrue(v("0.7.0-dev.19") < v("0.7.0"))
        assertTrue(v("0.8.0-beta.2") < v("0.8.0-beta.10"))
        assertTrue(v("0.8.0-beta.10") < v("0.8.0"))
    }
}
