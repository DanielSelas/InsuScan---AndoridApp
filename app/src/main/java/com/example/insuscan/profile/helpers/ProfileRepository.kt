package com.example.insuscan.profile.helpers

import android.content.Context
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager

class ProfileRepository {
    private val userRepository: UserRepository = UserRepositoryImpl()

    suspend fun fetchServerProfile(context: Context): Result<UserDto> {
        val email = UserProfileManager.getUserEmail(context) ?: return Result.failure(Exception("No email"))
        return userRepository.getUser(email).onSuccess { userDto ->
            UserProfileManager.syncFromServer(context, userDto)
            android.util.Log.d("ProfileRepository", "Profile synced from server")
        }.onFailure { e ->
            android.util.Log.e("ProfileRepository", "Failed to fetch profile: ${e.message}")
        }
    }

    suspend fun executeServerSync(context: Context, userDto: UserDto): Result<UserDto> {
        val email = UserProfileManager.getUserEmail(context) ?: return Result.failure(Exception("No email found"))
        return userRepository.updateUser(email, userDto)
    }
}
