package dev.rahim.feedhub.repository

import dev.rahim.feedhub.domain.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ArticleRepository : JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    /** Single lookup instead of one existence check per incoming item. */
    @Query("select a.guid from Article a where a.feed.id = :feedId and a.guid in :guids")
    fun findExistingGuids(@Param("feedId") feedId: Long, @Param("guids") guids: Collection<String>): Set<String>

    /** Cross-feed deduplication: the same article may arrive through several subscriptions. */
    @Query("select a.canonicalUrl from Article a where a.canonicalUrl in :urls")
    fun findExistingCanonicalUrls(@Param("urls") urls: Collection<String>): Set<String>

    fun countByFeedId(feedId: Long): Long

    fun countByFeedIdAndIsReadFalse(feedId: Long): Long

    /**
     * Bulk update instead of loading every article and flipping a flag.
     *
     * The statement bypasses the persistence context, so it is cleared afterwards
     * to avoid serving stale entities from the first-level cache.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Article a set a.isRead = true where a.feed.id = :feedId and a.isRead = false")
    fun markAllReadByFeedId(@Param("feedId") feedId: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Article a set a.isRead = true where a.isRead = false")
    fun markAllRead(): Int

    /**
     * Article -> Feed is LAZY and open-in-view is off, so the association has to
     * be fetched here; otherwise building the response DTO fails with
     * LazyInitializationException. The entity graph turns it into a single join
     * rather than one query per row.
     */
    @EntityGraph(attributePaths = ["feed"])
    override fun findAll(spec: Specification<Article>, pageable: Pageable): Page<Article>

    @EntityGraph(attributePaths = ["feed"])
    override fun findById(id: Long): Optional<Article>
}
