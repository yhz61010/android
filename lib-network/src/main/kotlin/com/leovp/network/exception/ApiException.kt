package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:54
 */
open class ApiException(message: String? = null, cause: Throwable? = null, var tag: Any? = null) :
    Exception(message, cause)
