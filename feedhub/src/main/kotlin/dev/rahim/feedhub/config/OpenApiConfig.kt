package dev.rahim.feedhub.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Swagger UI is served at /swagger-ui.html, the raw contract at /v3/api-docs. */
@Configuration
class OpenApiConfig {

    @Bean
    fun feedhubOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("feedhub API")
            .version("1.0")
            .description(
                "Агрегатор RSS/Atom-лент: подписки, фоновый параллельный обход источников, " +
                    "дедупликация статей и лента с полнотекстовым поиском.",
            )
            .license(License().name("MIT")),
    )
}
