package com.joebuildsthings.frost.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private fun rel(tag: String) = Release(
        tag, AppVersion.parse(tag)!!,
        "https://github.com/o/r/releases/download/$tag/Frost.apk",
        "https://github.com/o/r/releases/tag/$tag", ""
    )

    private val installed = AppVersion.parse("0.7.0")

    @Test
    fun upToDateMeansNoPrompt() {
        assertEquals(UpdateDecision.None, UpdatePolicy.decide(installed, listOf(rel("v0.7.0"), rel("v0.6.18")), null))
    }

    @Test
    fun oneOrTwoBehindIsOptionalAndOffersTheNewest() {
        val one = UpdatePolicy.decide(installed, listOf(rel("v0.7.1")), null)
        assertTrue(one is UpdateDecision.Optional)
        val two = UpdatePolicy.decide(installed, listOf(rel("v0.7.1"), rel("v0.7.2")), null) as UpdateDecision.Optional
        assertEquals("v0.7.2", two.latest.tag)
        assertEquals(2, two.behind)
    }

    @Test
    fun skippingHidesOnlyThatVersion() {
        val releases = listOf(rel("v0.7.1"))
        assertEquals(UpdateDecision.None, UpdatePolicy.decide(installed, releases, "v0.7.1"))
        val next = UpdatePolicy.decide(installed, releases + rel("v0.7.2"), "v0.7.1")
        assertTrue(next is UpdateDecision.Optional)
    }

    @Test
    fun threeBehindIsRequiredEvenIfSkipped() {
        val releases = listOf(rel("v0.7.1"), rel("v0.7.2"), rel("v0.8.0"))
        val d = UpdatePolicy.decide(installed, releases, "v0.8.0")
        assertTrue(d is UpdateDecision.Required)
        assertEquals("v0.8.0", (d as UpdateDecision.Required).latest.tag)
        assertEquals(3, d.behind)
    }

    @Test
    fun unknownInstalledVersionNeverPrompts() {
        assertEquals(UpdateDecision.None, UpdatePolicy.decide(null, listOf(rel("v9.0.0")), null))
    }
}
