package dev.rahim.feedhub.fetch

import dev.rahim.feedhub.config.RefreshProperties
import dev.rahim.feedhub.domain.Article
import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.repository.ArticleRepository
import dev.rahim.feedhub.repository.FeedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Persists refresh results.
 *
 * Every method here is blocking rather than `suspend` on purpose. JDBC is
 * blocking and Spring binds the transaction to the current thread; a suspend
 * function may resume on a different thread and lose it. Callers cross the
 * boundary explicitly with `withContext(Dispatchers.IO)`.
 */
@Service
class ArticleIngestService(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
    private val props: RefreshProperties,
) {

    private val log = LoggerFactory.getLogger(ArticleIngestService::class.java)

    /** Stores new articles and updates feed metadata. Returns how many were added. */
    @Transactional
    fun ingest(feedId: Long, parsed: ParsedFeed, etag: String?, lastModified: String?): Int {
        val feed = feedRepository.findById(feedId).orElse(null) ?: return 0
        val now = Instant.now()

        // Only overwrite feed metadata when the publisher actually sent it.
        parsed.title?.let { feed.title = it }
        parsed.siteUrl?.let { feed.siteUrl = it }
        feed.recordSuccess(now, etag, lastModified)

        val incoming = parsed.items.distinctBy { it.guid }
        if (incoming.isEmpty()) {
            feedRepository.save(feed)
            return 0
        }

        val fresh = selectNew(feedId, incoming).map { it.toArticle(feed, now) }
        if (fresh.isNotEmpty()) {
            articleRepository.saveAll(fresh)
        }
        feedRepository.save(feed)

        log.debug("Feed {}: {} items received, {} new", feed.url, incoming.size, fresh.size)
        return fresh.size
    }

    @Transactional
    fun recordNotModified(feedId: Long) {
        feedRepository.findById(feedId).ifPresent { feed ->
            feed.recordNotModified(Instant.now())
            feedRepository.save(feed)
        }
    }

    @Transactional
    fun recordFailure(feedId: Long, reason: String) {
        feedRepository.findById(feedId).ifPresent { feed ->
            feed.recordFailure(Instant.now(), reason, props.maxFailuresBeforeDisable)
            feedRepository.save(feed)
            log.warn("Feed {} failed ({} in a row): {}", feed.url, feed.consecutiveFailures, reason)
        }
    }

    /**
     * Filters out items already stored, using two bulk lookups instead of one
     * query per item.
     *
     * Within a feed the guid is the identity. Across feeds the canonical URL is:
     * a site-wide feed and a section feed publish the same article under
     * different guids, and without this check it would appear twice in the list.
     */
    private fun selectNew(feedId: Long, incoming: List<ParsedItem>): List<ParsedItem> {
        val knownGuids = articleRepository.findExistingGuids(feedId, incoming.map { it.guid })
        val candidates = incoming.filterNot { it.guid in knownGuids }
        if (candidates.isEmpty() || !props.crossFeedDeduplication) return candidates

        val canonical = candidates.associateWith { Article.canonicalize(it.link) }
        val known = articleRepository.findExistingCanonicalUrls(canonical.values.distinct())

        // Two feeds can deliver the same article within one refresh cycle, so
        // also deduplicate inside the batch.
        val seen = mutableSetOf<String>()
        return candidates.filter { item ->
            val url = canonical.getValue(item)
            url !in known && seen.add(url)
        }
    }
}

private fun ParsedItem.toArticle(feed: Feed, fetchedAt: Instant): Article =
    Article(
        feed = feed,
        guid = guid,
        title = title,
        link = link,
        canonicalUrl = Article.canonicalize(link),
        author = author,
        summary = summary,
        // Undated items would otherwise sink to the bottom of the list forever.
        publishedAt = publishedAt ?: fetchedAt,
        fetchedAt = fetchedAt,
    )
