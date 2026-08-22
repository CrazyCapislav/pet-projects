package dev.rahim.feedhub.fetch

/**
 * Outcome of downloading one feed.
 *
 * A sealed interface rather than exceptions: 304 and a dead host are expected
 * results, not errors, and the compiler enforces that every `when` over this
 * type stays exhaustive when a new outcome is added.
 */
sealed interface FetchOutcome {

    /** The server returned a fresh body. */
    data class Body(
        val xml: String,
        val etag: String?,
        val lastModified: String?,
    ) : FetchOutcome

    /** 304 Not Modified: nothing changed since the last fetch. */
    data object NotModified : FetchOutcome

    /** Timeout, connection error or a non-2xx status. */
    data class Failed(val reason: String) : FetchOutcome
}

/** Outcome of a full refresh of one feed: download, parse and store. */
sealed interface FeedRefreshResult {

    val feedId: Long

    data class Updated(
        override val feedId: Long,
        val feedUrl: String,
        val itemsInFeed: Int,
        val newArticles: Int,
    ) : FeedRefreshResult

    data class NotModified(
        override val feedId: Long,
        val feedUrl: String,
    ) : FeedRefreshResult

    data class Failed(
        override val feedId: Long,
        val feedUrl: String,
        val reason: String,
    ) : FeedRefreshResult
}

/** Aggregated result of one refresh cycle, returned by POST /api/feeds/refresh. */
data class RefreshSummary(
    val feedsProcessed: Int,
    val feedsUpdated: Int,
    val feedsNotModified: Int,
    val feedsFailed: Int,
    val newArticles: Int,
    val tookMillis: Long,
) {
    companion object {
        fun from(results: List<FeedRefreshResult>, tookMillis: Long): RefreshSummary {
            var updated = 0
            var notModified = 0
            var failed = 0
            var newArticles = 0

            for (result in results) {
                when (result) {
                    is FeedRefreshResult.Updated -> {
                        updated++
                        newArticles += result.newArticles
                    }

                    is FeedRefreshResult.NotModified -> notModified++
                    is FeedRefreshResult.Failed -> failed++
                }
            }

            return RefreshSummary(
                feedsProcessed = results.size,
                feedsUpdated = updated,
                feedsNotModified = notModified,
                feedsFailed = failed,
                newArticles = newArticles,
                tookMillis = tookMillis,
            )
        }
    }
}
