package com.leovp.network.http.exception.business

import com.leovp.network.http.exception.ResultException

/**
 * Author: Michael Leo
 * Date: 2025/9/29 16:22
 */
open class EmptyResponseException(message: String = "__RESPONSE_EMPTY__", cause: Throwable? = null, tag: Any? = null,) :
    ResultException(
        message = message,
        cause = cause,
        tag = tag
    )
