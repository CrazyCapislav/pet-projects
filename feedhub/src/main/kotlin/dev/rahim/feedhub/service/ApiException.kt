package dev.rahim.feedhub.service

import org.springframework.http.HttpStatus

/**
 * Domain errors, each carrying the status the web layer should map it to.
 *
 * Kept separate from Spring's ResponseStatusException so the service layer does
 * not depend on HTTP; the status here is a hint for the transport, not part of
 * the domain contract.
 */
sealed class ApiException(
    message: String,
    val status: HttpStatus,
) : RuntimeException(message) {

    class NotFound(what: String, id: Any) : ApiException("Не найдено: $what с id=$id", HttpStatus.NOT_FOUND)

    class Conflict(message: String) : ApiException(message, HttpStatus.CONFLICT)

    class BadRequest(message: String) : ApiException(message, HttpStatus.BAD_REQUEST)
}
