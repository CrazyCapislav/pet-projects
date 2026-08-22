package dev.rahim.feedhub.service

import dev.rahim.feedhub.domain.Article
import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.Tag
import dev.rahim.feedhub.repository.ArticleRepository
import jakarta.persistence.criteria.JoinType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Filter parameters for the article list, mapped from query parameters. */
data class ArticleQuery(
    val feedId: Long? = null,
    val tag: String? = null,
    val unreadOnly: Boolean = false,
    val search: String? = null,
    val page: Int = 0,
    val size: Int = 20,
)

@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
) {

    @Transactional(readOnly = true)
    fun search(query: ArticleQuery): Page<Article> {
        val pageable = PageRequest.of(
            query.page.coerceAtLeast(0),
            // Upper bound is required: an unbounded size turns one request into a full table scan.
            query.size.coerceIn(1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "publishedAt"),
        )
        return articleRepository.findAll(ArticleSpecs.of(query), pageable)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Article =
        articleRepository.findById(id).orElseThrow { ApiException.NotFound("Статья", id) }

    @Transactional
    fun setRead(id: Long, read: Boolean): Article {
        val article = findById(id)
        article.isRead = read
        return articleRepository.save(article)
    }

    /** Returns the number of articles actually flipped to read. */
    @Transactional
    fun markAllRead(): Int = articleRepository.markAllRead()

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * Dynamic WHERE clause built from the optional filters.
 *
 * A specification returning null contributes nothing to the query, which is how
 * "apply this filter only when the parameter is present" is expressed. The
 * alternative — a single JPQL query with `(:param is null or ...)` — produces
 * plans that cannot use indexes and runs into parameter type inference issues
 * on PostgreSQL.
 */
private object ArticleSpecs {

    fun of(query: ArticleQuery): Specification<Article> =
        Specification.allOf(
            byFeed(query.feedId),
            byTag(query.tag),
            unread(query.unreadOnly),
            matches(query.search),
        )

    private fun byFeed(feedId: Long?) = Specification<Article> { root, _, cb ->
        feedId?.let { cb.equal(root.get<Feed>("feed").get<Long>("id"), it) }
    }

    private fun byTag(tag: String?) = Specification<Article> { root, query, cb ->
        tag?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
            // Joining a to-many association multiplies rows; the count query
            // would report inflated totals without this.
            query.distinct(true)
            val tags = root.join<Article, Feed>("feed").join<Feed, Tag>("tags", JoinType.INNER)
            cb.equal(cb.lower(tags.get("name")), Tag.normalize(name))
        }
    }

    private fun unread(unreadOnly: Boolean) = Specification<Article> { root, _, cb ->
        if (unreadOnly) cb.isFalse(root.get("isRead")) else null
    }

    /**
     * Full-text match through the PostgreSQL function declared in V3.
     *
     * LIKE '%term%' was replaced because it matched inside words — a search for
     * "ai" returned "domain" and "available" — and could not use an index.
     */
    private fun matches(search: String?) = Specification<Article> { root, _, cb ->
        search?.trim()?.takeIf { it.isNotBlank() }?.let { term ->
            cb.isTrue(
                cb.function(
                    "article_matches",
                    Boolean::class.java,
                    root.get<String>("title"),
                    root.get<String>("summary"),
                    cb.literal(term),
                ),
            )
        }
    }
}
