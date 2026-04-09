package com.example.insuscan.network.repository.base

import com.example.insuscan.network.exception.ApiException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

abstract class BaseRepository {

    // Standard API call wrapper — maps HTTP codes and network errors to ApiException
    protected suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            when {
                response.isSuccessful && response.body() != null ->
                    Result.success(response.body()!!)
                response.code() == 401 ->
                    Result.failure(ApiException.Unauthorized)
                response.code() == 404 ->
                    Result.failure(ApiException.NotFound())
                response.code() in 400..499 ->
                    Result.failure(ApiException.ClientError(response.code(), response.message()))
                response.code() in 500..599 ->
                    Result.failure(ApiException.ServerError(response.code(), response.message()))
                else ->
                    Result.failure(ApiException.EmptyResponse)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiException.Timeout(e))
        } catch (e: UnknownHostException) {
            Result.failure(ApiException.NoConnection(e))
        } catch (e: Exception) {
            Result.failure(ApiException.Unknown(e))
        }
    }

    // For endpoints that return no body
    protected suspend fun safeApiCallUnit(call: suspend () -> Response<*>): Result<Unit> {
        return try {
            val response = call()
            when {
                response.isSuccessful ->
                    Result.success(Unit)
                response.code() == 401 ->
                    Result.failure(ApiException.Unauthorized)
                response.code() in 500..599 ->
                    Result.failure(ApiException.ServerError(response.code(), response.message()))
                else ->
                    Result.failure(ApiException.ClientError(response.code(), response.message()))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiException.Timeout(e))
        } catch (e: UnknownHostException) {
            Result.failure(ApiException.NoConnection(e))
        } catch (e: Exception) {
            Result.failure(ApiException.Unknown(e))
        }
    }
}
