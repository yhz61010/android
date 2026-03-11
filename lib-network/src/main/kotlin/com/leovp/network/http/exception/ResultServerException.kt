package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:26
 */
open class ResultServerException(
    statusCode: Int? = null,
    code: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    tag: Any? = null,
) : ResultResponseException(
    statusCode = statusCode,
    code = code,
    message = message,
    cause = cause,
    tag = tag
)
