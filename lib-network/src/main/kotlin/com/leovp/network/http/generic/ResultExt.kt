@file:Suppress("unused")

package com.leovp.network.http.generic

import com.leovp.network.exception.ApiException
import com.leovp.network.exception.ApiResponseException
import com.leovp.network.exception.ApiSerializationException
import com.leovp.network.exception.ApiServerException
import com.leovp.network.http.Result
import com.leovp.network.http.net.converters.SerializationConverter
import com.leovp.network.http.exception.ResultConvertException
import com.leovp.network.http.exception.ResultException
import com.leovp.network.http.exception.ResultResponseException
import com.leovp.network.http.exception.ResultServerException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * Author: Michael Leo
 * Date: 2025/8/14 17:27
 */

suspend inline fun <reified R> result(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    crossinline block: suspend CoroutineScope.() -> R,
): Result<R> = supervisorScope {
    /**
     * **Attention**
     *
     * This method will wrap http request which is implemented by your custom http implementation.
     *
     * In your custom http implementation, you must throw specific exception which is listed below:
     * - For 4xx response code or converting exception, it is the [ResultResponseException] instance.
     * - For 500 response code, it is the [ApiServerException] instance.
     * - For json serialize exception, it is the [ApiSerializationException] instance.
     * - For unexpected exception, it is the [ApiException] instance.
     *
     * Wrap the [block] result into [com.leovp.network.http.Result].
     *
     * Note that, this function is very useful when you wrap your http request block.
     *
     * If the [com.leovp.network.http.Result] is failure, the [com.leovp.network.http.Result.Failure] parameter will be:
     * - For 4xx response code or converting exception, it is the [ApiResponseException] instance.
     * - For 500 response code, it is the [ResultServerException] instance.
     * - For json serialize exception, it is the [ResultConvertException] instance.
     * - For unexpected exception, it is the [ResultException] instance.
     *
     * @param dispatcher The dispatcher for suspend [block] function. [Dispatchers.Main] by default.
     */
    runCatching {
        Result.Success(withContext(dispatcher) { block() })
    }.getOrElse { err ->

        // err can be one of the following exception:
        // - ApiResponseException (400~499)
        // - ApiServerException (500)
        // - ApiSerializationException
        // All above exceptions are inherited from ApiException.

        when (err) {
            // 500
            is ApiServerException -> {
                Result.Failure(
                    ResultServerException(
                        message = err.message,
                        cause = err.cause,
                        response = err.response,
                        tag = err.tag
                    )
                )
            }

            // JSON converting exception
            is ApiSerializationException -> {
                Result.Failure(
                    ResultConvertException(
                        message = err.message,
                        cause = err.cause,
                        response = err.response,
                        tag = err.tag
                    )
                )
            }

            // 400~499
            is ApiResponseException -> {
                val errorData: R? = runCatching {
                    val bodyString = err.response.body.string()
                    SerializationConverter.jsonDecoder.decodeFromString<R>(bodyString)
                }.getOrElse {
                    // - SerializationException
                    // - IllegalArgumentException
                    null
                }

                Result.Failure(
                    ResultResponseException(
                        message = err.message,
                        cause = err,
                        response = err.response,
                        tag = errorData
                    )
                )
            }

            else -> Result.Failure(ResultException(message = err.message, cause = err))
        }
    }
}
