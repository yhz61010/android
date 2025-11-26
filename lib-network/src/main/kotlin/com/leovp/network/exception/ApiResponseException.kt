@file:Suppress("unused")

package com.leovp.network.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ApiResponseException(
    val statusCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
    val responseBodyString: String? = null,
    tag: Any? = null,
) : ApiException(message = message, cause = cause, tag = tag)
