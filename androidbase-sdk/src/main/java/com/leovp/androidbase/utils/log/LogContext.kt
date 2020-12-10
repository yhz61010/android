package com.leovp.androidbase.utils.log

import com.leovp.androidbase.utils.log.base.ILog

/**
 * Author: Michael Leo
 * Date: 2020/10/16 下午6:15
 */
object LogContext {
    var enableLog = true
        set(value) {
            field = value
            if (!::log.isInitialized) throw IllegalAccessException("You must call setLogImp() first")
            log.enableLog = value
        }

    lateinit var log: ILog
        private set

    fun setLogImp(log: ILog) {
        this.log = log
    }
}