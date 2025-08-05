package com.leovp.mvvm.utils

import com.leovp.log.LLog
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/9/4 14:37
 */

fun previewInitLog() {
    LogContext.setLogImpl(LLog("AOS"))
}
