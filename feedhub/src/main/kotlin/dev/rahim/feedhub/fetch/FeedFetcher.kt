package dev.rahim.feedhub.fetch

import dev.rahim.feedhub.config.RefreshProperties
import dev.rahim.feedhub.domain.Feed
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import java.net.URI

/**
 * Downloads a single feed over HTTP.
 *
 * Suspending rather than blocking: while the remote host is thinking the thread
 * serves other feeds, which is what allows hundreds of subscriptions to be
 * refreshed on a small pool.
 */
@Component
class FeedFetcher(
    private val feedWebClient: WebClient,
    private val props: RefreshProperties,
) {

    suspend fun fetch(feed: Feed): FetchOutcome {
        // Cancellation propagates into the HTTP call, so a slow host does not
        // keep a request alive after the timeout.
        val outcome = withTimeoutOrNull(props.timeout.toMillis()) {
            runCatching { doFetch(feed) }
                .getOrElse { error -> FetchOutcome.Failed(error.describe()) }
        }

        return outcome ?: FetchOutcome.Failed("Timed out after ${props.timeout.toSeconds()}s")
    }

    private suspend fun doFetch(feed: Feed): FetchOutcome =
        feedWebClient.get()
            // URI.create, not a String: a raw String is treated as a URI template
            // and links containing braces break the request.
            .uri(URI.create(feed.url))
            .headers { headers -> feed.applyConditionalGet(headers) }
            // awaitExchange exposes the raw response, so 304 can be handled as a
            // normal outcome instead of an error.
            .awaitExchange { response ->
                val status = response.statusCode()
                when {
                    status == HttpStatus.NOT_MODIFIED -> {
                        response.releaseBody().awaitSingleOrNull()
                        FetchOutcome.NotModified
                    }

                    status.is2xxSuccessful -> {
                        val headers = response.headers().asHttpHeaders()
                        FetchOutcome.Body(
                            xml = response.awaitBody<String>(),
                            etag = headers.eTag,
                            lastModified = headers.getFirst(HttpHeaders.LAST_MODIFIED),
                        )
                    }

                    else -> {
                        response.releaseBody().awaitSingleOrNull()
                        FetchOutcome.Failed("HTTP ${status.value()}")
                    }
                }
            }
}

/**
 * Conditional GET: ask the server to send a body only if the feed changed.
 * Saves bandwidth on both sides and keeps us a polite client.
 */
private fun Feed.applyConditionalGet(headers: HttpHeaders) {
    etag?.let { headers.set(HttpHeaders.IF_NONE_MATCH, it) }
    lastModified?.let { headers.set(HttpHeaders.IF_MODIFIED_SINCE, it) }
}

/** Short, human-readable reason stored in Feed.lastError. */
private fun Throwable.describe(): String {
    val text = message?.take(200) ?: this::class.simpleName ?: "unknown error"
    return "${this::class.simpleName}: $text"
}
