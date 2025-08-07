package com.leovp.demo.basiccomponents.examples.adb

import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.adb.base.LocalServerSocket
import com.leovp.demo.databinding.ActivityAdbCommunicationBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlin.concurrent.thread

/**
 * https://juejin.cn/post/6844903746464399367
 */
class AdbCommunication :
    BaseDemonstrationActivity<ActivityAdbCommunicationBinding>(R.layout.activity_adb_communication) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAdbCommunicationBinding =
        ActivityAdbCommunicationBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        thread {
            LogContext.log.i(ITAG, "LocalServer starting...")
            val server = LocalServerSocket()
            //            val server = LocalServer()
            LogContext.log.i(ITAG, "LocalServer created.")
            server.startServer("local_name")
            LogContext.log.i(ITAG, "LocalServer started!")
        }
    }
}
