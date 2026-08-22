package dev.rahim.feedhub.fetch

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Triggers the refresh cycle on a schedule.
 *
 * `runBlocking` belongs here and nowhere else in the codebase: @Scheduled can
 * only call a regular function, so this is the boundary between the framework
 * and the coroutine world.
 */
@Component
@ConditionalOnProperty(prefix = "feedhub.refresh", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FeedRefreshScheduler(
    private val refreshService: FeedRefreshService,
) {

    private val log = LoggerFactory.getLogger(FeedRefreshScheduler::class.java)

    /**
     * fixedDelay, not fixedRate: the next run is measured from the end of the
     * previous one, so cycles cannot overlap if a refresh takes longer than the
     * interval.
     */
    @Scheduled(
        fixedDelayString = "\${feedhub.refresh.interval:PT10M}",
        initialDelayString = "\${feedhub.refresh.initial-delay:PT15S}",
    )
    fun scheduledRefresh() {
        try {
            runBlocking { refreshService.refreshAll() }
        } catch (e: Exception) {
            // An exception escaping a @Scheduled method can silence the whole
            // schedule, so it is swallowed after logging.
            log.error("Refresh cycle failed", e)
        }
    }
}
