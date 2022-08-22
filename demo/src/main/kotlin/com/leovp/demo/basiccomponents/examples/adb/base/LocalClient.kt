package com.leovp.demo.basiccomponents.examples.adb.base

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Demonstration client PC side source
 * when connect to Android Local Server.
 *
 * ```
 * adb forward tcp:8888 localabstract:local_name
 * adb forward tcp:8888 tcp:8888
 * ```
 *
 * Author: Michael Leo
 * Date: 2022/3/19 11:48
 */
class LocalClient {
    fun start() {
        val client = Socket()
        // Blocking
        client.connect(InetSocketAddress("127.0.0.1", 8888))
        println("Connect successfully!")
        val outputStream = client.getOutputStream()
        println("getOutputStream")
        outputStream.write("Hello World".toByteArray())
        println("wrote")
        outputStream.flush()
        outputStream.close()
        client.close()
        println("Disconnected!")
    }
}
