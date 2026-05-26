@file:Suppress("unused")

package com.leovp.basenetty.eventbus.util

import com.leovp.basenetty.eventbus.base.EventBusAttributes
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Author: Michael Leo
 * Date: 2021/7/26 14:07
 */
object EventBus {

    private val handlers: ConcurrentMap<String, CopyOnWriteArrayList<EventBusHandler>> =
        ConcurrentHashMap()
    private val replyHandlers: ConcurrentMap<String, EventBusHandler> =
        ConcurrentHashMap()

    /** same as `request` */
    fun send(
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null,
        handler: EventBusHandler? = null,
    ): Map<String, Any> = constructData(
        EventBusAttributes.TYPE_SEND,
        address,
        message,
        headers,
        null,
        handler
    )

    fun publish(
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null
    ): Map<String, Any> = constructData(
        EventBusAttributes.TYPE_PUBLISH, address, message, headers
    )

    /** same as `consumer` */
    fun register(
        address: String,
        headers: Map<String, Any>? = null,
        customFields: Map<String, Any?>? = null,
        handler: EventBusHandler,
    ): Map<String, Any> = constructData(
        EventBusAttributes.TYPE_REGISTER,
        address,
        null,
        headers,
        customFields,
        handler
    )

    fun unregister(address: String, headers: Map<String, Any>? = null): Map<String, Any> {
        handlers.remove(address)
        return constructData(
            EventBusAttributes.TYPE_UNREGISTER, address, null, headers
        )
    }

    // =============================================

    fun processHandlers(address: String, handle: (idx: Int, h: EventBusHandler) -> Unit) =
        handlers[address]?.forEachIndexed { idx, h -> handle(idx, h) }

    fun processReplyHandler(address: String, handle: (h: EventBusHandler) -> Unit) {
        replyHandlers.remove(address)?.let { handle(it) }
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
        handlers.computeIfAbsent(address) {
            CopyOnWriteArrayList()
        }.add(handler)
    }

    private fun addReplyHandler(address: String, handler: EventBusHandler) {
        replyHandlers.putIfAbsent(address, handler)
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
        val eventBusObj = mutableMapOf<String, Any>()
        eventBusObj[EventBusAttributes.TYPE] = type
        eventBusObj[EventBusAttributes.ADDRESS] = address
        headers?.let { eventBusObj[EventBusAttributes.HEADERS] = it }
        message?.let { eventBusObj[EventBusAttributes.BODY] = it }

        when (type) {
            EventBusAttributes.TYPE_SEND -> {
                handler?.let {
                    val replyAddress = UUID.randomUUID().toString()
                    eventBusObj[EventBusAttributes.REPLY_ADDRESS] =
                        replyAddress
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
