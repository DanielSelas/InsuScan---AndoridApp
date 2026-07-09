package com.example.insuscan.network

import com.example.insuscan.network.exception.ApiException
import org.json.JSONObject

/**
 * Translates network and API failures into short, user-facing messages.
 *
 * [message] gives a coarse reason for list and screen states, while [userMessage]
 * returns the most specific text available, preferring the server-supplied error.
 */
object NetworkErrorPresenter {
    fun isServerUnreachable(error: Throwable?): Boolean =
        error is ApiException.NoConnection ||
                error is ApiException.Timeout ||
                error is ApiException.ServerError

    fun message(error: Throwable?): String =
        if (isServerUnreachable(error)) "Can't reach the server."
        else "Couldn't load data."

    fun userMessage(error: Throwable?): String = when (error) {
        is ApiException.ClientError -> extractServerError(error.body) ?: "Request was rejected."
        is ApiException.ServerError -> extractServerError(error.body) ?: "Server error. Please try again."
        is ApiException -> error.message ?: "Something went wrong."
        else -> "Something went wrong."
    }

    private fun extractServerError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).optString(SERVER_ERROR_FIELD).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private const val SERVER_ERROR_FIELD = "error"
}