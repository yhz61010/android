package com.leovp.basenetty

import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.ExponentRetry
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class RetryStrategyTest {

    @Test
    fun `ConstantRetry returns fixed delay`() {
        val retry = ConstantRetry(maxTimes = 5, delayInMillSec = 3000L)
        retry.getMaxTimes().shouldBeEqualTo(5)
        retry.getDelayInMillSec(1).shouldBeEqualTo(3000L)
        retry.getDelayInMillSec(3).shouldBeEqualTo(3000L)
        retry.getDelayInMillSec(5).shouldBeEqualTo(3000L)
    }

    @Test
    fun `ConstantRetry default values`() {
        val retry = ConstantRetry()
        retry.getMaxTimes().shouldBeEqualTo(10)
        retry.getDelayInMillSec(1).shouldBeEqualTo(2000L)
    }

    @Test
    fun `ExponentRetry returns exponential delay`() {
        val retry = ExponentRetry(maxTimes = 5, base = 1L)
        retry.getMaxTimes().shouldBeEqualTo(5)
        retry.getDelayInMillSec(1).shouldBeEqualTo(1000L)
        retry.getDelayInMillSec(2).shouldBeEqualTo(2000L)
        retry.getDelayInMillSec(3).shouldBeEqualTo(4000L)
        retry.getDelayInMillSec(4).shouldBeEqualTo(8000L)
        retry.getDelayInMillSec(5).shouldBeEqualTo(16000L)
    }

    @Test
    fun `ExponentRetry with base 2`() {
        val retry = ExponentRetry(maxTimes = 3, base = 2L)
        retry.getDelayInMillSec(1).shouldBeEqualTo(2000L)
        retry.getDelayInMillSec(2).shouldBeEqualTo(4000L)
        retry.getDelayInMillSec(3).shouldBeEqualTo(8000L)
    }

    @Test
    fun `ExponentRetry default values`() {
        val retry = ExponentRetry()
        retry.getMaxTimes().shouldBeEqualTo(5)
        retry.getDelayInMillSec(1).shouldBeEqualTo(1000L)
    }
}
