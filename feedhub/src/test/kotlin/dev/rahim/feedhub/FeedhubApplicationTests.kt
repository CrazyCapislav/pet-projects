package dev.rahim.feedhub

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Cheap smoke test: catches configuration typos, missing beans and circular
 * dependencies before anything else runs.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["feedhub.refresh.enabled=false"])
class FeedhubApplicationTests {

    @Test
    fun contextLoads() {
    }
}
