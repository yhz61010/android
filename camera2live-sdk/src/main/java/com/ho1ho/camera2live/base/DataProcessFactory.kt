package com.ho1ho.camera2live.base

/**
 * Author: Michael Leo
 * Date: 20-4-1 下午2:17
 */
object DataProcessFactory {

    const val ENCODER_TYPE_NORMAL = 1
    const val ENCODER_TYPE_YUV_ORIGINAL = 2
    const val ENCODER_TYPE_NEXUS = 3

    fun getConcreteObject(type: Int): DataProcessContext? {
        return when (type) {
            ENCODER_TYPE_NORMAL -> DataProcessContext(DefaultEncoderStrategy())
            ENCODER_TYPE_YUV_ORIGINAL -> DataProcessContext(YuvEncoderStrategy())
            ENCODER_TYPE_NEXUS -> DataProcessContext(NexusEncoderStrategy())
            else -> null
        }
    }
}