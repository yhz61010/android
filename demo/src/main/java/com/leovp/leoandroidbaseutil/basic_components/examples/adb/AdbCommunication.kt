package com.leovp.leoandroidbaseutil.basic_components.examples.adb

import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.adb.base.LocalServerSocket
import com.leovp.log_sdk.LogContext
import kotlin.concurrent.thread

/**
 * https://juejin.cn/post/6844903746464399367
 */
class AdbCommunication : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adb_communication)

        thread {
            LogContext.log.i("LocalServer starting...")
            val server = LocalServerSocket()
            //            val server = LocalServer()
            LogContext.log.i("LocalServer created.")
            server.startServer("local_name")
            LogContext.log.i("LocalServer started!")
        }
    }
}