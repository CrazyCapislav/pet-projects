package dev.rahim.feedhub.fetch

import dev.rahim.feedhub.config.RefreshProperties
import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.FeedStatus
import dev.rahim.feedhub.repository.FeedRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Refreshes all subscriptions concurrently.
 *
 * Feeds are polled in parallel because the work is network-bound: twenty feeds
 * at ~300 ms each take six seconds sequentially and well under one second
 * concurrently. A semaphore caps how many requests are in flight so the
 * connection pool is not exhausted and remote hosts are not hammered.
 */
@Service
class FeedRefreshService(
    private val feedRepository: FeedRepository,
    private val fetcher: FeedFetcher,
    private val parser: RssParser,
    private val ingestService: ArticleIngestService,
    private val props: RefreshProperties,
    private val meterRegistry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(FeedRefreshService::class.java)

    private val cycleTimer = Timer.builder(METRIC_CYCLE)
        .description("Duration of a full refresh cycle")
        .register(meterRegistry)

    /**
     * `coroutineScope` gives structured concurrency: the function does not return
     * until every child coroutine finishes, and a failure cancels the siblings.
     */
    suspend fun refreshAll(): RefreshSummary = coroutineScope {
        val startedAt = System.nanoTime()

        val feeds = withContext(Dispatchers.IO) {
            // DISABLED feeds are skipped until someone re-enables them.
            feedRepository.findAllByStatusIn(listOf(FeedStatus.ACTIVE, FeedStatus.FAILING))
        }
        if (feeds.isEmpty()) return@coroutineScope RefreshSummary.from(emptyList(), 0)

        val gate = Semaphore(props.maxConcurrent)
        val results = feeds
            .map { feed -> async { gate.withPermit { refreshOne(feed) } } }
            .awaitAll()

        val elapsedNanos = System.nanoTime() - startedAt
        cycleTimer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS)

        RefreshSummary.from(results, elapsedNanos / 1_000_000).also { summary ->
            log.info(
                "Refresh cycle: {} feeds in {} ms, {} new articles (updated={}, unchanged={}, failed={})",
                summary.feedsProcessed, summary.tookMillis, summary.newArticles,
                summary.feedsUpdated, summary.feedsNotModified, summary.feedsFailed,
            )
        }
    }

    /**
     * Download, parse and store one feed.
     *
     * The `when` needs no else branch: FetchOutcome is a sealed interface, so the
     * compiler rejects the code if a new outcome is added and not handled here.
     */
    suspend fun refreshOne(feed: Feed): FeedRefreshResult {
        val feedId = feed.requireId()

        return when (val outcome = fetcher.fetch(feed)) {
            is FetchOutcome.NotModified -> {
                withContext(Dispatchers.IO) { ingestService.recordNotModified(feedId) }
                count(OUTCOME_NOT_MODIFIED)
                FeedRefreshResult.NotModified(feedId, feed.url)
            }

            is FetchOutcome.Failed -> {
                withContext(Dispatchers.IO) { ingestService.recordFailure(feedId, outcome.reason) }
                count(OUTCOME_FAILED)
                FeedRefreshResult.Failed(feedId, feed.url, outcome.reason)
            }

            is FetchOutcome.Body -> {
                // Parsing is CPU work on a string, so it stays on the calling dispatcher.
                val parsed = runCatching { parser.parse(outcome.xml) }.getOrElse { error ->
                    val reason = "Parse error: ${error.message?.take(200)}"
                    withContext(Dispatchers.IO) { ingestService.recordFailure(feedId, reason) }
                    count(OUTCOME_FAILED)
                    return FeedRefreshResult.Failed(feedId, feed.url, reason)
                }

                // Writing is blocking JDBC work; hand it to the IO dispatcher.
                val newArticles = withContext(Dispatchers.IO) {
                    ingestService.ingest(feedId, parsed, outcome.etag, outcome.lastModified)
                }

                count(OUTCOME_UPDATED)
                meterRegistry.counter(METRIC_ARTICLES).increment(newArticles.toDouble())

                FeedRefreshResult.Updated(
                    feedId = feedId,
                    feedUrl = feed.url,
                    itemsInFeed = parsed.items.size,
                    newArticles = newArticles,
                )
            }
        }
    }

    /** Refreshes a single feed on demand; null when the feed does not exist. */
    suspend fun refreshById(feedId: Long): FeedRefreshResult? {
        val feed = withContext(Dispatchers.IO) { feedRepository.findById(feedId).orElse(null) }
            ?: return null
        return refreshOne(feed)
    }

    private fun count(outcome: String) =
        meterRegistry.counter(METRIC_FEEDS, "outcome", outcome).increment()

    private companion object {
        const val METRIC_CYCLE = "feedhub.refresh.cycle"
        const val METRIC_FEEDS = "feedhub.refresh.feeds"
        const val METRIC_ARTICLES = "feedhub.articles.ingested"

        const val OUTCOME_UPDATED = "updated"
        const val OUTCOME_NOT_MODIFIED = "not_modified"
        const val OUTCOME_FAILED = "failed"
    }
}
