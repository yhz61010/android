package com.leovp.androidbase

import com.leovp.androidbase.exts.kotlin.formatToNormalFullDateTime
import com.leovp.androidbase.exts.kotlin.formatToNormalServerFullDateTime
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
class CalendarUtilTest {

    @Test
    fun test() {
        val staticDate = Calendar.getInstance().apply {
            set(2021, 9, 13, 16, 28, 46)
        }
        var dateString = Date(staticDate.timeInMillis).formatToNormalServerFullDateTime()
        assertEquals("2021-10-13 16:28:46 (CST)", dateString)

        dateString = Date(staticDate.timeInMillis).formatToNormalFullDateTime()
        assertEquals("2021/10/13 16:28:46 (CST)", dateString)
    }
}
