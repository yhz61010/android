package com.leovp.network.http.exception

/**
 * Author: Michael Leo
 * Date: 2025/8/14 13:26
 */
open class ResultResponseException(
    val statusCode: Int? = null,
    code: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    val responseBodyString: String? = null,
    tag: Any? = null,
) : ResultException(
    code = code,
    message = message,
    cause = cause,
    tag = tag
) {
    /**
     * Decoded response error payload, kept separate from caller-owned [tag].
     * Migration: older result helpers stored the decoded payload in [tag]. Read [errorData]
     * for that payload after upgrading; [tag] now retains the original request tag.
     */
    var errorData: Any? = null
        private set

    @PublishedApi
    @JvmSynthetic
    internal fun attachErrorData(value: Any?) {
        errorData = value
    }
}
