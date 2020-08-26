package com.ho1ho.androidbase.utils.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import java.io.RandomAccessFile

/**
 * static long getMobileRxBytes() // 获取通过 Mobile 连接收到的字节总数，但不包含 WiFi static long
 * getMobileRxPackets() // 获取 Mobile 连接收到的数据包总数 static long
 * getMobileTxBytes() // Mobile 发送的总字节数 static long
 * getMobileTxPackets() // Mobile 发送的总数据包数 static long
 * getTotalRxBytes() // 获取总的接受字节数，包含 Mobile 和 WiFi 等 static long
 * getTotalRxPackets() // 总的接受数据包数，包含 Mobile 和 WiFi 等 static long
 * getTotalTxBytes() // 总的发送字节数，包含 Mobile 和 WiFi 等 static long
 * getTotalTxPackets() // 发送的总数据包数，包含 Mobile 和 WiFi 等 static long
 * getUidRxBytes(int uid) // 获取某个网络 UID 的接受字节数 static long getUidTxBytes(int uid) //获取某个网络 UID 的发送字节数
 *
 *
 * Author: Michael Leo
 * Date: 19-8-30 下午1:38
 */
class TrafficStatHelper private constructor(val ctx: Context, private val monitorCallback: NetworkMonitor.Callback?) {

    /**
     * The data will be sent in every *freq* second(s)
     */
//    private var freq = 1

    /**
     * Last saved received bytes
     */
    private var preRxBytes: Long = 0

    /**
     * Last saved sent bytes
     */
    private var preTxBytes: Long = 0

    fun getSpeed(): Array<Long> {
        return arrayOf(downloadSpeed, uploadSpeed)
    }

    @Suppress("unused")
    val currentAppUid = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).uid

    /**
     * Total traffic
     */
    @Suppress("unused")
    val totalTraffic: Long = receiveTraffic + sendTraffic

    /**
     * Get sent bytes
     */
    private val sendTraffic: Long
        get() {
            var sendTraffic = TrafficStats.getUidTxBytes(UUID)
            val sndPath = "/proc/uid_stat/$UUID/tcp_snd"
            try {
                RandomAccessFile(sndPath, "r").use { sendTraffic = it.readLine().toLong() }
            } finally {
                return sendTraffic
            }
        }

    /**
     * Get received traffic
     */
    private val receiveTraffic: Long
        get() {
            var recTraffic = TrafficStats.getUidRxBytes(UUID)
            val rcvPath = "/proc/uid_stat/$UUID/tcp_rcv"
            try {
                RandomAccessFile(rcvPath, "r").use { recTraffic = it.readLine().toLong() }
            } finally {
                return recTraffic
            }
        }

    /**
     * Get current download speed(STAT_TIME_INTERVAL_IN_SECONDS seconds total traffic)
     */
    private val downloadSpeed: Long
        get() {
            val curRxBytes = totalReceivedBytes
            if (preRxBytes == 0L) preRxBytes = curRxBytes
            val bytes = curRxBytes - preRxBytes
            preRxBytes = curRxBytes
            return bytes
        }

    /**
     * Get upload speed(STAT_TIME_INTERVAL_IN_SECONDS seconds total traffic)
     */
    private val uploadSpeed: Long
        get() {
            val curTxBytes = totalSentBytes
            if (preTxBytes == 0L) preTxBytes = curTxBytes
            val bytes = curTxBytes - preTxBytes
            preTxBytes = curTxBytes
            return bytes
        }

    companion object {
        @Volatile
        private var instance: TrafficStatHelper? = null
        var UUID: Int = -1
        fun getInstance(ctx: Context, monitorCallback: NetworkMonitor.Callback?): TrafficStatHelper {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        UUID = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).uid
                        instance = TrafficStatHelper(ctx, monitorCallback)
                    }
                }
            }
            return instance!!
        }

        val totalReceivedBytes: Long get() = TrafficStats.getTotalRxBytes()
        val totalSentBytes: Long get() = TrafficStats.getTotalTxBytes()
    }
}