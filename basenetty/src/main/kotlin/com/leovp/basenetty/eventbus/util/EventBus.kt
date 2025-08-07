@file:Suppress("unused")

package com.leovp.basenetty.eventbus.util

import com.leovp.basenetty.eventbus.base.EventBusAttributes
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Author: Michael Leo
 * Date: 2021/7/26 14:07
 */
object EventBus {
    //    private const val TAG = "eb"

    private val handlers: ConcurrentMap<String, MutableList<EventBusHandler>> = ConcurrentHashMap()
    private val replyHandlers: ConcurrentMap<String, EventBusHandler> = ConcurrentHashMap()

    /** same as `request` */
    fun send(
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null,
        handler: EventBusHandler? = null,
    ): Map<String, Any> = constructData(EventBusAttributes.TYPE_SEND, address, message, headers, null, handler)

    fun publish(address: String, message: Any? = null, headers: Map<String, Any>? = null): Map<String, Any> =
        constructData(EventBusAttributes.TYPE_PUBLISH, address, message, headers)

    /** same as `consumer` */
    fun register(
        address: String,
        headers: Map<String, Any>? = null,
        customFields: Map<String, Any?>? = null,
        handler: EventBusHandler,
    ): Map<String, Any> = constructData(EventBusAttributes.TYPE_REGISTER, address, null, headers, customFields, handler)

    fun unregister(address: String, headers: Map<String, Any>? = null): Map<String, Any> {
        handlers.remove(address)
        return constructData(EventBusAttributes.TYPE_UNREGISTER, address, null, headers)
    }

    // =============================================

    fun processHandlers(address: String, handle: (idx: Int, h: EventBusHandler) -> Unit) =
        handlers[address]?.forEachIndexed { idx, h -> handle(idx, h) }

    fun processReplyHandler(address: String, handle: (h: EventBusHandler) -> Unit) {
        replyHandlers[address]?.let {
            handle(it)
            replyHandlers.remove(address)
        }
    }

    // =============================================

    fun clearHandlers() {
        handlers.clear()
    }

    fun clearAllHandlers() {
        clearHandlers()
        replyHandlers.clear()
    }

    // =============================================

    private fun addHandler(address: String, handler: EventBusHandler) {
        val handlerList: MutableList<EventBusHandler>? = handlers[address]
        if (handlerList == null) {
            // LogContext.log.i(TAG, "[$address] Add handler to EventBus.")
            handlers[address] = mutableListOf<EventBusHandler>().apply { add(handler) }
        } else {
            if (handlers.containsKey(address)) {
                handlers[address] = handlerList.apply { add(handler) }
                // LogContext.log.i(TAG, "[$address] Replaced handler in EventBus. Current handler size: ${handlers.size}")
            }
        }
    }

    private fun addReplyHandler(address: String, handler: EventBusHandler) {
        if (!replyHandlers.containsKey(address)) {
            // LogContext.log.i(TAG, "[$address] Add reply handler to EventBus.")
            replyHandlers[address] = handler
            // LogContext.log.i(TAG, "[$address] Reply handler added.")
        }
    }

    // =============================================

    private fun constructData(
        type: String,
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null,
        customFields: Map<String, Any?>? = null,
        handler: EventBusHandler? = null,
    ): Map<String, Any> {
        // LogContext.log.i("constructData", "[$type][$address]")

        val eventBusObj = mutableMapOf<String, Any>()
        eventBusObj[EventBusAttributes.TYPE] = type
        eventBusObj[EventBusAttributes.ADDRESS] = address
        headers?.let { eventBusObj[EventBusAttributes.HEADERS] = it }
        message?.let { eventBusObj[EventBusAttributes.BODY] = it }

        when (type) {
            EventBusAttributes.TYPE_SEND -> {
                handler?.let {
                    val replyAddress = UUID.randomUUID().toString()
                    // LogContext.log.i("serializeData", "replyAddress=$replyAddress")
                    eventBusObj[EventBusAttributes.REPLY_ADDRESS] = replyAddress
                    addReplyHandler(replyAddress, it)
                }
            }

            EventBusAttributes.TYPE_REGISTER -> {
                customFields?.let { map ->
                    for ((key, value) in map) {
                        if (null != value) eventBusObj[key] = value
                    }
                }
                handler?.let { addHandler(address, it) }
            }

            else -> Unit
        }
        return eventBusObj
    }
}
