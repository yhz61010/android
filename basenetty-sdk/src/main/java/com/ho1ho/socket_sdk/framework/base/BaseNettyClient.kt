package com.ho1ho.socket_sdk.framework.base

import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.exception
import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import com.ho1ho.socket_sdk.framework.base.inter.ReceivingDataListener
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseNettyClient protected constructor(private var mHost: String, private var mPort: Int) {

    private val tag = this::class.java.simpleName

    private var mEventLoopGroup: EventLoopGroup? = null
    private var mBootstrap: Bootstrap? = null
    private var mChannel: Channel? = null
    private var mChannelFuture: ChannelFuture? = null
    private var mChannelInitializer: ChannelInitializer<SocketChannel>? = null
    var defaultChannelHandler: BaseChannelInboundHandler<*>? = null
        private set

    var receivingDataListener: ReceivingDataListener? = null
    var connectionListener: NettyConnectionListener? = null
    var connectState = DISCONNECTED
    private val connectStateName
        get() = when (connectState) {
            UNINITIALIZED -> "UNINITIALIZED"
            INITIALIZED -> "INITIALIZED"
            DISCONNECTED -> "DISCONNECTED"
            CONNECTING -> "CONNECTING"
            CONNECTED -> "CONNECTED"
            CONNECT_FAILED -> "CONNECT_FAILED"
            CONNECT_EXCEPTION -> "CONNECT_EXCEPTION"
            else -> "UNKNOWN"
        }
    private var mRetryHandler: Handler? = null
    private var mRetryThread: HandlerThread? = null
    private var mRetryTimes = 0

    init {
        init()
        initRetryHandler()
    }

    private fun init() {
        try {
            connectState = INITIALIZED
            mBootstrap = Bootstrap()
            mEventLoopGroup = NioEventLoopGroup()
            mBootstrap!!.group(mEventLoopGroup)
            mBootstrap!!.channel(NioSocketChannel::class.java)
            mBootstrap!!.option(ChannelOption.TCP_NODELAY, true)
            mBootstrap!!.option(ChannelOption.SO_KEEPALIVE, true)
            mBootstrap!!.option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                CONNECTION_TIMEOUT_IN_MILLS
            )
        } catch (e: Exception) {
            LLog.e(tag, "init() Exception: ${e.message}")
        }
    }

    fun initHandler(handler: BaseChannelInboundHandler<*>?) {
        defaultChannelHandler = handler
        mChannelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                val pipeline = socketChannel.pipeline()
                addLastToPipeline(pipeline)
                if (null != defaultChannelHandler) {
                    pipeline.addLast("default-channel-handler", defaultChannelHandler)
                }
            }
        }
        mBootstrap!!.handler(mChannelInitializer)
    }

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun connect(listener: NettyConnectionListener) {
        LLog.i(tag, "===== connect() current state=$connectStateName =====")
        when (connectState) {
            CONNECTING -> {
                LLog.w(tag, "===== Wait for connecting =====")
                return
            }
            CONNECTED -> {
                LLog.w(tag, "===== Already connected =====")
                return
            }
            else -> LLog.i(tag, "===== Prepare to connect to server =====")
        }
        connectState = CONNECTING
        try {
            connectionListener = listener.apply { onConnectionConnecting(this@BaseNettyClient) }
            val f = mBootstrap!!.connect(mHost, mPort).sync()
            LLog.i(tag, "Binding ChannelFuture listener...")
            f.addListener(mChannelFutureListener)
        } catch (e: Exception) {
            LLog.e(tag, "Retry due to connect error=${e.message}")
            doRetry()
        }
    }

    @Suppress("WeakerAccess")
    fun reconnect() {
        LLog.w(tag, "===== reconnect() current state=$connectStateName =====")
        connectionListener ?: exception("You must call connect first")
//        disconnect()
        connectState = DISCONNECTED
        connect(connectionListener!!)
    }

    @Suppress("WeakerAccess")
    fun disconnect() {
        LLog.w(tag, "===== disconnect() current state=$connectStateName =====")
        connectState = DISCONNECTED
        defaultChannelHandler?.let {
            LLog.w(tag, "===== call onConnectionDisconnect =====")
            connectionListener?.onConnectionDisconnect(this)
        }
    }

    fun release() {
        LLog.e(tag, "===== release() current state=$connectStateName } =====")
        if (UNINITIALIZED == connectState) {
            LLog.w(tag, "Already released")
            return
        }
        connectState = UNINITIALIZED

        LLog.w(tag, "Releasing connect listener...")
        connectionListener = null

        mRetryHandler?.run {
            LLog.w(tag, "Removing RetryThread from RetryHandler...")
            removeCallbacksAndMessages(null)
            mRetryHandler = null
        }

        mRetryThread?.run {
            LLog.w(tag, "Quiting RetryThread safely...")
            quitSafely()
            mRetryThread = null
        }

        LLog.w(tag, "Releasing default socket handler...")
        defaultChannelHandler?.release()
        defaultChannelHandler = null

        mChannel?.run {
            LLog.w(tag, "Closing channel...")
//            closeFuture().syncUninterruptibly()
            closeFuture()
            close()
            mChannel = null
        }
        defaultChannelHandler = null
        mChannelInitializer = null

        mEventLoopGroup?.run {
            LLog.w(tag, "Shutting down socket...")
            shutdownGracefully().syncUninterruptibly()
            mEventLoopGroup = null
        }
        mBootstrap = null

        LLog.w(tag, "=====> Socket released <=====")
    }

    private val mChannelFutureListener: ChannelFutureListener = ChannelFutureListener { future ->
        if (future.isSuccess) {
            mRetryTimes = 0
            mChannelFuture = future
            mChannel = mChannelFuture!!.syncUninterruptibly().channel()
            connectState = CONNECTED
            if (null != defaultChannelHandler) {
                LLog.i(tag, "===== operationComplete - onConnectionCreated() =====")
                connectionListener?.onConnectionCreated(this@BaseNettyClient)
            }
        } else {
            connectState = CONNECT_FAILED
            LLog.e(tag, "Retry due to future failure")
            doRetry()
        }
    }

    // ================================================
    private fun initRetryHandler() {
        LLog.i(tag, "===== initRetryHandler() =====")
        if (mRetryThread == null) {
            LLog.i(tag, "===== init mRetryThread =====")
            mRetryThread = HandlerThread("retry-thread")
            mRetryThread!!.start()
        }
        if (mRetryHandler == null) {
            LLog.i(tag, "===== init mRetryHandler =====")
            mRetryHandler = Handler(mRetryThread!!.looper)
        }
    }

    private fun doRetry() {
        if (mRetryTimes >= CONNECT_MAX_RETRY_TIMES) {
            defaultChannelHandler?.let {
                LLog.w(tag, "===== Connect failed - call onConnectionFailed() =====")
                mRetryTimes = 0
                connectionListener?.onConnectionFailed(this)
            }
            return
        }

//        val retryDelay = 1000L * (1 shl mRetryTimes++)
        val retryDelay = 5000L;mRetryTimes++
        LLog.e(tag, "===== doRetry($mRetryTimes) in ${retryDelay}ms =====")

        mRetryHandler!!.postDelayed({ reconnect() }, retryDelay)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(): Boolean {
        if (CONNECTED != connectState) {
            LLog.e(tag, "Socket is not connected. Can not send command.")
            ToastUtil.showDebugToast("Socket is not connected. Can not send command.")
            return false
        }
        if (mChannel == null) {
            LLog.e(tag, "Can not execute cmd because of Channel is null.")
            ToastUtil.showDebugToast("Channel is null. Can not send command.")
            return false
        }
        return true
    }

    private fun executeCommandInString(cmd: String?, showLog: Boolean) {
        if (!isValidExecuteCommandEnv()) {
            LLog.e(tag, "Not invalid execute command evn!")
            return
        }
        if (cmd.isNullOrBlank()) {
            LLog.w(tag, "Can not execute blank string command.")
            ToastUtil.showDebugErrorToast("Empty string command")
            return
        }
        if (showLog) {
            LLog.i(tag, "exeCmd s[${cmd.length}]=$cmd")
        }
        mChannel!!.writeAndFlush(cmd + "\n")
    }

    private fun executeCommandInBinary(bytes: ByteArray?, showLog: Boolean) {
        if (!isValidExecuteCommandEnv()) {
            return
        }
        if (bytes == null || bytes.isEmpty()) {
            LLog.w(tag, "Can not execute blank binary command.")
            ToastUtil.showDebugErrorToast("Command bytes is empty. Can not send command.")
            return
        }
        if (showLog) {
            LLog.i(tag, "exeCmd HEX[${bytes.size}]=[${bytes.toHexStringLE()}]")
        }
        mChannel!!.writeAndFlush(Unpooled.wrappedBuffer(bytes))
    }

    fun executeCommand(cmd: String) {
        executeCommandInString(cmd, true)
    }

    @Suppress("unused")
    fun executeCommand(cmd: String, showLog: Boolean) {
        executeCommandInString(cmd, showLog)
    }

    @Suppress("unused")
    fun executeCommand(cmd: ByteArray?, showLog: Boolean) {
        executeCommandInBinary(cmd, showLog)
    }

    fun executeCommand(cmd: ByteArray?) {
        executeCommandInBinary(cmd, true)
    }

    // ================================================

    companion object {
        const val UNINITIALIZED = -1
        const val INITIALIZED = 0
        const val DISCONNECTED = 1
        const val CONNECTING = 2
        const val CONNECTED = 3
        const val CONNECT_FAILED = 4
        const val CONNECT_EXCEPTION = 5

        const val HEARTBEAT_INTERVAL_IN_MS = 10_000L
        const val HEARTBEAT_TIMEOUT_TIMES = 3
        const val CONNECTION_TIMEOUT_IN_MILLS = 60_000
        private const val CONNECT_MAX_RETRY_TIMES = 180
    }
}