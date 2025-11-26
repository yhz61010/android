@file:Suppress("unused")

package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 17:59
 */
open class ApiSerializationException(
    message: String? = null,
    cause: Throwable? = null,
    responseBodyString: String? = null,
    tag: Any? = null,
) : ApiResponseException(
    message = message,
    cause = cause,
    responseBodyString = responseBodyString,
    tag = tag
)
