package com.leovp.leoandroidbaseutil.basic_components.examples.adb.base

import com.leovp.log_sdk.LogContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Demonstration client PC side source
 * when connect to Android Local Server.
 *
 * adb forward tcp:8888 localabstract:local
 *
 * Author: Michael Leo
 * Date: 2022/3/19 11:48
 */
class LocalClient {
    fun start() {
        val client = Socket()
        // Blocking
        client.connect(InetSocketAddress("127.0.0.1", 8888))
        LogContext.log.i("Connect successfully!")
        val outputStream = client.getOutputStream()
        outputStream.write("Hello World".toByteArray())
        outputStream.flush()
        outputStream.close()
    }
}