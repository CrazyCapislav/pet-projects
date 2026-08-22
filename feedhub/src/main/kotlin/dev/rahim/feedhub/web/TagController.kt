package dev.rahim.feedhub.web

import dev.rahim.feedhub.service.TagService
import dev.rahim.feedhub.web.dto.TagResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tags")
@Tag(name = "Tags", description = "Тематические метки подписок")
class TagController(
    private val tagService: TagService,
) {

    @GetMapping
    @Operation(summary = "Теги, которыми размечена хотя бы одна подписка")
    fun list(): List<TagResponse> = tagService.findAllInUse().map { tag ->
        TagResponse.from(tag, tagService.countFeeds(tag.name))
    }
}
