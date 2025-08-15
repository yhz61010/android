package com.leovp.network.http.exception

import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:26
 */
open class ResultServerException(
    message: String? = null,
    cause: Throwable? = null,
    response: Response,
    tag: Any? = null,
) : ResultResponseException(message = message, cause = cause, response = response, tag = tag)
