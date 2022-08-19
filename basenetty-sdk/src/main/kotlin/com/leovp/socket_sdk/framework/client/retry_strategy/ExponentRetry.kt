package com.leovp.socket_sdk.framework.client.retry_strategy

import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:18
 */
class ExponentRetry(private val maxTimes: Int = 5, private val base: Long = 1L) : RetryStrategy {
    override fun getMaxTimes(): Int {
        return maxTimes
    }

    override fun getDelayInMillSec(currentRetryTimes: Int): Long {
        return (base shl (currentRetryTimes - 1)) * 1000
    }
}
