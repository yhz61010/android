package com.leovp.network.http.exception

import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ResultResponseException(
    message: String? = null,
    cause: Throwable? = null,
    val response: Response,
    tag: Any? = null,
) : ResultException(message = message, cause = cause, tag = tag)
