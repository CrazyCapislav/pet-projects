package dev.rahim.feedhub.service

import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.FeedStatus
import dev.rahim.feedhub.fetch.FeedRefreshResult
import dev.rahim.feedhub.fetch.FeedRefreshService
import dev.rahim.feedhub.fetch.RefreshSummary
import dev.rahim.feedhub.repository.ArticleRepository
import dev.rahim.feedhub.repository.FeedRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

/**
 * Subscription operations. Returns entities; mapping to response DTOs is the
 * web layer's job, so the service stays independent of the API format.
 */
@Service
class FeedService(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
    private val refreshService: FeedRefreshService,
    private val tagService: TagService,
) {

    private val log = LoggerFactory.getLogger(FeedService::class.java)

    @Transactional(readOnly = true)
    fun findAll(): List<Feed> = feedRepository.findAll().sortedBy { it.title ?: it.url }

    @Transactional(readOnly = true)
    fun findById(id: Long): Feed =
        feedRepository.findById(id).orElseThrow { ApiException.NotFound("Лента", id) }

    /**
     * Adds a subscription and fetches it immediately so the response already
     * carries the real title instead of a bare URL.
     *
     * The first fetch is best-effort: an unreachable site still yields a stored
     * subscription that the next scheduled cycle picks up.
     */
    @Transactional
    fun add(rawUrl: String, tagNames: Collection<String> = emptyList()): Feed {
        val url = normalizeUrl(rawUrl)

        if (feedRepository.existsByUrl(url)) {
            throw ApiException.Conflict("Подписка на $url уже существует")
        }

        val feed = feedRepository.save(Feed(url = url, tags = tagService.resolveOrCreate(tagNames)))
        val feedId = feed.requireId()

        runCatching { runBlocking { refreshService.refreshById(feedId) } }
            .onFailure { log.warn("Initial fetch of {} failed: {}", url, it.message) }

        return feedRepository.findById(feedId).orElse(feed)
    }

    @Transactional
    fun delete(id: Long) {
        // Articles are removed by the database (on delete cascade, see V1).
        if (!feedRepository.existsById(id)) throw ApiException.NotFound("Лента", id)
        feedRepository.deleteById(id)
        tagService.deleteOrphans()
    }

    /** Brings back a feed that the failure counter switched off. */
    @Transactional
    fun enable(id: Long): Feed {
        val feed = findById(id)
        feed.status = FeedStatus.ACTIVE
        feed.consecutiveFailures = 0
        feed.lastError = null
        return feedRepository.save(feed)
    }

    /** Replaces the whole tag set of a feed. */
    @Transactional
    fun replaceTags(id: Long, tagNames: Collection<String>): Feed {
        val feed = findById(id)
        feed.tags = tagService.resolveOrCreate(tagNames)
        val saved = feedRepository.save(feed)
        tagService.deleteOrphans()
        return saved
    }

    /**
     * Marks every unread article of a feed as read in a single statement and
     * returns the number of rows affected.
     */
    @Transactional
    fun markAllRead(id: Long): Int {
        findById(id) // 404 for an unknown feed before touching articles
        return articleRepository.markAllReadByFeedId(id)
    }

    fun refreshOne(id: Long): FeedRefreshResult =
        runBlocking { refreshService.refreshById(id) } ?: throw ApiException.NotFound("Лента", id)

    fun refreshAll(): RefreshSummary = runBlocking { refreshService.refreshAll() }

    /** Total and unread article counts for one feed. */
    @Transactional(readOnly = true)
    fun counts(feedId: Long): Pair<Long, Long> =
        articleRepository.countByFeedId(feedId) to articleRepository.countByFeedIdAndIsReadFalse(feedId)

    /**
     * Validates and lightly canonicalises the URL.
     *
     * A trailing slash in the path is deliberately preserved: `/feed` and
     * `/feed/` are different resources to the server, and normalising it away
     * regularly turns a working feed into a 404. Only scheme and host, which are
     * case-insensitive by specification, are lowercased.
     */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()

        val uri = runCatching { URI.create(trimmed) }.getOrElse {
            throw ApiException.BadRequest("Некорректный URL: $trimmed")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) {
            throw ApiException.BadRequest("Поддерживаются только http и https, а пришло: $trimmed")
        }
        if (uri.host.isNullOrBlank()) {
            throw ApiException.BadRequest("В URL не указан хост: $trimmed")
        }

        return buildString {
            append(scheme).append("://").append(uri.host.lowercase())
            if (uri.port != -1) append(':').append(uri.port)
            append(uri.rawPath?.ifBlank { "/" } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
    }
}
