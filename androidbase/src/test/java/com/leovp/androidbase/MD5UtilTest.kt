package com.leovp.androidbase

import com.leovp.androidbase.exts.kotlin.toMd5
import com.leovp.androidbase.utils.cipher.MD5Util
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
class MD5UtilTest {
    @Test
    fun md5String() {
        assertEquals("E10ADC3949BA59ABBE56E057F20F883E", MD5Util.encrypt("123456"))
        assertEquals("E10ADC3949BA59ABBE56E057F20F883E", "123456".toMd5(true))
    }
}
