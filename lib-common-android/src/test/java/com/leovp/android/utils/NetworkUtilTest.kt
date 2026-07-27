package com.leovp.android.utils

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 *
 * Unit tests for remediation H5: ping target validation must reject argument/flag injection.
 */
class NetworkUtilTest {

    @Test
    fun `isValidPingTarget accepts ipv4 and hostnames`() {
        NetworkUtil.isValidPingTarget("8.8.8.8").shouldBeTrue()
        NetworkUtil.isValidPingTarget("example.com").shouldBeTrue()
        NetworkUtil.isValidPingTarget("a.b-c.example.com").shouldBeTrue()
    }

    @Test
    fun `isValidPingTarget rejects flag and whitespace injection`() {
        NetworkUtil.isValidPingTarget("8.8.8.8 -f").shouldBeFalse()
        NetworkUtil.isValidPingTarget("-i0.1 host").shouldBeFalse()
        NetworkUtil.isValidPingTarget("8.8.8.8; reboot").shouldBeFalse()
        NetworkUtil.isValidPingTarget("").shouldBeFalse()
    }
}
