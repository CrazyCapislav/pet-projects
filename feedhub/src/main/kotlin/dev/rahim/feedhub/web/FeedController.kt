package dev.rahim.feedhub.web

import dev.rahim.feedhub.fetch.RefreshSummary
import dev.rahim.feedhub.service.FeedService
import dev.rahim.feedhub.web.dto.CreateFeedRequest
import dev.rahim.feedhub.web.dto.FeedResponse
import dev.rahim.feedhub.web.dto.MarkReadResponse
import dev.rahim.feedhub.web.dto.RefreshResultResponse
import dev.rahim.feedhub.web.dto.ReplaceTagsRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/feeds")
@Tag(name = "Feeds", description = "Управление подписками")
class FeedController(
    private val feedService: FeedService,
) {

    @GetMapping
    @Operation(summary = "Список подписок со счётчиками статей")
    fun list(): List<FeedResponse> = feedService.findAll().map { feed ->
        val (total, unread) = feedService.counts(feed.requireId())
        FeedResponse.from(feed, total, unread)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Одна подписка")
    fun get(@PathVariable id: Long): FeedResponse {
        val feed = feedService.findById(id)
        val (total, unread) = feedService.counts(id)
        return FeedResponse.from(feed, total, unread)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить подписку и сразу её загрузить")
    fun create(@Valid @RequestBody request: CreateFeedRequest): FeedResponse =
        FeedResponse.from(feedService.add(request.url, request.tags))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить подписку вместе со статьями")
    fun delete(@PathVariable id: Long) = feedService.delete(id)

    @PutMapping("/{id}/tags")
    @Operation(summary = "Заменить набор тегов подписки")
    fun replaceTags(@PathVariable id: Long, @RequestBody request: ReplaceTagsRequest): FeedResponse =
        FeedResponse.from(feedService.replaceTags(id, request.tags))

    @PostMapping("/{id}/read-all")
    @Operation(summary = "Отметить все статьи подписки прочитанными")
    fun markAllRead(@PathVariable id: Long): MarkReadResponse =
        MarkReadResponse(feedService.markAllRead(id))

    @PostMapping("/{id}/enable")
    @Operation(summary = "Вернуть в строй подписку, отключённую после серии ошибок")
    fun enable(@PathVariable id: Long): FeedResponse = FeedResponse.from(feedService.enable(id))

    @PostMapping("/{id}/refresh")
    @Operation(summary = "Обновить одну подписку немедленно")
    fun refresh(@PathVariable id: Long): RefreshResultResponse =
        RefreshResultResponse.from(feedService.refreshOne(id))

    @PostMapping("/refresh")
    @Operation(summary = "Обновить все подписки; отвечает по завершении цикла")
    fun refreshAll(): RefreshSummary = feedService.refreshAll()
}

