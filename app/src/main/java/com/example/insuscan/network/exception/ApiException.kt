package com.example.insuscan.network.exception

/**
 * Sealed exception hierarchy for all HTTP / network failures.
 * Used by [com.example.insuscan.network.repository.base.BaseRepository]
 * so every repository gets typed errors for free.
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // ── Client Errors (4xx) ───────────────────────────────────────────────────

    class ClientError(
        val code: Int,
        message: String = "Client error"
    ) : ApiException("HTTP $code: $message")

    object Unauthorized : ApiException(
        "Session expired. Please sign in again."
    )

    class NotFound(
        resource: String = "Resource"
    ) : ApiException("$resource not found.")

    // ── Server Errors (5xx) ───────────────────────────────────────────────────

    class ServerError(
        val code: Int,
        message: String = "Server error"
    ) : ApiException("Server error ($code): $message")

    // ── Empty Body ────────────────────────────────────────────────────────────

    object EmptyResponse : ApiException(
        "Server returned an empty response."
    )

    // ── Network ───────────────────────────────────────────────────────────────

    class Timeout(
        cause: Throwable? = null
    ) : ApiException("Request timed out. Check your connection.", cause)

    class NoConnection(
        cause: Throwable? = null
    ) : ApiException("No internet connection. Check your network settings.", cause)

    // ── Generic ───────────────────────────────────────────────────────────────

    class Unknown(
        cause: Throwable? = null
    ) : ApiException(cause?.message ?: "An unexpected error occurred.", cause)
}
