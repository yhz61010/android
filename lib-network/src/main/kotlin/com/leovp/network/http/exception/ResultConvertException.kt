package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:41
 */
open class ResultConvertException(
    statusCode: Int? = null,
    code: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    responseBodyString: String? = null,
    tag: Any? = null,
) : ResultResponseException(
    statusCode = statusCode,
    code = code,
    message = message,
    cause = cause,
    responseBodyString = responseBodyString,
    tag = tag
)
