package com.example.insuscan.network

import com.example.insuscan.network.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface InsuScanApi {

    // ===== Users =====

    @POST("users")
    suspend fun createUser(@Body newUser: NewUserDto): Response<UserDto>

    @GET("users/login/{systemId}/{email}")
    suspend fun login(
        @Path("systemId") systemId: String,
        @Path("email") email: String
    ): Response<UserDto>

    @GET("users/{systemId}/{email}")
    suspend fun getUser(
        @Path("systemId") systemId: String,
        @Path("email") email: String
    ): Response<UserDto>

    @PUT("users/{systemId}/{email}")
    suspend fun updateUser(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Body user: UserDto
    ): Response<UserDto>

    // ===== Meals =====

    @POST("meals")
    suspend fun createMeal(@Body request: CreateMealRequest): Response<MealDto>

    @POST("meals/{systemId}/{email}/save-scanned")
    suspend fun saveScannedMeal(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Body meal: MealDto
    ): Response<MealDto>

    @GET("meals/{systemId}/{mealId}")
    suspend fun getMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<MealDto>

    @GET("meals/user/{systemId}/{email}")
    suspend fun getUserMeals(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): Response<List<MealDto>>

    @GET("meals/recent/{systemId}/{email}")
    suspend fun getRecentMeals(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Query("count") count: Int = 5
    ): Response<List<MealDto>>

    @PUT("meals/{systemId}/{mealId}/fooditems")
    suspend fun updateFoodItems(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String,
        @Body foodItems: List<FoodItemDto>
    ): Response<MealDto>

    @PUT("meals/{systemId}/{mealId}/confirm")
    suspend fun confirmMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String,
        @Query("actualDose") actualDose: Float? = null
    ): Response<MealDto>

    @PUT("meals/{systemId}/{mealId}/complete")
    suspend fun completeMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<MealDto>

    @DELETE("meals/{systemId}/{mealId}")
    suspend fun deleteMeal(
        @Path("systemId") systemId: String,
        @Path("mealId") mealId: String
    ): Response<Unit>

    @GET("meals/user/{systemId}/{email}/by-date")
    suspend fun getMealsByDate(
        @Path("systemId") systemId: String,
        @Path("email") email: String,
        @Query("from") fromDate: String, // yyyy-MM-dd
        @Query("to") toDate: String,     // yyyy-MM-dd
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): Response<List<MealDto>>

    // ===== Vision Analysis =====

    @Multipart
    @POST("vision/analyze")
    suspend fun analyzeImage(
        @Part file: MultipartBody.Part,
        @Query("email") email: String,
        @Query("estimatedWeightGrams") estimatedWeightGrams: Float? = null,
        @Query("volumeCm3") volumeCm3: Float? = null,
        @Query("portionConfidence") portionConfidence: Float? = null
    ): Response<MealDto>

    // Search USDA food database
    @GET("food/search")
    suspend fun searchFood(
        @Query("query") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<FoodSearchResultDto>>
    
    // AI-enhanced food search with intelligent ranking
    @POST("food/ai-search")
    suspend fun aiSearchFood(
        @Body request: AiSearchRequestDto
    ): Response<AiSearchResponseDto>
}