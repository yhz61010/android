package com.leovp.opengl_sdk.util

object VerticesRotationUtil {
    enum class Rotation {
        NORMAL, ROTATION_90, ROTATION_180, ROTATION_270;

        companion object {
            fun getRotation(rotation: Int): Rotation {
                return when (rotation) {
                    0    -> NORMAL
                    90   -> ROTATION_90
                    180  -> ROTATION_180
                    270  -> ROTATION_270
                    else -> NORMAL
                }
            }
        }
    }

    fun rotate(verticesArray: FloatArray, rotation: Int): FloatArray {
        return rotate(verticesArray, Rotation.getRotation(rotation))
    }

    fun rotate(verticesArray: FloatArray, rotation: Rotation): FloatArray {
        return when (rotation) {
            Rotation.ROTATION_90  -> floatArrayOf(
                verticesArray[2], verticesArray[3],
                verticesArray[4], verticesArray[5],
                verticesArray[6], verticesArray[7],
                verticesArray[0], verticesArray[1])
            Rotation.ROTATION_180 -> floatArrayOf(
                verticesArray[4], verticesArray[5],
                verticesArray[6], verticesArray[7],
                verticesArray[0], verticesArray[1],
                verticesArray[2], verticesArray[3])
            Rotation.ROTATION_270 -> floatArrayOf(
                verticesArray[6], verticesArray[7],
                verticesArray[0], verticesArray[1],
                verticesArray[2], verticesArray[3],
                verticesArray[4], verticesArray[5])
            else                  -> verticesArray
        }
    }

    fun flip(verticesArray: FloatArray, isVertical: Boolean = false, isHorizontal: Boolean = false): FloatArray {
        var temp = floatArrayOf(
            verticesArray[0], verticesArray[1],
            verticesArray[2], verticesArray[3],
            verticesArray[4], verticesArray[5],
            verticesArray[6], verticesArray[7])
        temp = if (isVertical) floatArrayOf(
            temp[2], temp[3],
            temp[0], temp[1],
            temp[6], temp[7],
            temp[4], temp[5]) else temp
        temp = if (isHorizontal) floatArrayOf(
            temp[6], temp[7],
            temp[4], temp[5],
            temp[2], temp[3],
            temp[0], temp[1]) else temp
        return temp
    }
}