package com.leovp.network.http.exception.business

import com.leovp.network.http.exception.ResultException

/**
 * Author: Michael Leo
 * Date: 2025/9/19 10:38
 */
open class DataNotFoundException(
    message: String = "__RESPONSE_NO_DATA__",
    cause: Throwable? = null,
    tag: Any? = null,
) : ResultException(
    message = message,
    cause = cause,
    tag = tag
)
