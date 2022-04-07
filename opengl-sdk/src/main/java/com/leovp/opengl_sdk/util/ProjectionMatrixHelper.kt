package com.leovp.opengl_sdk.util

import android.opengl.GLES20
import android.opengl.Matrix

/**
 * Author: Michael Leo
 * Date: 2022/4/7 13:32
 */
class ProjectionMatrixHelper(program: Int, name: String) {

    companion object {
        /** 矩阵数组 */
        val projectionMatrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f)
    }

    private val uMatrixLocation: Int = GLES20.glGetUniformLocation(program, name)

    fun enable(width: Int, height: Int) {
        // 边长比(>=1)，非宽高比
        val aspectRatio: Float = if (width > height) width.toFloat() / height else height.toFloat() / width

        // 1. 矩阵数组
        // 2. 结果矩阵起始的偏移量
        // 3. left  ：x 的最小值
        // 4. right ：x 的最大值
        // 5. bottom：y 的最小值
        // 6. top   ：y 的最大值
        // 7. near  ：z 的最小值
        // 8. far   ：z 的最大值
        if (width > height) {
            // 横屏
            Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
        } else {
            // 竖屏 or 正方形
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f)
        }
        // 更新 u_Matrix 的值，即更新矩阵数组
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0)
    }
}
