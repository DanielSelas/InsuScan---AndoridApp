package com.example.insuscan.auth.util

object AuthErrorHandler {
    
    fun mapFirebaseError(error: String?): String {
        return when {
            error == null -> "An unknown error occurred"
            error.contains("no user record", ignoreCase = true) -> "No account found with this email"
            error.contains("password is invalid", ignoreCase = true) -> "Incorrect password"
            error.contains("email address is already in use", ignoreCase = true) -> "An account with this email already exists"
            error.contains("badly formatted", ignoreCase = true) -> "Invalid email format"
            error.contains("network error", ignoreCase = true) -> "Network error. Check your connection"
            error.contains("too many requests", ignoreCase = true) -> "Too many attempts. Try again later"
            else -> error
        }
    }
}
