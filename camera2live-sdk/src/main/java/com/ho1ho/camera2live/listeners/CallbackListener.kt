package com.ho1ho.camera2live.listeners

/**
 * Author: Michael Leo
 * Date: 19-8-2 下午4:54
 */
interface CallbackListener {
    fun onCallback(h264Data: ByteArray)
}