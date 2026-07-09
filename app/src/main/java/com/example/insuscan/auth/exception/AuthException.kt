package com.example.insuscan.auth.exception

/**
 * Sealed exception hierarchy for authentication and Firebase Auth failures.
 * Used together with [com.example.insuscan.auth.util.AuthErrorHandler], which maps
 * raw Firebase error strings into these typed exceptions.
 */
sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // ── Session ───────────────────────────────────────────────────────────────

    object NotLoggedIn : AuthException("User is not logged in. Please sign in to continue.")

    object TokenExpired : AuthException("Session expired. Please sign in again.")

    // ── Login / Registration ──────────────────────────────────────────────────

    object UserNotFound : AuthException("No account found with this email address.")

    object InvalidCredentials : AuthException("Incorrect email or password.")

    object EmailAlreadyInUse : AuthException("An account with this email already exists.")

    object InvalidEmailFormat : AuthException("Invalid email format.")

    object WeakPassword : AuthException("Password is too weak. Use at least 8 characters.")

    // ── Google Sign-In ────────────────────────────────────────────────────────

    class GoogleSignInFailed(
        cause: Throwable? = null
    ) : AuthException("Google Sign-In failed: ${cause?.message ?: "unknown error"}", cause)

    object GoogleSignInCancelled : AuthException("Google Sign-In was cancelled.")

    // ── Network ───────────────────────────────────────────────────────────────

    object NetworkError : AuthException("Network error. Check your internet connection.")

    object TooManyRequests : AuthException("Too many failed attempts. Please try again later.")

    // ── Generic ───────────────────────────────────────────────────────────────

    class Unknown(
        cause: Throwable? = null
    ) : AuthException(cause?.message ?: "An unknown authentication error occurred.", cause)
}
