package dev.rahim.feedhub.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun feedWebClient(builder: WebClient.Builder, props: RefreshProperties): WebClient =
        builder
            .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent)
            .defaultHeader(
                HttpHeaders.ACCEPT,
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
            )
            // The 256 KB default is regularly too small for a full feed.
            .codecs { it.defaultCodecs().maxInMemorySize(props.maxResponseBytes) }
            .build()
}
