package dev.rahim.feedhub.web

import dev.rahim.feedhub.service.ArticleQuery
import dev.rahim.feedhub.service.ArticleService
import dev.rahim.feedhub.web.dto.ArticleResponse
import dev.rahim.feedhub.web.dto.MarkReadResponse
import dev.rahim.feedhub.web.dto.PagedResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles", description = "Лента статей")
class ArticleController(
    private val articleService: ArticleService,
) {

    @GetMapping
    @Operation(summary = "Статьи с фильтрами и постраничной навигацией")
    fun list(
        @RequestParam(required = false) feedId: Long? = null,
        @RequestParam(required = false) tag: String? = null,
        @RequestParam(required = false, defaultValue = "false") unreadOnly: Boolean = false,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false, defaultValue = "0") page: Int = 0,
        @RequestParam(required = false, defaultValue = "20") size: Int = 20,
    ): PagedResponse<ArticleResponse> {
        val result = articleService.search(
            ArticleQuery(
                feedId = feedId,
                tag = tag,
                unreadOnly = unreadOnly,
                search = search,
                page = page,
                size = size,
            ),
        )
        return PagedResponse.from(result, ArticleResponse::from)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Одна статья")
    fun get(@PathVariable id: Long): ArticleResponse = ArticleResponse.from(articleService.findById(id))

    @PostMapping("/{id}/read")
    @Operation(summary = "Пометить статью прочитанной")
    fun markRead(@PathVariable id: Long): ArticleResponse =
        ArticleResponse.from(articleService.setRead(id, read = true))

    @DeleteMapping("/{id}/read")
    @Operation(summary = "Снять отметку о прочтении")
    fun markUnread(@PathVariable id: Long): ArticleResponse =
        ArticleResponse.from(articleService.setRead(id, read = false))

    @PostMapping("/read-all")
    @Operation(summary = "Пометить прочитанными все статьи во всех подписках")
    fun markAllRead(): MarkReadResponse = MarkReadResponse(articleService.markAllRead())
}
