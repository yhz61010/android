package com.leovp.opengl_sdk.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Author: Michael Leo
 * Date: 2022/4/2 18:08
 */
object BufferUtil {
    /**
     * 创建一个 FloatBuffer 缓冲区，用于保存顶点/屏幕顶点和纹理顶点
     *
     * OpenGL的世界坐标系是 [-1, -1, 1, 1]，纹理的坐标系为 [0, 0, 1, 1]
     */
    fun createFloatBuffers(array: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(array.size * Float.SIZE_BYTES)
            // Use the device hardware's native byte order
            .order(ByteOrder.nativeOrder())

            // Create a floating point buffer from the ByteBuffer
            .asFloatBuffer().apply {
                // Add the coordinates to the FloatBuffer
                put(array)
                // Set the buffer to read the first coordinate
                //                position(0)
                rewind()
            }
    }
}