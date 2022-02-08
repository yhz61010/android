package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.toMd5
import com.leovp.androidbase.utils.cipher.MD5Util
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner


/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class MD5UtilTest {

    @Test
    fun md5String() {
        Assert.assertEquals("E10ADC3949BA59ABBE56E057F20F883E", MD5Util.encrypt("123456"))
        Assert.assertEquals("E10ADC3949BA59ABBE56E057F20F883E", "123456".toMd5(true))
    }
}