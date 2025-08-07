package com.leovp.basenetty.framework.client.retrystrategy

import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:14
 */
class ConstantRetry(private val maxTimes: Int = 10, private val delayInMillSec: Long = 2000L) : RetryStrategy {
    override fun getMaxTimes(): Int = maxTimes

    override fun getDelayInMillSec(currentRetryTimes: Int): Long = delayInMillSec
}
