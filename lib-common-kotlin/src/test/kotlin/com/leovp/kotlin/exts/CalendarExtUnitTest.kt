package com.leovp.kotlin.exts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
  * Author: Michael Leo
  * Date: 2024/8/29 15:17
  */
class CalendarExtUnitTest {

    @Test
    fun formatTest() {
        assertEquals("00:03:29", 209000L.formatTimestamp())
        assertEquals("03:29", 209000L.formatTimestampShort())

        assertEquals("03:23:29", 12209000L.formatTimestamp())
        assertEquals("203:29", 12209000L.formatTimestampShort())
    }
}
