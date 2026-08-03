package com.leovp.kotlin.exts

import kotlin.test.assertIs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Author: Michael Leo
 * Date: 2022/5/30 09:22
 */
class NumericExtUnitTest {

    @Test
    fun gcdTest() {
        assertEquals(0, gcd(0, 0))

        assertIs<IllegalArgumentException>(assertThrows<IllegalArgumentException> { gcd(-1, 1) })
        assertIs<IllegalArgumentException>(assertThrows<IllegalArgumentException> { gcd(1, -1) })
        assertIs<IllegalArgumentException>(assertThrows<IllegalArgumentException> { gcd(0, -1) })
        assertIs<IllegalArgumentException>(assertThrows<IllegalArgumentException> { gcd(-1, 0) })

        assertEquals(1, gcd(0, 1))
        assertEquals(1, gcd(1, 0))

        assertEquals(5, gcd(0, 5))
        assertEquals(5, gcd(5, 0))

        assertEquals(1, gcd(1, 5))
        assertEquals(1, gcd(5, 1))

        assertEquals(1, gcd(2, 5))
        assertEquals(1, gcd(5, 2))

        assertEquals(1, gcd(1, 1))
        assertEquals(5, gcd(5, 5))

        assertEquals(5, gcd(15, 5))
        assertEquals(5, gcd(5, 15))

        assertEquals(3, gcd(15, 3))
        assertEquals(3, gcd(3, 15))

        assertEquals(5, gcd(15, 25))
        assertEquals(5, gcd(25, 15))

        assertEquals(256, gcd(1024, 768))
        assertEquals(256, gcd(768, 1024))
    }

    @Test
    fun getRatioTest() {
        assertEquals("4:3", getRatio(1024, 768))
        assertEquals("3:4", getRatio(768, 1024))

        assertEquals("9/16", getRatio(1080, 1920, "/"))
        assertEquals("16/9", getRatio(1080, 1920, "/", true))
        assertEquals("16/9", getRatio(1920, 1080, "/"))

        assertEquals("2:3", getRatio(1280, 1920))
        assertEquals("3:2", getRatio(1280, 1920, swapResult = true))
        assertEquals("3:2", getRatio(1920, 1280))

        assertEquals("37:18", getRatio(2960, 1440))

        assertNull(getRatio(0, 0))

        assertNull(getRatio(0, -1))
        assertNull(getRatio(-1, 0))

        assertNull(getRatio(1, -1))
        assertNull(getRatio(-1, 1))

        assertEquals("0:1", getRatio(0, 1))
        assertEquals("1:0", getRatio(1, 0))

        assertEquals("0:1", getRatio(0, 5))
        assertEquals("1:0", getRatio(5, 0))

        assertEquals("1:1", getRatio(1, 1))
        assertEquals("1:1", getRatio(5, 5))

        assertEquals("1:5", getRatio(1, 5))
        assertEquals("5:1", getRatio(5, 1))

        assertEquals("13:17", getRatio(13, 17))
    }

    @Test
    fun `formatDecimalSeparator groups positives`() {
        assertEquals("0", 0.formatDecimalSeparator())
        assertEquals("999", 999.formatDecimalSeparator())
        assertEquals("1,000", 1000.formatDecimalSeparator())
        assertEquals("1,234,567", 1234567.formatDecimalSeparator())
        assertEquals("1,234,567,890", 1234567890L.formatDecimalSeparator())
    }

    @Test
    fun `formatDecimalSeparator handles negatives without misplacing the sign`() {
        // Regression for remediation H14: the sign used to be chunked into its own group,
        // producing "-,100" / "-,123,456" when the digit count was a multiple of three.
        assertEquals("-100", (-100).formatDecimalSeparator())
        assertEquals("-1,234", (-1234).formatDecimalSeparator())
        assertEquals("-123,456", (-123456).formatDecimalSeparator())
    }

    @Test
    fun `formatDecimalSeparator handles MIN_VALUE without abs overflow`() {
        // abs(Long.MIN_VALUE) overflows and stays negative; the string-strip path must still work.
        assertEquals("-9,223,372,036,854,775,808", Long.MIN_VALUE.formatDecimalSeparator())
        assertEquals("-2,147,483,648", Int.MIN_VALUE.formatDecimalSeparator())
    }
}
