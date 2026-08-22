package dev.rahim.feedhub.web

import dev.rahim.feedhub.TestcontainersConfiguration
import dev.rahim.feedhub.domain.Article
import dev.rahim.feedhub.domain.Feed
import dev.rahim.feedhub.domain.Tag
import dev.rahim.feedhub.repository.ArticleRepository
import dev.rahim.feedhub.repository.FeedRepository
import dev.rahim.feedhub.repository.TagRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests against a real PostgreSQL container: Flyway migrations,
 * JPA mapping and the generated SQL are all exercised, which unit tests cannot
 * cover.
 *
 * Requires Docker. The scheduler is disabled so the tests stay offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["feedhub.refresh.enabled=false"])
class ArticleApiIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Autowired
    private lateinit var articleRepository: ArticleRepository

    @Autowired
    private lateinit var tagRepository: TagRepository

    private var feedId: Long = 0
    private var otherFeedId: Long = 0

    @BeforeEach
    fun setUp() {
        articleRepository.deleteAll()
        feedRepository.deleteAll()
        tagRepository.deleteAll()

        val feed = feedRepository.save(
            Feed(
                url = "https://example.com/rss",
                title = "Основная лента",
                tags = mutableSetOf(Tag("kotlin"), Tag("backend")),
            ),
        )
        val other = feedRepository.save(
            Feed(url = "https://other.com/rss", title = "Другая лента", tags = mutableSetOf(Tag("mobile"))),
        )
        feedId = feed.requireId()
        otherFeedId = other.requireId()

        val now = Instant.now()
        articleRepository.saveAll(
            listOf(
                article(feed, "Coroutines in Kotlin", "g1", now.minus(1, ChronoUnit.HOURS)),
                article(feed, "Spring Boot and JPA", "g2", now.minus(2, ChronoUnit.HOURS), isRead = true),
                article(other, "Kotlin on Android", "g3", now.minus(3, ChronoUnit.HOURS)),
            ),
        )
    }

    // ---------- listing and filters ----------

    @Test
    fun `returns all articles sorted by publication date descending`() {
        mockMvc.get("/api/articles")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(3) }
                jsonPath("$.items[0].title") { value("Coroutines in Kotlin") }
                jsonPath("$.items[2].title") { value("Kotlin on Android") }
            }
    }

    @Test
    fun `filters by feed`() {
        mockMvc.get("/api/articles") { param("feedId", feedId.toString()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
            }
    }

    @Test
    fun `filters unread only`() {
        mockMvc.get("/api/articles") { param("unreadOnly", "true") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
            }
    }

    @Test
    fun `filters by feed tag`() {
        mockMvc.get("/api/articles") { param("tag", "kotlin") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
            }

        mockMvc.get("/api/articles") { param("tag", "mobile") }
            .andExpect { jsonPath("$.totalElements") { value(1) } }
    }

    @Test
    fun `tag filter ignores case`() {
        mockMvc.get("/api/articles") { param("tag", "KOTLIN") }
            .andExpect { jsonPath("$.totalElements") { value(2) } }
    }

    @Test
    fun `caps the page size`() {
        mockMvc.get("/api/articles") { param("size", "100000") }
            .andExpect {
                status { isOk() }
                jsonPath("$.size") { value(100) }
            }
    }

    // ---------- full-text search ----------

    @Test
    fun `search matches whole words only`() {
        // The previous LIKE '%ai%' implementation matched "domain" and "available".
        articleRepository.save(
            article(feedRepository.findById(feedId).orElseThrow(), "A joke domain purchase", "g4", Instant.now()),
        )

        mockMvc.get("/api/articles") { param("search", "ai") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(0) }
            }
    }

    @Test
    fun `search stems english words`() {
        mockMvc.get("/api/articles") { param("search", "coroutine") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.items[0].title") { value("Coroutines in Kotlin") }
            }
    }

    @Test
    fun `search ignores case`() {
        mockMvc.get("/api/articles") { param("search", "KOTLIN") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
            }
    }

    @Test
    fun `combines filters`() {
        mockMvc.get("/api/articles") {
            param("feedId", otherFeedId.toString())
            param("search", "kotlin")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    // ---------- read state ----------

    @Test
    fun `toggles the read flag of one article`() {
        val id = articleRepository.findAll().first { it.guid == "g1" }.id

        mockMvc.post("/api/articles/$id/read")
            .andExpect {
                status { isOk() }
                jsonPath("$.isRead") { value(true) }
            }

        mockMvc.get("/api/articles") { param("unreadOnly", "true") }
            .andExpect { jsonPath("$.totalElements") { value(1) } }
    }

    @Test
    fun `marks a whole feed as read without touching other feeds`() {
        mockMvc.post("/api/feeds/$feedId/read-all")
            .andExpect {
                status { isOk() }
                jsonPath("$.markedRead") { value(1) } // g2 was already read
            }

        mockMvc.get("/api/articles") {
            param("feedId", feedId.toString())
            param("unreadOnly", "true")
        }.andExpect { jsonPath("$.totalElements") { value(0) } }

        mockMvc.get("/api/articles") {
            param("feedId", otherFeedId.toString())
            param("unreadOnly", "true")
        }.andExpect { jsonPath("$.totalElements") { value(1) } }
    }

    @Test
    fun `marking an unknown feed as read returns 404`() {
        mockMvc.post("/api/feeds/999999/read-all")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `marks every article as read`() {
        mockMvc.post("/api/articles/read-all")
            .andExpect {
                status { isOk() }
                jsonPath("$.markedRead") { value(2) }
            }

        mockMvc.get("/api/articles") { param("unreadOnly", "true") }
            .andExpect { jsonPath("$.totalElements") { value(0) } }
    }

    // ---------- tags ----------

    @Test
    fun `exposes tags with feed counts`() {
        mockMvc.get("/api/tags")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
            }
    }

    @Test
    fun `replaces the tag set of a feed and drops orphaned tags`() {
        mockMvc.put("/api/feeds/$feedId/tags") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tags":["Kotlin","News"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.tags.length()") { value(2) }
            jsonPath("$.tags[0]") { value("kotlin") }
            jsonPath("$.tags[1]") { value("news") }
        }

        // "backend" is no longer referenced by any feed.
        mockMvc.get("/api/tags")
            .andExpect { jsonPath("$.length()") { value(3) } }
    }

    // ---------- errors ----------

    @Test
    fun `returns 404 with a ProblemDetail body for an unknown article`() {
        mockMvc.get("/api/articles/999999")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
                jsonPath("$.detail") { exists() }
            }
    }

    @Test
    fun `rejects a blank feed url`() {
        mockMvc.post("/api/feeds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"  "}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `rejects an unsupported url scheme`() {
        mockMvc.post("/api/feeds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"ftp://example.com/feed.xml"}"""
        }.andExpect { status { isBadRequest() } }
    }

    private fun article(
        feed: Feed,
        title: String,
        guid: String,
        publishedAt: Instant,
        isRead: Boolean = false,
    ): Article {
        val link = "https://example.com/$guid"
        return Article(
            feed = feed,
            guid = guid,
            title = title,
            link = link,
            canonicalUrl = Article.canonicalize(link),
            summary = "Summary for $title",
            publishedAt = publishedAt,
            isRead = isRead,
        )
    }
}
