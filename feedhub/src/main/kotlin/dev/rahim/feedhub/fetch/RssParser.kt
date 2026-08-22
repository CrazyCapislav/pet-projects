package dev.rahim.feedhub.fetch

import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedFeed(
    val title: String?,
    val siteUrl: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val author: String?,
    val summary: String?,
    val publishedAt: Instant?,
)

class FeedParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Parses RSS 2.0 and Atom 1.0 with the JDK DOM parser.
 *
 * Malformed entries are skipped rather than failing the whole feed: publishers
 * regularly emit items without a link or with unparseable dates, and losing one
 * entry is better than losing the feed.
 */
@Component
class RssParser {

    fun parse(xml: String): ParsedFeed {
        val root = parseDocument(xml)

        return when (root.tagName.substringAfterLast(':').lowercase()) {
            "rss", "rdf" -> {
                val channel = root.child("channel")
                    ?: throw FeedParseException("No <channel> element under <${root.tagName}>")
                ParsedFeed(
                    title = channel.text("title"),
                    siteUrl = channel.text("link"),
                    items = (channel.children("item") + root.children("item")).mapNotNull { it.toRssItem() },
                )
            }

            "feed" -> ParsedFeed(
                title = root.text("title"),
                siteUrl = root.alternateLinkHref(),
                items = root.children("entry").mapNotNull { it.toAtomEntry() },
            )

            else -> throw FeedParseException("Unsupported feed format: root element <${root.tagName}>")
        }
    }

    private fun Element.toRssItem(): ParsedItem? {
        val link = text("link") ?: text("guid") ?: return null

        return ParsedItem(
            // guid identifies the item for deduplication; the link is a stable
            // fallback for publishers that omit it.
            guid = (text("guid") ?: link).take(500),
            title = (text("title") ?: UNTITLED).take(1000),
            link = link.take(2000),
            author = text("author", "dc:creator")?.take(255),
            summary = text("description", "content:encoded")?.stripHtml()?.take(2000),
            publishedAt = parseDate(text("pubDate", "dc:date", "published")),
        )
    }

    private fun Element.toAtomEntry(): ParsedItem? {
        val link = alternateLinkHref() ?: text("id") ?: return null

        return ParsedItem(
            guid = (text("id") ?: link).take(500),
            title = (text("title") ?: UNTITLED).take(1000),
            link = link.take(2000),
            author = child("author")?.text("name")?.take(255),
            summary = text("summary", "content")?.stripHtml()?.take(2000),
            publishedAt = parseDate(text("published", "updated")),
        )
    }

    private fun parseDocument(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // XXE hardening. The input comes from arbitrary sites, so external
            // entities must not be resolved: otherwise a feed could make the
            // server read local files or reach into the internal network.
            // Not every parser implementation knows every flag, hence runCatching.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
            isXIncludeAware = false
            isExpandEntityReferences = false
            // Namespace-unaware on purpose: tag names arrive verbatim
            // ("entry", "dc:creator"), which keeps the lookups below simple.
            isNamespaceAware = false
        }

        val document = try {
            // trim() drops a BOM or leading whitespace before <?xml, which the
            // parser would otherwise reject.
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            throw FeedParseException("Malformed XML: ${e.message}", e)
        }

        return document.documentElement ?: throw FeedParseException("Empty XML document")
    }

    /** RSS mandates RFC-822 and Atom ISO-8601, but publishers use both and more. */
    private fun parseDate(raw: String?): Instant? {
        val value = raw?.trim()?.ifBlank { null } ?: return null
        return DATE_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { Instant.from(format.parse(value)) }.getOrNull()
        }
    }

    private companion object {
        const val UNTITLED = "(untitled)"

        val DATE_FORMATS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
        )
    }
}

// DOM helpers. org.w3c.dom predates iterators and generics, so these extensions
// keep the parsing code above readable.

private fun NodeList.asSequence(): Sequence<Node> =
    (0 until length).asSequence().mapNotNull { item(it) }

private fun Element.children(name: String): List<Element> =
    childNodes.asSequence()
        .filterIsInstance<Element>()
        .filter { it.tagName.equals(name, ignoreCase = true) }
        .toList()

/** First direct child matching any of the given tag names. */
private fun Element.child(vararg names: String): Element? =
    names.firstNotNullOfOrNull { name -> children(name).firstOrNull() }

/** Text of the first matching child; blank is treated as absent. */
private fun Element.text(vararg names: String): String? =
    child(*names)?.textContent?.trim()?.ifBlank { null }

/** In Atom the article URL lives in the href of <link rel="alternate">. */
private fun Element.alternateLinkHref(): String? =
    children("link")
        .firstOrNull { link ->
            val rel = link.getAttribute("rel")
            rel.isEmpty() || rel == "alternate"
        }
        ?.getAttribute("href")
        ?.ifBlank { null }

/** Good enough for list previews; the full body is never rendered by this service. */
private fun String.stripHtml(): String =
    replace(SCRIPT_TAG, " ")
        .replace(ANY_TAG, " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&") // last, so &amp;lt; is not expanded twice
        .replace(WHITESPACE, " ")
        .trim()

private val SCRIPT_TAG = Regex("<script.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val ANY_TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")
