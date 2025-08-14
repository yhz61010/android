@file:Suppress("unused")

package com.leovp.network.http

import com.drake.net.exception.RequestParamsException
import com.drake.net.exception.ServerResponseException
import com.leovp.network.exception.ApiException
import com.leovp.network.exception.ApiResponseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * A generic class that holds a value or an exception
 */
sealed interface Result<out R> {
    /**
     * Successfully receive network result without error message
     * or with business logic error message.
     */
    data class Success<out T>(val data: T) : Result<T>

    /**
     * The network encounters unexpected exception before getting a response
     * from the network such as IOException, UnKnownHostException and etc.
     *
     * @param exception For response code 400~499 or 500,
     * the _exception_ is the _ApiResponseException_ instance.
     * Other error codes, the it is the _ApiException_ instance.
     */
    data class Failure(val exception: ApiException) : Result<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val jsonDecoder = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
            coerceInputValues = false
        }
    }
}

fun <R, T : R> Result<T>.get(): R = (this as Result.Success<T>).data

fun <R, T : R> Result<T>.getOrDefault(defaultValue: T): R = when {
    isFailure -> defaultValue
    else -> (this as Result.Success<T>).data
}

fun <T> Result<T>.getOrNull(): T? = when {
    isFailure -> null
    else -> (this as Result.Success<T>).data
}

fun <T> Result<T>.getOrThrow(): T = when {
    isFailure -> throw (this as Result.Failure).exception
    else -> (this as Result.Success<T>).data
}

inline fun <R, T : R> Result<T>.getOrElse(onFailure: (exception: ApiException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> (this as Result.Success<T>).data
        else -> onFailure(exception)
    }

inline fun <T, R> Result<T>.map(transform: (value: T) -> R): Result<R> = when (val exception = exceptionOrNull()) {
    null -> Result.Success(transform((this as Result.Success<T>).data))
    else -> Result.Failure(exception)
}

fun <T> Result<T>.exceptionOrNull(): ApiException? = when {
    isFailure -> (this as Result.Failure).exception
    else -> null
}

fun <T> Result<T>.exception(): ApiException = (this as Result.Failure).exception

inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (isSuccess) action((this as Result.Success<T>).data)
    return this
}

inline fun <T> Result<T>.onFailure(action: (exception: ApiException) -> Unit): Result<T> {
    exceptionOrNull()?.let { action(it) }
    return this
}

inline fun <T, R> Result<T>.fold(onSuccess: (value: T) -> R, onFailure: (exception: ApiException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> onSuccess((this as Result.Success<T>).data)
        else -> onFailure(exception)
    }

// ----------

/**
 * Wrap the [block] result into [Result].
 *
 * Note that, this function is very useful when you wrap your http request block.
 * In this project, it wraps http request which is implemented by _Net_.
 *
 * If the [Result] is failure, the [ApiException] will be used as [Result.Failure] parameter.
 *
 * @param dispatcher The dispatcher for suspend [block] function. [Dispatchers.Main] by default.
 */
suspend inline fun <reified R> result(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    crossinline block: suspend CoroutineScope.() -> R,
): Result<R> = supervisorScope {
    runCatching {
        Result.Success(withContext(dispatcher) { block() })
    }.getOrElse { err ->

        // err can be one of the following exception:
        // - RequestParamsException
        // - ServerResponseException
        // - ConvertException
        // All above exceptions are inherited from HttpResponseException.

        when (err) {
            // 400~499 || 500
            is RequestParamsException, is ServerResponseException -> {
                // val bodyString = err.response.body.string()
                // runCatching {
                //     val errorData =
                //         Result.jsonDecoder.decodeFromString<R>(bodyString)
                // }.onFailure {
                //     // SerializationException
                //     // IllegalArgumentException
                //     message = it.message
                // }
                Result.Failure(ApiResponseException(message = err.message, cause = err, response = err.response))
            }

            else -> Result.Failure(ApiException(message = err.message, cause = err))
        }
    }
}
