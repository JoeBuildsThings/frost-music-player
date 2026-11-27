package com.joebuildsthings.frost.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFeedTest {

    private val repo = "JoeBuildsThings/frost-music-player"
    private val dl = "https://github.com/$repo/releases/download"

    private fun release(tag: String, assetUrl: String, draft: Boolean = false, pre: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"html_url":"https://github.com/$repo/releases/tag/$tag",
         "body":"Notes for $tag","assets":[{"name":"Frost-$tag.aab","browser_download_url":"$dl/$tag/Frost-$tag.aab"},
         {"name":"Frost-$tag.apk","browser_download_url":"$assetUrl"}]}
    """.trimIndent()

    @Test
    fun keepsPublishedReleasesWithTrustedApk() {
        val json = "[" + listOf(
            release("v0.7.1", "$dl/v0.7.1/Frost-v0.7.1.apk"),
            release("v0.7.2", "$dl/v0.7.2/Frost-v0.7.2.apk", draft = true),
            release("v0.8.0-beta.1", "$dl/v0.8.0-beta.1/Frost.apk", pre = true),
            release("v0.7.3", "https://evil.example/Frost.apk"),
            release("nightly", "$dl/nightly/Frost.apk")
        ).joinToString(",") + "]"
        val out = ReleaseFeed.parse(json, repo)
        assertEquals(listOf("v0.7.1"), out.map { it.tag })
        assertEquals("$dl/v0.7.1/Frost-v0.7.1.apk", out.single().apkUrl)
        assertEquals("Notes for v0.7.1", out.single().notes)
    }

    @Test
    fun garbageFeedGivesNoReleases() {
        assertTrue(ReleaseFeed.parse("<html>rate limited</html>", repo).isEmpty())
    }

    @Test
    fun downloadMustBeThisRepository() {
        assertTrue(ReleaseFeed.isTrustedDownload("$dl/v1/Frost.apk", repo))
        assertFalse(ReleaseFeed.isTrustedDownload("https://github.com/someone/else/releases/download/v1/x.apk", repo))
        assertFalse(ReleaseFeed.isTrustedDownload("http://github.com/$repo/releases/download/v1/x.apk", repo))
    }
}
