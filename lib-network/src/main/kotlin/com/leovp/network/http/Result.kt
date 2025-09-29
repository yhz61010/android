@file:Suppress("unused")

package com.leovp.network.http

import com.leovp.network.http.exception.ResultConvertException
import com.leovp.network.http.exception.ResultException

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
     * @param exception
     * - For response code 400~499,
     * it is the [com.leovp.network.http.exception.ResultResponseException] instance.
     * - For response code 500,
     * it is the [com.leovp.network.http.exception.ResultServerException] instance.
     * - For json serialize exception,
     * it is the [com.leovp.network.http.exception.ResultConvertException] instance.
     * - Other error codes, it is the [com.leovp.network.http.exception.ResultException] instance.
     */
    data class Failure(val exception: ResultException) : Result<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
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

inline fun <R, T : R> Result<T>.getOrElse(onFailure: (exception: ResultException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> (this as Result.Success<T>).data
        else -> onFailure(exception)
    }

inline fun <T, R> Result<T>.map(transform: (value: T) -> R): Result<R> = when (val exception = exceptionOrNull()) {
    null -> Result.Success(transform((this as Result.Success<T>).data))
    else -> Result.Failure(exception)
}

fun <T> Result<T>.exceptionOrNull(): ResultException? = when {
    isFailure -> (this as Result.Failure).exception
    else -> null
}

fun <T> Result<T>.exception(): ResultException = (this as Result.Failure).exception

inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (isSuccess) action((this as Result.Success<T>).data)
    return this
}

inline fun <T> Result<T>.onFailure(action: (exception: ResultException) -> Unit): Result<T> {
    exceptionOrNull()?.let { action(it) }
    return this
}

inline fun <T, R> Result<T>.fold(onSuccess: (value: T) -> R, onFailure: (exception: ResultException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> onSuccess((this as Result.Success<T>).data)
        else -> onFailure(exception)
    }
