package dev.rahim.feedhub.web.dto

import dev.rahim.feedhub.domain.Article
import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.FeedStatus
import dev.rahim.feedhub.domain.Tag
import dev.rahim.feedhub.fetch.FeedRefreshResult
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import java.time.Instant

/*
 * Request and response models for the REST API.
 *
 * Entities are deliberately not exposed: doing so ties the public contract to
 * the database schema, leaks internal fields, and triggers
 * LazyInitializationException during serialization.
 */

// ---------- requests ----------

data class CreateFeedRequest(
    @field:NotBlank(message = "URL не может быть пустым")
    @field:Size(max = 2000, message = "URL слишком длинный")
    val url: String,

    /** Optional topic labels applied to the new subscription. */
    val tags: List<String> = emptyList(),
)

data class ReplaceTagsRequest(
    val tags: List<String> = emptyList(),
)

// ---------- responses ----------

data class FeedResponse(
    val id: Long,
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val status: FeedStatus,
    val tags: List<String>,
    val lastFetchedAt: Instant?,
    val lastSuccessAt: Instant?,
    val consecutiveFailures: Int,
    val lastError: String?,
    val totalArticles: Long?,
    val unreadArticles: Long?,
) {
    companion object {
        fun from(feed: Feed, totalArticles: Long? = null, unreadArticles: Long? = null) = FeedResponse(
            id = feed.requireId(),
            url = feed.url,
            title = feed.title,
            siteUrl = feed.siteUrl,
            status = feed.status,
            tags = feed.tags.map { it.name }.sorted(),
            lastFetchedAt = feed.lastFetchedAt,
            lastSuccessAt = feed.lastSuccessAt,
            consecutiveFailures = feed.consecutiveFailures,
            lastError = feed.lastError,
            totalArticles = totalArticles,
            unreadArticles = unreadArticles,
        )
    }
}

data class ArticleResponse(
    val id: Long,
    val feedId: Long,
    val feedTitle: String?,
    val title: String,
    val link: String,
    val author: String?,
    val summary: String?,
    val publishedAt: Instant?,
    val isRead: Boolean,
) {
    companion object {
        fun from(article: Article) = ArticleResponse(
            id = requireNotNull(article.id),
            feedId = article.feed.requireId(),
            feedTitle = article.feed.title,
            title = article.title,
            link = article.link,
            author = article.author,
            summary = article.summary,
            publishedAt = article.publishedAt,
            isRead = article.isRead,
        )
    }
}

data class TagResponse(
    val name: String,
    val feeds: Long,
) {
    companion object {
        fun from(tag: Tag, feeds: Long) = TagResponse(tag.name, feeds)
    }
}

data class MarkReadResponse(val markedRead: Int)

/**
 * Page wrapper of our own.
 *
 * Spring's Page serializes to an unstable JSON shape that changes between
 * versions, and Boot warns about relying on it. This keeps the contract under
 * our control.
 */
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <E : Any, T> from(page: Page<E>, mapper: (E) -> T) = PagedResponse(
            items = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
        )
    }
}

data class RefreshResultResponse(
    val feedId: Long,
    val feedUrl: String,
    val outcome: String,
    val newArticles: Int? = null,
    val itemsInFeed: Int? = null,
    val error: String? = null,
) {
    companion object {
        fun from(result: FeedRefreshResult): RefreshResultResponse = when (result) {
            is FeedRefreshResult.Updated -> RefreshResultResponse(
                feedId = result.feedId,
                feedUrl = result.feedUrl,
                outcome = "UPDATED",
                newArticles = result.newArticles,
                itemsInFeed = result.itemsInFeed,
            )

            is FeedRefreshResult.NotModified -> RefreshResultResponse(
                feedId = result.feedId,
                feedUrl = result.feedUrl,
                outcome = "NOT_MODIFIED",
            )

            is FeedRefreshResult.Failed -> RefreshResultResponse(
                feedId = result.feedId,
                feedUrl = result.feedUrl,
                outcome = "FAILED",
                error = result.reason,
            )
        }
    }
}
