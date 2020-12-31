package com.leovp.socket_sdk.framework.base

import io.netty.channel.ChannelHandlerContext

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:53
 */
abstract class BaseNetty

interface ReadSocketDataListener<in T> {
    fun onReceivedData(ctx: ChannelHandlerContext, msg: T)
}

enum class ClientConnectState {
    /**
     * This is the connection default status after initializing netty client.
     *
     * Only you release socket, it will be in this status.
     * In this status, you can not reconnect again. You must create netty client again.
     */
    UNINITIALIZED,

    /**
     * **PRIVATE status** for library.
     * **DO NOT** use it in your codes
     *
     * Because of [CONNECTED] status is an asynchronous status, when user do connect to server successively,
     * it will create more than one connection than you expect. So we need this status to tell user you're
     * doing a connect now.
     */
    CONNECTING,

    CONNECTED,

    /**
     * After connecting, this connection is **ONLY** be working in this status if you do intent to disconnect to server as you expect.
     *
     * **Attention:** [FAILED] and listeners will **NOT** trigger [DISCONNECTED] listener.
     */
    DISCONNECTED,

    /**
     * During netty initializing connecting phase, if connect to server failed, the connecting state will be assigned in this status.
     * For example, server down, invalid ip or port, retry to connect failed.
     *
     * Once connecting is in this status, [DISCONNECTED] listeners will **NOT** be triggered.
     */
    FAILED
}


enum class ServerConnectStatus {
    /**
     * This is the connection default status after initializing netty client.
     *
     * Only you release socket, it will be in this status.
     * In this status, you can not reconnect again. You must create netty client again.
     */
    UNINITIALIZED,

    STARTED,
    STOPPED,
    CLIENT_CONNECTED,

    /**
     * After connecting, this connection is **ONLY** be working in this status if you do intent to disconnect to server as you expect.
     *
     * **Attention:** [FAILED] listeners will **NOT** trigger [CLIENT_DISCONNECTED] listener.
     */
    CLIENT_DISCONNECTED,

    /**
     * During netty initializing connecting phase, if connect to server failed, the connecting state will be assigned in this status.
     * For example, server down, invalid ip or port, retry to connect failed.
     *
     * Once connecting is in this status, [CLIENT_DISCONNECTED] listeners will **NOT** be triggered.
     */
    FAILED
}