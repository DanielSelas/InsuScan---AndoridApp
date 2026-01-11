package com.example.insuscan.network

import com.example.insuscan.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface InsuScanApi {

    // ===== Users =====

    @POST("insuscan/users")
    suspend fun createUser(@Body newUser: NewUserDto): Response<UserDto>

    @GET("insuscan/users/login/{systemId}/{email}")
    suspend fun login(
        @Path("systemId") systemId: String,
        @Path("email") email: String
    ): Response<UserDto>

    @GET("insuscan/users/{systemId}/{email}")
    suspend fun getUser(
        @Path("systemId") systemId: String,
        @Path("email") email: String
    ): Response<UserDto>

    @PUT("insuscan/users/{systemId}/{email}")
    suspend fun updateUser(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Body user: UserDto
    ): Response<UserDto>

    // ===== Meals =====

    @POST("insuscan/meals")
    suspend fun createMeal(@Body request: CreateMealRequest): Response<MealDto>

    @GET("insuscan/meals/{systemId}/{mealId}")
    suspend fun getMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<MealDto>

    @GET("insuscan/meals/user/{systemId}/{email}")
    suspend fun getUserMeals(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): Response<List<MealDto>>

    @GET("insuscan/meals/recent/{systemId}/{email}")
    suspend fun getRecentMeals(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Query("count") count: Int = 5
    ): Response<List<MealDto>>

    @PUT("insuscan/meals/{systemId}/{mealId}/fooditems")
    suspend fun updateFoodItems(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String,
        @Body foodItems: List<FoodItemDto>
    ): Response<MealDto>

    @PUT("insuscan/meals/{systemId}/{mealId}/confirm")
    suspend fun confirmMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String,
        @Query("actualDose") actualDose: Float? = null
    ): Response<MealDto>

    @PUT("insuscan/meals/{systemId}/{mealId}/complete")
    suspend fun completeMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<MealDto>

    @DELETE("insuscan/meals/{systemId}/{mealId}")
    suspend fun deleteMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<Unit>
}