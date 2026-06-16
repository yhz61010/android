package com.leovp.basenetty.eventbus.handler

/**
 * Author: Michael Leo
 * Date: 2021/8/5 13:12
 */
fun interface EventBusHandler {
    fun handle(message: Any?)
}
