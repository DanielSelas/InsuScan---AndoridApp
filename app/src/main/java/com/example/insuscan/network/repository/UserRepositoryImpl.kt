package com.example.insuscan.network.repository

import com.example.insuscan.network.ApiConfig
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.NewUserDto
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.base.BaseRepository

class UserRepositoryImpl : BaseRepository(), UserRepository {

    private val api = RetrofitClient.api

    override suspend fun login(email: String): Result<UserDto> = safeApiCall {
        api.login(ApiConfig.SYSTEM_ID, email)
    }

    override suspend fun register(email: String, username: String): Result<UserDto> = safeApiCall {
        val newUser = NewUserDto(email = email, username = username)
        api.createUser(newUser)
    }

    override suspend fun getUser(email: String): Result<UserDto> = safeApiCall {
        api.getUser(ApiConfig.SYSTEM_ID, email)
    }

    override suspend fun updateUser(email: String, user: UserDto): Result<UserDto> = safeApiCall {
        api.updateUser(ApiConfig.SYSTEM_ID, email, user)
    }
}