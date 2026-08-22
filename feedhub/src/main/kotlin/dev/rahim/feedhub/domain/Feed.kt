package dev.rahim.feedhub.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.Instant

/**
 * A subscribed RSS/Atom source.
 *
 * Declared as a regular class rather than a data class: generated equals/hashCode
 * would include lazy associations and trigger unwanted loads.
 */
@Entity
@Table(name = "feeds")
class Feed(
    @Column(nullable = false, unique = true)
    var url: String,

    var title: String? = null,

    var siteUrl: String? = null,

    @Enumerated(EnumType.STRING)
    var status: FeedStatus = FeedStatus.ACTIVE,

    /** Conditional GET validators, sent back as If-None-Match / If-Modified-Since. */
    var etag: String? = null,
    var lastModified: String? = null,

    var lastFetchedAt: Instant? = null,
    var lastSuccessAt: Instant? = null,
    var consecutiveFailures: Int = 0,

    @Column(length = 500)
    var lastError: String? = null,

    var createdAt: Instant = Instant.now(),

    /**
     * Tags are small and always rendered with the feed, so EAGER avoids an
     * extra query per feed on the subscription list.
     */
    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "feed_tags",
        joinColumns = [JoinColumn(name = "feed_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    var tags: MutableSet<Tag> = mutableSetOf(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun recordSuccess(at: Instant, newEtag: String?, newLastModified: String?) {
        lastFetchedAt = at
        lastSuccessAt = at
        consecutiveFailures = 0
        lastError = null
        status = FeedStatus.ACTIVE
        // Publishers do not always resend validators; keep the previous ones.
        etag = newEtag ?: etag
        lastModified = newLastModified ?: lastModified
    }

    fun recordFailure(at: Instant, reason: String, maxFailures: Int) {
        lastFetchedAt = at
        consecutiveFailures += 1
        lastError = reason.take(500)
        status = if (consecutiveFailures >= maxFailures) FeedStatus.DISABLED else FeedStatus.FAILING
    }

    fun recordNotModified(at: Instant) {
        lastFetchedAt = at
        lastSuccessAt = at
        consecutiveFailures = 0
        lastError = null
        status = FeedStatus.ACTIVE
    }

    fun requireId(): Long = requireNotNull(id) { "Feed has not been persisted yet" }
}
