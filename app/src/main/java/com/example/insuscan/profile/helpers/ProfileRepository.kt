package com.example.insuscan.profile.helpers

import android.content.Context
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import android.util.Log

/**
 * Performs network operations for the profile screen:
 * fetching the server profile and pushing local edits back.
 */
class ProfileRepository {
    private val userRepository: UserRepository = UserRepositoryImpl()

    /**
     * Fetches the user profile from the server and syncs it to local storage.
     * Returns a failure if no email is saved locally.
     */
    suspend fun fetchServerProfile(context: Context): Result<UserDto> {
        val email = UserProfileManager.getUserEmail(context) ?: return Result.failure(Exception("No email"))
        return userRepository.getUser(email).onSuccess { userDto ->
            UserProfileManager.syncFromServer(context, userDto)
            Log.d("ProfileRepository", "Profile synced from server")
        }.onFailure { e ->
            Log.e("ProfileRepository", "Failed to fetch profile: ${e.message}")
        }
    }

    /**
     * Pushes [userDto] to the server for the locally stored email.
     * Returns a failure if no email is saved locally.
     */
    suspend fun executeServerSync(context: Context, userDto: UserDto): Result<UserDto> {
        val email = UserProfileManager.getUserEmail(context) ?: return Result.failure(Exception("No email found"))
        return userRepository.updateUser(email, userDto)
    }
}
