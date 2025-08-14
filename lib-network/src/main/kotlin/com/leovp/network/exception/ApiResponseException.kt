@file:Suppress("unused")

package com.leovp.network.exception

import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ApiResponseException(
    message: String? = null,
    cause: Throwable? = null,
    val response: Response,
    tag: Any? = null,
) : ApiException(message, cause, tag)
