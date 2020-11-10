package com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.base

import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.ServerConnectListener

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:14
 */
class AudioReceiverWebSocket(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true)