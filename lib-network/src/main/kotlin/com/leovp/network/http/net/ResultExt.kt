@file:Suppress("unused")

package com.leovp.network.http.net

import com.drake.net.exception.ConvertException
import com.drake.net.exception.RequestParamsException
import com.drake.net.exception.ServerResponseException
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

/**
 * **Attention**
 *
 * This method will wrap http request which is implemented by _Net_.
 *
 * Wrap the [block] result into [com.leovp.network.http.Result].
 *
 * Note that, this function is very useful when you wrap your http request block.
 *
 * If the [com.leovp.network.http.Result] is failure, the [com.leovp.network.http.Result.Failure] parameter will be:
 * - For 4xx response code or converting exception, it is the [ResultResponseException] instance.
 * - For 500 response code, it is the [ResultServerException] instance.
 * - For json serialize exception, it is the [ResultConvertException] instance.
 * - For unexpected exception, it is the [ResultException] instance.
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
        // - RequestParamsException (400~499)
        // - ServerResponseException (500)
        // - ConvertException
        // All above exceptions are inherited from HttpResponseException.

        when (err) {
            // 400~499
            is RequestParamsException -> {
                val errorData: R? = runCatching {
                    val bodyString = err.response.body.string()
                    SerializationConverter.defaultJson.decodeFromString<R>(bodyString)
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

            // 500
            is ServerResponseException -> {
                Result.Failure(ResultServerException(message = err.message, cause = err, response = err.response))
            }

            // JSON convert exception
            is ConvertException -> {
                Result.Failure(ResultConvertException(message = err.message, cause = err, response = err.response))
            }

            else -> Result.Failure(ResultException(message = err.message, cause = err))
        }
    }
}
