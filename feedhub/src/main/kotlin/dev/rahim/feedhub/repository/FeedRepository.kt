package dev.rahim.feedhub.repository

import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.FeedStatus
import org.springframework.data.jpa.repository.JpaRepository

interface FeedRepository : JpaRepository<Feed, Long> {

    fun findByUrl(url: String): Feed?

    fun existsByUrl(url: String): Boolean

    fun findAllByStatusIn(statuses: Collection<FeedStatus>): List<Feed>
}
