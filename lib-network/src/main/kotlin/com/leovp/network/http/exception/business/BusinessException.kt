package com.leovp.network.http.exception.business

import com.leovp.network.http.exception.ResultException

/**
 * Author: Michael Leo
 * Date: 2025/8/29 14:59
 */
open class BusinessException(code: String, message: String, cause: Throwable? = null, tag: Any? = null,) :
    ResultException(
        code = code,
        message = message,
        cause = cause,
        tag = tag
    )
