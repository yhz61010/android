@file:Suppress("unused", "MemberVisibilityCanBePrivate", "WeakerAccess")

package com.leovp.opengl_sdk.util

object VerticesUtil {
    /**
     * OpenGL 的世界坐标系是 `[-1, -1, 1, 1]`
     *
     * 顺序：ABCD
     *
     * 与 Android 中的 Canvas 或者屏幕坐标体系不同，GL 的坐标起始位置在屏幕中心，
     * (0,0) 作为中心点，X 坐标从左到右，Y 坐标从下到上，在 `[-1,1]` 之间取值，再映射到屏幕上。
     *
     * ```
     * C(-1,1)        D(1,1)
     *       ┌────────┐
     *       │    ↑   │
     *       │ ───┼──→│ center (0,0)
     *       │    │   │
     *       └────────┘
     * A(-1,-1)       B(1,-1)
     * ```
     */
    val VERTICES_COORD = floatArrayOf(
        -1.0f, -1.0f, // lb
        1.0f, -1.0f,  // rb
        -1.0f, 1.0f,  // lt
        1.0f, 1.0f    // rt
    )

    /**
     * 纹理的坐标系。取值范围在 `[0,0]` 到 `[1,1]` 内。
     *
     * 顺序：ABCD
     *
     * 两个维度分别是 S、T，所以一般称为 ST 纹理坐标。而有些时候也叫 UV 坐标。
     * 纹理坐标方向性在 Android 上与我们平时熟悉的 Bitmap、canvas 等一致，都是顶点在左上角。
     *
     * 说明：
     * 其实 OpenGL 中的纹理坐标是没有内在方向性的，所以我们可以随意定义。
     * 但由于大多数计算机图像都有默认方向，所以暂按照下面左图取理解即可。
     *
     * ```
     * C(0,0)────s──→D(1,0)       C(0,1)        D(1,1)
     *   │  ┌───────┐               ↑  ┌───────┐
     *   t  │texture│               t  │texture│
     *   │  │       │               │  │       │
     *   ↓  └───────┘               │  └───────┘
     * A(0,1)        B(1,1)       A(0,0)────s──→B(1,0)
     * ```
     */
    val TEX_COORD = floatArrayOf(
        0.0f, 1.0f, // lb
        1.0f, 1.0f, // rb
        0.0f, 0.0f, // lt
        1.0f, 0.0f  // rt
    )

    // ===============================

    /**
     * OpenGL 的世界坐标系是 `[-1, -1, 1, 1]`。
     * 顺时针方向。
     *
     * 顺序：ABCD
     *
     * 与 Android 中的 Canvas 或者屏幕坐标体系不同，GL 的坐标起始位置在屏幕中心，
     * (0,0) 作为中心点，X 坐标从左到右，Y 坐标从下到上，在 `[-1,1]` 之间取值，再映射到屏幕上。
     *
     * ```
     * B(-1,1)        C(1,1)
     *       ┌────────┐
     *       │    ↑   │
     *       │ ───┼──→│ center (0,0)
     *       │    │   │
     *       └────────┘
     * A(-1,-1)       D(1,-1)
     */
    val VERTICES_COORD_CW = floatArrayOf(
        -1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, -1.0f
    )

    /**
     * 纹理的坐标系。取值范围在 `[0,0]` 到 `[1,1]` 内。
     * 顺时针方向。
     *
     * 顺序：ABCD
     *
     * 两个维度分别是 S、T，所以一般称为 ST 纹理坐标。而有些时候也叫 UV 坐标。
     * 纹理坐标方向性在 Android 上与我们平时熟悉的 Bitmap、canvas 等一致，都是顶点在左上角。
     *
     * 说明：
     * 其实 OpenGL 中的纹理坐标是没有内在方向性的，所以我们可以随意定义。
     * 但由于大多数计算机图像都有默认方向，所以暂按照下面左图取理解即可。
     *
     * ```
     * B(0,0)────s──→C(1,0)       B(0,1)        C(1,1)
     *   │  ┌───────┐               ↑  ┌───────┐
     *   t  │texture│               t  │texture│
     *   │  │       │               │  │       │
     *   ↓  └───────┘               │  └───────┘
     * A(0,1)        D(1,1)       A(0,0)────s──→D(1,0)
     * ```
     */
    val TEX_COORD_CW = floatArrayOf(
        0f, 1f,
        0f, 0f,
        1f, 0f,
        1f, 1f
    )

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
                verticesArray[6], verticesArray[7],
                verticesArray[0], verticesArray[1],
                verticesArray[4], verticesArray[5],
            )
            Rotation.ROTATION_180 -> floatArrayOf(
                verticesArray[6], verticesArray[7],
                verticesArray[4], verticesArray[5],
                verticesArray[2], verticesArray[3],
                verticesArray[0], verticesArray[1])
            Rotation.ROTATION_270 -> floatArrayOf(
                verticesArray[4], verticesArray[5],
                verticesArray[0], verticesArray[1],
                verticesArray[6], verticesArray[7],
                verticesArray[2], verticesArray[3])
            else                  -> verticesArray
        }
    }

    fun flip(verticesArray: FloatArray, isHorizontal: Boolean = false, isVertical: Boolean = false): FloatArray {
        var temp = floatArrayOf(
            verticesArray[0], verticesArray[1],
            verticesArray[2], verticesArray[3],
            verticesArray[4], verticesArray[5],
            verticesArray[6], verticesArray[7])
        temp = if (isHorizontal) floatArrayOf(
            temp[2], temp[3],
            temp[0], temp[1],
            temp[6], temp[7],
            temp[4], temp[5]) else temp
        temp = if (isVertical) floatArrayOf(
            temp[6], temp[7],
            temp[4], temp[5],
            temp[2], temp[3],
            temp[0], temp[1]) else temp
        return temp
    }
}