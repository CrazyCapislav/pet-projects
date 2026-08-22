package dev.rahim.feedhub.fetch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the parser: no Spring, no database, no network.
 *
 * Most of the awkward logic in this project lives here — date formats and
 * malformed markup — and it is cheap to cover at this level.
 */
class RssParserTest {

    private val parser = RssParser()

    @Test
    fun `parses RSS 2 0 with a full set of fields`() {
        val feed = parser.parse(RSS_SAMPLE)

        assertEquals("Example feed", feed.title)
        assertEquals("https://example.com", feed.siteUrl)
        assertEquals(2, feed.items.size)

        val first = feed.items.first()
        assertEquals("https://example.com/posts/1", first.guid)
        assertEquals("First post", first.title)
        assertEquals("https://example.com/posts/1", first.link)
        assertEquals("Ivan Petrov", first.author)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), first.publishedAt)
    }

    @Test
    fun `strips html from the summary`() {
        val feed = parser.parse(RSS_SAMPLE)
        val summary = assertNotNull(feed.items.first().summary)

        assertTrue("<b>" !in summary, "HTML tags should be removed, got: $summary")
        assertTrue("bold" in summary, "Text inside tags should survive, got: $summary")
        assertTrue("&amp;" !in summary, "HTML entities should be decoded, got: $summary")
    }

    @Test
    fun `falls back to the link when the publisher omits guid`() {
        val feed = parser.parse(RSS_SAMPLE)

        assertEquals("https://example.com/posts/2", feed.items[1].guid)
    }

    @Test
    fun `parses Atom 1 0`() {
        val feed = parser.parse(ATOM_SAMPLE)

        assertEquals("Atom feed", feed.title)
        assertEquals("https://example.org/", feed.siteUrl)
        assertEquals(1, feed.items.size)

        val entry = feed.items.single()
        assertEquals("urn:uuid:1225c695", entry.guid)
        assertEquals("An Atom entry", entry.title)
        assertEquals("https://example.org/entry/1", entry.link)
        assertEquals("Maria", entry.author)
        assertEquals(Instant.parse("2026-08-02T12:30:00Z"), entry.publishedAt)
    }

    @Test
    fun `skips entries without a link instead of failing the whole feed`() {
        val feed = parser.parse(RSS_WITH_BROKEN_ITEM)

        assertEquals(1, feed.items.size)
        assertEquals("Valid post", feed.items.single().title)
    }

    @Test
    fun `tolerates missing and unparseable dates`() {
        val feed = parser.parse(RSS_WITHOUT_DATE)

        assertNull(feed.items.single().publishedAt)
    }

    @Test
    fun `rejects invalid xml`() {
        assertThrows<FeedParseException> { parser.parse("not xml at all") }
    }

    @Test
    fun `rejects an unknown root element`() {
        assertThrows<FeedParseException> { parser.parse("""<?xml version="1.0"?><html><body/></html>""") }
    }

    /**
     * Regression test for XXE: a feed must not be able to make the server read
     * local files. Either rejecting the document or parsing it without
     * substituting the entity is acceptable.
     */
    @Test
    fun `does not resolve external entities`() {
        val malicious = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0"><channel>
              <title>&xxe;</title>
              <item><title>x</title><link>https://example.com/1</link></item>
            </channel></rss>
        """.trimIndent()

        runCatching { parser.parse(malicious) }.onSuccess { feed ->
            assertTrue(
                feed.title?.contains("root:") != true,
                "Local file contents leaked into the parsed feed",
            )
        }
    }

    private companion object {
        val RSS_SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Example feed</title>
                <link>https://example.com</link>
                <item>
                  <title>First post</title>
                  <link>https://example.com/posts/1</link>
                  <guid isPermaLink="true">https://example.com/posts/1</guid>
                  <author>Ivan Petrov</author>
                  <description>&lt;p&gt;Text with &lt;b&gt;bold&lt;/b&gt; and an ampersand &amp;amp;&lt;/p&gt;</description>
                  <pubDate>Sat, 01 Aug 2026 10:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Second post</title>
                  <link>https://example.com/posts/2</link>
                  <pubDate>Sun, 02 Aug 2026 09:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val ATOM_SAMPLE = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom feed</title>
              <link rel="alternate" href="https://example.org/"/>
              <entry>
                <title>An Atom entry</title>
                <link rel="alternate" href="https://example.org/entry/1"/>
                <id>urn:uuid:1225c695</id>
                <author><name>Maria</name></author>
                <summary>Short summary</summary>
                <published>2026-08-02T12:30:00Z</published>
              </entry>
            </feed>
        """.trimIndent()

        val RSS_WITH_BROKEN_ITEM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>Feed with a broken item</title>
              <item><title>No link here</title></item>
              <item><title>Valid post</title><link>https://example.com/ok</link></item>
            </channel></rss>
        """.trimIndent()

        val RSS_WITHOUT_DATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>No dates</title>
              <item>
                <title>Post</title>
                <link>https://example.com/x</link>
                <pubDate>yesterday evening</pubDate>
              </item>
            </channel></rss>
        """.trimIndent()
    }
}
