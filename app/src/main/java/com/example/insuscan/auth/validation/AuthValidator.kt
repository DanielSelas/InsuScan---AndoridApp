package com.example.insuscan.auth.validation

object AuthValidator {

    fun validateEmail(email: String): String? {
        if (email.isEmpty()) return "Email is required"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "Enter a valid email address"
        }
        return null // Valid
    }

    fun validatePasswordNotEmpty(password: String): String? {
        if (password.isEmpty()) return "Password is required"
        return null // Valid
    }

    fun validatePassword(password: String): String? {
        if (password.isEmpty()) return "Password is required"
        if (password.length < 8) return "At least 8 characters"
        if (!password.any { it.isUpperCase() }) return "Must contain an uppercase letter"
        if (!password.any { it.isLowerCase() }) return "Must contain a lowercase letter"
        if (!password.any { it.isDigit() }) return "Must contain a number"
        if (!password.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" }) return "Must contain a special character"
        return null // Valid
    }

    fun validatePasswordsMatch(password: String, confirmPassword: String): String? {
        if (confirmPassword.isEmpty()) return "Please confirm your password"
        if (password != confirmPassword) return "Passwords don't match"
        return null // Valid
    }
}
