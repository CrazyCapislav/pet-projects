package dev.rahim.feedhub.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Background refresh settings, bound from the `feedhub.refresh` section of
 * application.yml.
 */
@ConfigurationProperties(prefix = "feedhub.refresh")
data class RefreshProperties(
    /** Disables the scheduler; integration tests rely on this to stay offline. */
    val enabled: Boolean = true,

    /** Delay between refresh cycles. */
    val interval: Duration = Duration.ofMinutes(10),

    /** Timeout for downloading a single feed. */
    val timeout: Duration = Duration.ofSeconds(15),

    /** Upper bound on concurrent downloads, enforced by a semaphore. */
    val maxConcurrent: Int = 8,

    /** Consecutive failures after which a feed is switched to DISABLED. */
    val maxFailuresBeforeDisable: Int = 5,

    /** Skip articles already stored under another subscription. */
    val crossFeedDeduplication: Boolean = true,

    /** Many sites reject requests without a meaningful User-Agent. */
    val userAgent: String = "feedhub/1.0 (+https://github.com/CrazyCapislav/feedhub)",

    /** Response size limit, so a malformed feed cannot exhaust the heap. */
    val maxResponseBytes: Int = 8 * 1024 * 1024,
)
