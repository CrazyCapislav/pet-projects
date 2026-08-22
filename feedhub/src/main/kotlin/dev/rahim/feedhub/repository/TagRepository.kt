package dev.rahim.feedhub.repository

import dev.rahim.feedhub.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TagRepository : JpaRepository<Tag, Long> {

    fun findByName(name: String): Tag?

    fun findAllByNameIn(names: Collection<String>): List<Tag>

    /** Tags that no feed references any more, so the list stays meaningful. */
    @Query("select t from Tag t where exists (select 1 from Feed f join f.tags ft where ft.id = t.id) order by t.name")
    fun findAllInUse(): List<Tag>

    @Query("select count(f) from Feed f join f.tags t where t.name = :name")
    fun countFeedsByTagName(@Param("name") name: String): Long
}
