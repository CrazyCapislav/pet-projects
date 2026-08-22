package dev.rahim.feedhub.domain

enum class FeedStatus {
    /** Polled on schedule. */
    ACTIVE,

    /** Recent fetches failed, still retried. */
    FAILING,

    /** Too many consecutive failures; skipped until re-enabled manually. */
    DISABLED,
}
