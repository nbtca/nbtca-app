package space.nbtca.app.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {
    @Test
    fun parsesFeedWithoutMojibake() {
        val feed = RssParser.parse(fixture("rss.xml"))

        assertTrue("feed should not be empty", feed.all.isNotEmpty())
        assertEquals(2, feed.featured.size)
        assertTrue(feed.recent.size in 1..12)
        assertEquals("prompt-strategies", feed.featured.first().id)
        assertTrue(feed.featured.first().title.contains("提示词"))
        assertTrue(feed.all.none { it.title.contains("æ") || it.summary.contains("æ") })
        assertTrue(feed.all.all { it.articleUrl.startsWith("https://nbtca.space/posts/") })
        assertTrue(feed.categories.isNotEmpty())
    }

    @Test
    fun keepsNewestFirst() {
        val feed = RssParser.parse(fixture("rss.xml"))
        val dates = feed.all.map { it.published }
        assertFalse(dates.isEmpty())
        assertEquals("prompt-strategies", feed.all.first().id)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText(Charsets.UTF_8)
}
