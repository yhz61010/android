package com.leovp.basenetty.framework.client.retrystrategy

import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy

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
