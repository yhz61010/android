package com.leovp.network.http.exception.business

/**
 * Author: Michael Leo
 * Date: 2025/9/29 09:37
 */
open class ReloginException(code: String, message: String, cause: Throwable? = null, tag: Any? = null,) :
    BusinessException(
        code = code,
        message = message,
        cause = cause,
        tag = tag
    )
