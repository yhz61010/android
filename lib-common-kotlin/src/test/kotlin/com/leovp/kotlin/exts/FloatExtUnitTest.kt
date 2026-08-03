package com.leovp.kotlin.exts

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Author: Michael Leo
 *
 * Unit tests for remediation H15: [round] must not depend on the default locale's decimal separator
 * and must tolerate non-finite inputs instead of throwing.
 */
class FloatExtUnitTest {

    @Test
    fun `round works for normal doubles`() {
        assertEquals(3.14, 3.14159.round(2), 0.0)
        assertEquals(3.1, 3.14.round(1), 0.0)
        assertEquals(10.0, 9.999.round(2), 0.0)
    }

    @Test
    fun `round formats with English separators regardless of default locale`() {
        val original = Locale.getDefault()
        try {
            // Under GERMANY the decimal separator is ',', which used to make toDouble() throw (H15).
            Locale.setDefault(Locale.GERMANY)
            assertEquals(3.1, 3.14.round(1), 0.0)
            assertEquals(3.14, 3.14159.round(2), 0.0)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `round returns non-finite values unchanged`() {
        assertTrue(Double.POSITIVE_INFINITY.round().isInfinite())
        assertTrue(Double.NEGATIVE_INFINITY.round().isInfinite())
        assertTrue(Double.NaN.round().isNaN())
    }

    @Test
    fun `round rejects negative precision`() {
        // remediation M-K3: an invalid precision must fail fast with a clear message.
        assertThrows<IllegalArgumentException> { 3.14.round(-1) }
    }
}
