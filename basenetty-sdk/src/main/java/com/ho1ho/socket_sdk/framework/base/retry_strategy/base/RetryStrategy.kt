package com.ho1ho.socket_sdk.framework.base.retry_strategy.base

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:14
 */
interface RetryStrategy {
    fun getMaxTimes(): Int
    fun getDelayInMillSec(): Long
}