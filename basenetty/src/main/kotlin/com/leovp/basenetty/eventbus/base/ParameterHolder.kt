package com.leovp.basenetty.eventbus.base

import androidx.annotation.Keep
import com.leovp.basenetty.eventbus.handler.EventBusHandler

/**
 * Author: Michael Leo
 * Date: 2022/8/19 17:21
 */
@Keep
data class ParameterHolder(
    val type: String
) {
    lateinit var address: String
    var message: Any? = null
    var headers: Map<String, Any>? = null
    var customFields: Map<String, Any?>? = null
    var handler: EventBusHandler? = null
}
