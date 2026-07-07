package com.example.insuscan.network

import com.example.insuscan.network.exception.ApiException
import org.json.JSONObject

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
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}