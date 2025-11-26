package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:41
 */
open class ResultConvertException(
    message: String? = null,
    cause: Throwable? = null,
    responseBodyString: String? = null,
    tag: Any? = null,
) : ResultResponseException(
    message = message,
    cause = cause,
    responseBodyString = responseBodyString,
    tag = tag
)
