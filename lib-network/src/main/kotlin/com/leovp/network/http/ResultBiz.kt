@file:Suppress("unused")

package com.leovp.network.http

import com.leovp.network.http.exception.ResultException
import com.leovp.network.http.exception.business.BusinessException

private const val TAG = "ResultBiz"

/**
 * A generic class that holds a value or an exception
 */
/**
 * A generic class that holds a value or an exception
 */
sealed interface ResultBiz<out R> {
    /**
     * Successfully receive network result without error message
     * or with business logic error message.
     */
    data class Success<out T>(
        val data: T, // T & Any
        val extraData: Any? = null,
    ) : ResultBiz<T>

    /**
     * Response with business error.
     */
    data class BusinessError<out T>(val exception: BusinessException, val data: T? = null) : ResultBiz<T>

    /**
     * A special business error that needs to relogin.
     */
    data class Relogin<out T>(val exception: BusinessException, val data: T? = null) : ResultBiz<T>

    /**
     * The network encounters unexpected exception before getting a response
     * from the network such as IOException, UnKnownHostException etc.
     *
     * @param exception
     * - For response code 400~499,
     * it is the [com.leovp.network.http.exception.ResultResponseException] instance.
     * - For response code 500,
     * it is the [com.leovp.network.http.exception.ResultServerException] instance.
     * - For JSON serialize exception,
     * it is the [com.leovp.network.http.exception.ResultConvertException] instance.
     * - Other error codes, it is the [com.leovp.network.http.exception.ResultException] instance.
     */
    data class Failure(val exception: ResultException) : ResultBiz<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val isBusinessFailure: Boolean get() = this is BusinessError
    val isRelogin: Boolean get() = this is Relogin
}

fun <R, T : R> ResultBiz<T>.get(): R = (this as ResultBiz.Success<T>).data

fun <R, T : R> ResultBiz<T>.getOrDefault(defaultValue: T): R = when {
    isSuccess -> (this as ResultBiz.Success<T>).data
    else -> defaultValue
}

fun <T> ResultBiz<T>.getOrNull(): T? = when {
    isSuccess -> (this as ResultBiz.Success<T>).data
    else -> null
}

inline fun <R, T : R> ResultBiz<T>.getOrElse(onFailure: (exception: ResultException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> (this as ResultBiz.Success<T>).data
        else -> onFailure(exception)
    }

inline fun <T, R : Any> ResultBiz<T>.map(transform: (value: T) -> R): ResultBiz<R> =
    when (val exception = exceptionOrNull()) {
        null -> ResultBiz.Success(transform((this as ResultBiz.Success<T>).data))
        else -> ResultBiz.Failure(exception)
    }

fun <T> ResultBiz<T>.getOrThrow(): T = when {
    isSuccess -> (this as ResultBiz.Success<T>).data
    else -> throw exception()
}

fun <T> ResultBiz<T>.getBizErr(): ResultException? = when {
    isBusinessFailure -> (this as ResultBiz.BusinessError<T>).exception
    else -> null
}

fun <R, T : R> ResultBiz<T>.getBizErrData(): R? = (this as? ResultBiz.BusinessError<T>)?.data

fun <R, T : R> ResultBiz<T>.getReloginErrData(): R? = (this as? ResultBiz.Relogin<T>)?.data

fun <T> ResultBiz<T>.exceptionOrNull(): ResultException? = when {
    isFailure -> (this as ResultBiz.Failure).exception
    isBusinessFailure -> (this as ResultBiz.BusinessError<T>).exception
    isRelogin -> (this as ResultBiz.Relogin<T>).exception
    else -> null
}

fun <T> ResultBiz<T>.exception(): ResultException =
    when (val err: ResultException? = this.exceptionOrNull()) {
        null -> error("No exception!")
        else -> err
    }

inline fun <T> ResultBiz<T>.onSuccess(action: (value: T) -> Unit): ResultBiz<T> {
    if (isSuccess) action((this as ResultBiz.Success<T>).data)
    return this
}

inline fun <T> ResultBiz<T>.onFailure(action: (exception: ResultException) -> Unit): ResultBiz<T> {
    exceptionOrNull()?.let { action(it) }
    return this
}

inline fun <T, R> ResultBiz<T>.fold(onSuccess: (value: T) -> R, onFailure: (exception: ResultException) -> R): R =
    when (val exception = exceptionOrNull()) {
        null -> onSuccess((this as ResultBiz.Success<T>).data)
        else -> onFailure(exception)
    }
