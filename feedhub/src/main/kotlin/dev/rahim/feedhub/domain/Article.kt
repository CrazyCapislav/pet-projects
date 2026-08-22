package dev.rahim.feedhub.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.net.URI
import java.time.Instant

/**
 * A single entry parsed from a feed.
 *
 * The (feed_id, guid) constraint is the only real guarantee against duplicates:
 * two concurrent refreshes of the same feed can pass the application-level check
 * at the same time.
 */
@Entity
@Table(
    name = "articles",
    uniqueConstraints = [UniqueConstraint(name = "uk_articles_feed_guid", columnNames = ["feed_id", "guid"])],
)
class Article(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false)
    var feed: Feed,

    @Column(nullable = false)
    var guid: String,

    @Column(nullable = false, length = 1000)
    var title: String,

    @Column(nullable = false, length = 2000)
    var link: String,

    /** Link without tracking parameters, used to detect the same article across feeds. */
    @Column(nullable = false, length = 2000)
    var canonicalUrl: String,

    var author: String? = null,

    @Column(columnDefinition = "text")
    var summary: String? = null,

    var publishedAt: Instant? = null,

    var fetchedAt: Instant = Instant.now(),

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    companion object {
        private val TRACKING_PARAMS = setOf("fbclid", "yclid", "gclid", "igshid", "mc_cid", "mc_eid")

        /**
         * Strips the fragment and tracking parameters so links that differ only
         * by campaign markers compare equal.
         *
         * Falls back to the original string for links the URI parser rejects —
         * feeds do publish malformed URLs.
         */
        fun canonicalize(link: String): String = runCatching {
            val uri = URI(link)
            val query = uri.rawQuery
                ?.split('&')
                ?.filter { param ->
                    val name = param.substringBefore('=').lowercase()
                    name.isNotEmpty() && !name.startsWith("utm_") && name !in TRACKING_PARAMS
                }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString("&")

            buildString {
                append(uri.scheme?.lowercase() ?: "http").append("://")
                append(uri.host?.lowercase() ?: return@runCatching link)
                if (uri.port != -1) append(':').append(uri.port)
                append(uri.rawPath.orEmpty().ifBlank { "/" })
                query?.let { append('?').append(it) }
            }
        }.getOrDefault(link).take(2000)
    }
}
