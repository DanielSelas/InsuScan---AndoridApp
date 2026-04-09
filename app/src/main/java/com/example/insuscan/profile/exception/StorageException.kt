package com.example.insuscan.profile.exception

/**
 * Sealed exception hierarchy for Firebase Storage failures.
 * Used when uploading or managing profile photos and meal images.
 */
sealed class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // ── Upload ────────────────────────────────────────────────────────────────

    class UploadFailed(
        cause: Throwable? = null
    ) : StorageException("Image upload failed: ${cause?.message ?: "unknown error"}", cause)

    class FileTooLarge(
        val sizeBytes: Long,
        val maxBytes: Long
    ) : StorageException(
        "File is too large (${sizeBytes / 1024}KB). Maximum allowed: ${maxBytes / 1024}KB."
    )

    class InvalidFileType(
        val type: String
    ) : StorageException("File type '$type' is not supported. Please use JPG or PNG.")

    // ── Download / URL ────────────────────────────────────────────────────────

    object DownloadUrlFailed : StorageException("Could not retrieve image URL from storage.")

    // ── Permissions ───────────────────────────────────────────────────────────

    object Unauthorized : StorageException("You are not authorized to access this file.")

    // ── Generic ───────────────────────────────────────────────────────────────

    class Unknown(
        cause: Throwable? = null
    ) : StorageException(cause?.message ?: "An unknown storage error occurred.", cause)
}
