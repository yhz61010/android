package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2023/9/13 16:22
 */
open class ApiException(message: String? = null, cause: Throwable? = null, var tag: Any? = null) :
    Exception(message, cause)
