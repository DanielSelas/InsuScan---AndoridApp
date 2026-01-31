package com.example.insuscan.network.exception

// Sealed class for scan-related errors - clean OOP hierarchy
sealed class ScanException(message: String) : Exception(message) {

    // No food was detected in the image (server returned 422)
    class NoFoodDetected(
        message: String = "No food detected in image"
    ) : ScanException(message)

    // Server error (5xx)
    class ServerError(
        val code: Int,
        message: String = "Server error"
    ) : ScanException("Server error ($code): $message")

    // Network error (no connection, timeout, etc.)
    class NetworkError(
        cause: Throwable? = null
    ) : ScanException(cause?.message ?: "Network error")

    // Unauthorized (401)
    class Unauthorized(
        message: String = "Please log in again"
    ) : ScanException(message)

    // Generic/unknown error
    class Unknown(
        message: String = "An unexpected error occurred"
    ) : ScanException(message)
}