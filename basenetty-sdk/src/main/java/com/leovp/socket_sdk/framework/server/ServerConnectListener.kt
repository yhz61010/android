package com.leovp.socket_sdk.framework.server

import com.leovp.socket_sdk.framework.base.BaseNetty
import io.netty.channel.Channel

/**
 * Author: Michael Leo
 * Date: 20-8-6 下午6:25
 */
interface ServerConnectListener<in T : BaseNetty> {

    fun onStarted(netty: T)
    fun onStopped()
    fun onStartFailed(netty: T, code: Int, msg: String?)
    fun onReceivedData(netty: T, clientChannel: Channel, data: Any?, action: Int = -1)
    fun onClientConnected(netty: T, clientChannel: Channel)
    fun onClientDisconnected(netty: T, clientChannel: Channel)

    companion object {
        const val CONNECTION_ERROR_ALREADY_RELEASED = 0x8000
        const val CONNECTION_ERROR_SERVER_START_ERROR = 0x8001
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = 0x8003
    }
}
