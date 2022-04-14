package com.leovp.opengl_sdk.util

/**
 * Author: Michael Leo
 * Date: 2022/4/7 16:27
 */
object GLConstants {
    // I420, YV12
    const val THREE_PLANAR = 3

    // NV12, NV21
    const val TWO_PLANAR = 2

    /**
     * 坐标占用的向量个数
     * 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)
     * 例如，若只有 x、y，则该值为 2
     */
    const val TWO_DIMEN_POS_COMPONENT_COUNT = 2

    const val THREE_DIMEN_POS_COMPONENT_COUNT = 3

    /**
     * 纹理坐标中每个点所占的向量个数
     */
    const val TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT = 2

    /**
     * RGB 颜色占用的向量个数
     */
    const val RGB_COLOR_COMPONENT_COUNT = 3

    /**
     * 数据数组中每个顶点起始数据的间距：数组中每个顶点相关属性占的 Byte 值
     */
    const val TWO_DIMEN_STRIDE_IN_FLOAT = TWO_DIMEN_POS_COMPONENT_COUNT * Float.SIZE_BYTES
}