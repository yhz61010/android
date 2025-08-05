package com.leovp.mvvm.exception

/**
 * Author: Michael Leo
 * Date: 2023/9/13 16:22
 */
class ApiException(
    val code: Int = 0,
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception("$code:$message", cause)
