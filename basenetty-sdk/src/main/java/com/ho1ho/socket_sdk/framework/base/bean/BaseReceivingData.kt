package com.ho1ho.socket_sdk.framework.base.bean

/**
 * Author: Michael Leo
 * Date: 20-5-25 下午4:30
 */
interface BaseReceivingData {
    fun getWhich(): Int

    companion object {
        const val RECEIVING_DATA_TYPE_CLIENT_INFO = 0X1001
    }
}