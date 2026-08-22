package dev.rahim.feedhub.service

import dev.rahim.feedhub.domain.Tag
import dev.rahim.feedhub.repository.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TagService(
    private val tagRepository: TagRepository,
) {

    @Transactional(readOnly = true)
    fun findAllInUse(): List<Tag> = tagRepository.findAllInUse()

    @Transactional(readOnly = true)
    fun countFeeds(tagName: String): Long = tagRepository.countFeedsByTagName(Tag.normalize(tagName))

    /**
     * Resolves names to entities, creating the ones that do not exist yet.
     *
     * Existing tags are looked up in a single query so adding ten tags does not
     * cost ten round trips.
     */
    @Transactional
    fun resolveOrCreate(rawNames: Collection<String>): MutableSet<Tag> {
        val names = rawNames
            .map(Tag::normalize)
            .filter { it.isNotBlank() }
            .distinct()

        names.firstOrNull { it.length > Tag.MAX_LENGTH }?.let {
            throw ApiException.BadRequest("Тег длиннее ${Tag.MAX_LENGTH} символов: $it")
        }
        if (names.isEmpty()) return mutableSetOf()

        val existing = tagRepository.findAllByNameIn(names).associateBy { it.name }
        val created = names
            .filterNot { it in existing }
            .map { Tag(name = it) }
            .let { if (it.isEmpty()) emptyList() else tagRepository.saveAll(it) }

        return (existing.values + created).toMutableSet()
    }

    /** Removes tags no feed references any more; called after a feed's tags change. */
    @Transactional
    fun deleteOrphans() {
        val inUse = tagRepository.findAllInUse().mapNotNull { it.id }.toSet()
        val orphans = tagRepository.findAll().filter { it.id !in inUse }
        if (orphans.isNotEmpty()) tagRepository.deleteAll(orphans)
    }
}
