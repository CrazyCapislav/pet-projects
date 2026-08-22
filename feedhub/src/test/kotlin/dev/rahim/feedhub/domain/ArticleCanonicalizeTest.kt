package dev.rahim.feedhub.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Canonical URLs are what makes the same article recognisable across two
 * overlapping subscriptions, so the normalisation rules are pinned here.
 */
class ArticleCanonicalizeTest {

    @Test
    fun `drops utm parameters`() {
        assertEquals(
            "https://habr.com/ru/articles/1070880/",
            Article.canonicalize("https://habr.com/ru/articles/1070880/?utm_campaign=1070880&utm_source=habrahabr"),
        )
    }

    @Test
    fun `drops other tracking parameters`() {
        assertEquals(
            "https://example.com/post",
            Article.canonicalize("https://example.com/post?fbclid=abc&gclid=def"),
        )
    }

    @Test
    fun `keeps meaningful query parameters`() {
        assertEquals(
            "https://example.com/post?id=42",
            Article.canonicalize("https://example.com/post?id=42&utm_source=rss"),
        )
    }

    @Test
    fun `drops the fragment`() {
        assertEquals(
            "https://example.com/post",
            Article.canonicalize("https://example.com/post#comments"),
        )
    }

    @Test
    fun `lowercases scheme and host but not the path`() {
        assertEquals(
            "https://example.com/Post",
            Article.canonicalize("HTTPS://Example.COM/Post"),
        )
    }

    @Test
    fun `returns the original string for links the parser rejects`() {
        val malformed = "not a url at all"

        assertEquals(malformed, Article.canonicalize(malformed))
    }
}
