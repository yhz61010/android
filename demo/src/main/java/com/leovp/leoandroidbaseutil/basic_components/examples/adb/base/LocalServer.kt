package com.leovp.leoandroidbaseutil.basic_components.examples.adb.base

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.leovp.log_sdk.LogContext
import java.io.InputStream

/**
 * ```
 * adb forward tcp:8888 localabstract:local
 * ```
 *
 * Author: Michael Leo
 *
 * Date: 2022/3/19 11:36
 */
class LocalServer {
    fun startServer(name: String) {
        val serverSocket = LocalServerSocket(name)
        // Blocking
        val client: LocalSocket = serverSocket.accept()
        LogContext.log.i("Connect successfully.")
        while (true) {
            if (!client.isConnected) {
                return
            }
            val inputStream: InputStream = client.inputStream
            val result: String = inputStream.reader().readText()
            LogContext.log.i("ServerSocket rcv=$result")
        }
    }
}