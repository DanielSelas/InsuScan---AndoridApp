package com.example.insuscan.profile.exception

/**
 * Sealed exception hierarchy for Firebase Storage failures.
 * Used when uploading or managing profile photos and meal images.
 */
sealed class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UploadFailed(
        cause: Throwable? = null
    ) : StorageException("Image upload failed: ${cause?.message ?: "unknown error"}", cause)

    object DownloadUrlFailed : StorageException("Could not retrieve image URL from storage.")

    class Unknown(
        cause: Throwable? = null
    ) : StorageException(cause?.message ?: "An unknown storage error occurred.", cause)
}
