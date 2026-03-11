package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ResultResponseException(
    val statusCode: Int? = null,
    code: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    val responseBodyString: String? = null,
    tag: Any? = null,
) : ResultException(
    code = code,
    message = message,
    cause = cause,
    tag = tag
)
