package com.leovp.network.exception

import android.R.id.message

/**
 * Author: Michael Leo
 * Date: 2025/8/15 09:54
 */
open class ApiException(
    val code: String? = null,
    override val message: String? = null,
    override val cause: Throwable? = null,
    var tag: Any? = null,
) : Exception("$code:$message", cause)
