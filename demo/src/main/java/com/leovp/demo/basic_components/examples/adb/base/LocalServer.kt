package com.leovp.demo.basic_components.examples.adb.base

import com.leovp.log_sdk.LogContext
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * adb forward tcp:8888 tcp:8888
 *
 * Author: Michael Leo
 * Date: 2022/3/21 10:12
 */
class LocalServer {
    fun startServer() {
        thread {
            val serverSocket = ServerSocket(8888)
            // Blocking
            val client: Socket = serverSocket.accept()
            LogContext.log.i("Client connected!")
            while (true) {
                if (!client.isConnected) {
                    LogContext.log.i("client.isConnected=false")
                    return@thread
                }
                val inputStream: InputStream = client.getInputStream()
                val result: String = inputStream.reader().readText()
                LogContext.log.i("serverSocket rcv=$result")
            }
        }
    }
}
