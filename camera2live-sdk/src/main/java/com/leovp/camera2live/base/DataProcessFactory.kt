package com.leovp.camera2live.base

import com.leovp.camera2live.base.encode_strategies.Yuv420PEncoderStrategy
import com.leovp.camera2live.base.encode_strategies.Yuv420SpEncoderStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 下午2:17
 */
object DataProcessFactory {

    const val ENCODER_TYPE_YUV420P = 1
    const val ENCODER_TYPE_YUV420SP = 2

    fun getConcreteObject(type: Int): DataProcessContext? {
        return when (type) {
            ENCODER_TYPE_YUV420P -> DataProcessContext(Yuv420PEncoderStrategy())
            ENCODER_TYPE_YUV420SP -> DataProcessContext(Yuv420SpEncoderStrategy())
            else -> null
        }
    }
}