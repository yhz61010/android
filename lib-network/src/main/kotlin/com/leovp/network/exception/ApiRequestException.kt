@file:Suppress("unused")

package com.leovp.network.exception

import okhttp3.Request

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ApiRequestException(
    message: String? = null,
    cause: Throwable? = null,
    val request: Request,
    tag: Any? = null,
) : ApiException(message, cause, tag)
