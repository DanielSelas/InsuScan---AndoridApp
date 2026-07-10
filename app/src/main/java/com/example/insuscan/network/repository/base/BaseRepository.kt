package com.example.insuscan.network.repository.base

import com.example.insuscan.network.exception.ApiException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Base class for all repository implementations.
 *
 * Provides [safeApiCall] and [safeApiCallUnit] wrappers that translate HTTP
 * status codes and network exceptions into typed [ApiException] failures,
 * so callers never receive raw Retrofit responses.
 */
abstract class BaseRepository {

    /**
     * Executes [call] and maps the response to a [Result].
     * HTTP 2xx with a body → success; 4xx/5xx and network errors → typed [ApiException].
     */
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

                response.code() in 400..499 -> {
                    val errorBody = try {
                        response.errorBody()?.string()
                    } catch (e: Exception) {
                        null
                    }
                    Result.failure(
                        ApiException.ClientError(
                            response.code(),
                            errorBody ?: response.message()
                        )
                    )
                }

                response.code() in 500..599 ->
                    Result.failure(ApiException.ServerError(response.code(), response.message()))

                else ->
                    Result.failure(ApiException.EmptyResponse)
            }
        } catch (e: Exception) {
            Result.failure(mapThrowable(e))
        }
    }

    /**
     * Variant of [safeApiCall] for endpoints that return no body (e.g. DELETE).
     * Any 2xx response is mapped to [Result.success] of [Unit].
     */
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

                else -> {
                    val errorBody = try {
                        response.errorBody()?.string()
                    } catch (e: Exception) {
                        null
                    }
                    Result.failure(
                        ApiException.ClientError(
                            response.code(),
                            errorBody ?: response.message()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(mapThrowable(e))
        }
    }

    private fun mapThrowable(e: Throwable): ApiException = when (e) {
        is SocketTimeoutException -> ApiException.Timeout(e)
        is UnknownHostException -> ApiException.NoConnection(e)
        is java.net.ConnectException -> ApiException.NoConnection(e)
        else -> ApiException.Unknown(e)
    }
}
