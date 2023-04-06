package com.leovp.demo.basiccomponents.examples.adb.base

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.leovp.log.LogContext
import java.io.InputStream

/**
 * ```
 * adb forward tcp:8888 localabstract:local_name
 * ```
 * Forward port 8888 on PC to device UNIX localabstract which name is _local_name_
 * Note that: the `local_name` must be the same value in `LocalServerSocket#startServer(name)` variable.
 *
 * Author: Michael Leo
 *
 * Date: 2022/3/19 11:36
 */
class LocalServerSocket {
    fun startServer(name: String) {
        LogContext.log.i("startServer($name)")
        val serverSocket = LocalServerSocket(name)
        LogContext.log.i("LocalServerSocket created!")
        // Blocking
        val client: LocalSocket = serverSocket.accept()
        LogContext.log.i("Client connected!")
        while (true) {
            if (!client.isConnected) {
                LogContext.log.i("isConnected=false")
                return
            }
            val inputStream: InputStream = client.inputStream
            val result: String = inputStream.reader().readText()
            LogContext.log.i("ServerSocket rcv=$result")
        }
    }
}
