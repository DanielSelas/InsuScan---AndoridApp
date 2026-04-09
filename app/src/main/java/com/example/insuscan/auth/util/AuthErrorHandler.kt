package com.example.insuscan.auth.util

import com.example.insuscan.auth.exception.AuthException

object AuthErrorHandler {

    /** Maps a raw Firebase error string to a typed [AuthException]. */
    fun toAuthException(error: String?): AuthException {
        return when {
            error == null -> AuthException.Unknown()
            error.contains("no user record", ignoreCase = true) -> AuthException.UserNotFound
            error.contains("password is invalid", ignoreCase = true) -> AuthException.InvalidCredentials
            error.contains("email address is already in use", ignoreCase = true) -> AuthException.EmailAlreadyInUse
            error.contains("badly formatted", ignoreCase = true) -> AuthException.InvalidEmailFormat
            error.contains("weak password", ignoreCase = true) -> AuthException.WeakPassword
            error.contains("network error", ignoreCase = true) -> AuthException.NetworkError
            error.contains("too many requests", ignoreCase = true) -> AuthException.TooManyRequests
            else -> AuthException.Unknown(Exception(error))
        }
    }
}
