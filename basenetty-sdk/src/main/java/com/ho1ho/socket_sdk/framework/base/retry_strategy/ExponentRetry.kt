package com.ho1ho.socket_sdk.framework.base.retry_strategy

import com.ho1ho.socket_sdk.framework.base.retry_strategy.base.RetryStrategy
import java.util.concurrent.atomic.AtomicInteger

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:18
 */
class ExponentRetry(private val maxTimes: Int = 10, private val base: Long = 1L) : RetryStrategy {
    private val currentRetryTimes = AtomicInteger(0)

    override fun getMaxTimes(): Int {
        return maxTimes
    }

    override fun getDelayInMillSec(): Long {
        return base shl currentRetryTimes.getAndIncrement()
    }
}