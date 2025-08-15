package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2023/9/13 16:22
 */
open class ResultException(message: String? = null, cause: Throwable? = null, var tag: Any? = null) :
    Exception(message, cause)
