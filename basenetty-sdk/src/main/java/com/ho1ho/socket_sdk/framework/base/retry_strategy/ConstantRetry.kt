package com.ho1ho.socket_sdk.framework.base.retry_strategy

import com.ho1ho.socket_sdk.framework.base.retry_strategy.base.RetryStrategy

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:14
 */
class ConstantRetry(private val maxTimes: Int = 10, private val delayInMillSec: Long = 2000L) : RetryStrategy {
    override fun getMaxTimes(): Int {
        return maxTimes
    }

    override fun getDelayInMillSec(currentRetryTimes: Int): Long {
        return delayInMillSec
    }
}