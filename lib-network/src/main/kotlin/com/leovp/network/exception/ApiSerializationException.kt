@file:Suppress("unused")

package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 17:59
 */
open class ApiSerializationException(
    code: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    responseBodyString: String? = null,
    tag: Any? = null,
) : ApiResponseException(
    code = code,
    message = message,
    cause = cause,
    responseBodyString = responseBodyString,
    tag = tag
)
