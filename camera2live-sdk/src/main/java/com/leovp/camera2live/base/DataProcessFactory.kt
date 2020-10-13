package com.leovp.camera2live.base

import com.leovp.camera2live.base.encode_strategies.DefaultEncoderStrategy
import com.leovp.camera2live.base.encode_strategies.YuvEncoderStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 下午2:17
 */
object DataProcessFactory {

    const val ENCODER_TYPE_NORMAL = 1
    const val ENCODER_TYPE_YUV_ORIGINAL = 2

    fun getConcreteObject(type: Int): DataProcessContext? {
        return when (type) {
            ENCODER_TYPE_NORMAL -> DataProcessContext(DefaultEncoderStrategy())
            ENCODER_TYPE_YUV_ORIGINAL -> DataProcessContext(YuvEncoderStrategy())
            else -> null
        }
    }
}