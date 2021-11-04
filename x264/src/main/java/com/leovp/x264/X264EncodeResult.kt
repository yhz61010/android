package com.leovp.x264

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
@Keep
data class X264EncodeResult(val err: Int, val data: ByteArray, val pts: Long, val isKey: Boolean)