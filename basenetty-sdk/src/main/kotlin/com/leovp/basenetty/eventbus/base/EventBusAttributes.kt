package com.leovp.basenetty.eventbus.base

/**
 * Author: Michael Leo
 * Date: 2021/7/22 17:16
 */
object EventBusAttributes {
    const val ADDRESS = "address"
    const val REPLY_ADDRESS = "replyAddress"
    const val HEADERS = "headers"
    const val BODY = "body"
    const val TYPE = "type"

    const val TYPE_SEND = "send"
    const val TYPE_PUBLISH = "publish"
    const val TYPE_REGISTER = "register"
    const val TYPE_UNREGISTER = "unregister"
}
