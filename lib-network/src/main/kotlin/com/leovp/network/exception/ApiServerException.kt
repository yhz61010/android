@file:Suppress("unused")

package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 17:55
 */
open class ApiServerException(
    statusCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
    tag: Any? = null,
) : ApiResponseException(
    statusCode = statusCode,
    message = message,
    cause = cause,
    tag = tag
)
