@file:Suppress("unused")

package com.leovp.network.exception

import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2025/8/14 17:55
 */
open class ApiServerException(
    message: String? = null,
    cause: Throwable? = null,
    response: Response,
    tag: Any? = null,
) : ApiResponseException(message = message, cause = cause, response = response, tag = tag)
