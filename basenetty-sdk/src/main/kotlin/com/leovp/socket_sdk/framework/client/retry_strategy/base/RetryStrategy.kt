package com.leovp.socket_sdk.framework.client.retry_strategy.base

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:14
 */
interface RetryStrategy {
    fun getMaxTimes(): Int

    /**
     * @param currentRetryTimes Start at 1
     */
    fun getDelayInMillSec(currentRetryTimes: Int): Long
}
