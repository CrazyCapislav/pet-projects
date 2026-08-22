package dev.rahim.feedhub.web

import dev.rahim.feedhub.service.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * Maps exceptions to RFC 7807 Problem Details, so clients get a documented
 * error shape instead of a stack trace or an empty body.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ProblemDetail =
        problem(e.status, e.message ?: "Ошибка запроса")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail {
        val details = e.bindingResult.fieldErrors.joinToString("; ") { error ->
            "${error.field}: ${error.defaultMessage}"
        }
        return problem(HttpStatus.BAD_REQUEST, details.ifBlank { "Некорректный запрос" })
    }

    /**
     * Catch-all. The response stays generic on purpose: library messages and
     * stack traces disclose internals. Details go to the log instead.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error("Unhandled exception", e)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервиса")
    }

    private fun problem(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
            type = URI.create("https://feedhub.dev/errors/${status.value()}")
        }
}
