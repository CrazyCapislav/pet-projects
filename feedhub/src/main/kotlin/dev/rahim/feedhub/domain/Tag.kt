package dev.rahim.feedhub.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A topic label attached to one or more feeds. */
@Entity
@Table(name = "tags")
class Tag(
    @Column(nullable = false, length = 50)
    var name: String,

    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /**
     * Identity is the name, not the id: tags are compared while still transient,
     * and Feed keeps them in a Set.
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is Tag && name.equals(other.name, ignoreCase = true))

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = "Tag($name)"

    companion object {
        const val MAX_LENGTH = 50

        /** Tags are stored lowercase so "Kotlin" and "kotlin" stay the same tag. */
        fun normalize(raw: String): String = raw.trim().lowercase()
    }
}
