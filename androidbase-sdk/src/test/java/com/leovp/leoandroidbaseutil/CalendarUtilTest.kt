package com.leovp.leoandroidbaseutil

import android.util.Log
import com.leovp.androidbase.exts.kotlin.formatToNormalFullDateTime
import com.leovp.androidbase.exts.kotlin.formatToNormalServerFullDateTime
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*


/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class CalendarUtilTest {

    @Test
    fun test() {
        val staticDate = Calendar.getInstance().apply {
            set(2021, 9, 13, 16, 28, 46)
        }
        var dateString = Date(staticDate.timeInMillis).formatToNormalServerFullDateTime()
        Assert.assertEquals("2021-10-13 16:28:46 (CST)", dateString)

        dateString = Date(staticDate.timeInMillis).formatToNormalFullDateTime()
        Assert.assertEquals("2021/10/13 16:28:46 (CST)", dateString)
    }

}