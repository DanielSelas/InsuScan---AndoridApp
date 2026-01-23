package com.example.insuscan.network.repository

import com.example.insuscan.network.dto.UserDto

interface UserRepository {
    suspend fun login(email: String): Result<UserDto>
    suspend fun register(email: String, username: String): Result<UserDto>
    suspend fun getUser(email: String): Result<UserDto>
    suspend fun updateUser(email: String, user: UserDto): Result<UserDto>
}