package com.example.insuscan.network.repository

import com.example.insuscan.network.ApiConfig
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.NewUserDto
import com.example.insuscan.network.dto.UserDto

// Handles user-related API calls
class UserRepository {

    private val api = RetrofitClient.api

    suspend fun login(email: String): Result<UserDto> {
        return try {
            val response = api.login(ApiConfig.SYSTEM_ID, email)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, username: String): Result<UserDto> {
        return try {
            val newUser = NewUserDto(email = email, username = username)
            val response = api.createUser(newUser)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(email: String): Result<UserDto> {
        return try {
            val response = api.getUser(ApiConfig.SYSTEM_ID, email)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("User not found: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(email: String, user: UserDto): Result<UserDto> {
        return try {
            val response = api.updateUser(ApiConfig.SYSTEM_ID, email, user)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Update failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}