/***
 * Excerpted from "OpenGL ES for Android",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/kbogla for more book information.
 */
package com.leovp.opengl_sdk.util

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog

/**
 * 纹理加载助手类
 */
object TextureHelper {
    private const val TAG = "TextureHelper"

    /**
     * 纹理数据
     */
    class TextureBean {
        var textureId: Int = 0
            internal set
        var width: Int = 0
        var height: Int = 0
    }

    /**
     * 根据资源 ID 获取相应的 OpenGL 纹理 ID，若加载失败则返回 0。
     * 必须在 GL 线程中调用。
     */
    fun loadTexture(context: Context, resourceId: Int): TextureBean {
        val bean = TextureBean()
        val textureObjectIds = IntArray(1)
        // 1. 创建纹理对象
        GLES20.glGenTextures(1, textureObjectIds, 0)

        if (textureObjectIds[0] == 0) {
            LogContext.log.w(TAG, "Could not generate a new OpenGL texture object.", outputType = ILog.OUTPUT_TYPE_SYSTEM)
            return bean
        }

        val options = BitmapFactory.Options()
        options.inScaled = false

        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)

        if (bitmap == null) {
            LogContext.log.w(TAG, "Resource ID $resourceId could not be decoded.", outputType = ILog.OUTPUT_TYPE_SYSTEM)
            // 加载 Bitmap 资源失败，删除纹理 Id
            GLES20.glDeleteTextures(1, textureObjectIds, 0)
            return bean
        }
        // 2. 将纹理绑定到 OpenGL 对象上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureObjectIds[0])

        // 3. 设置纹理过滤参数:解决纹理缩放过程中的锯齿问题。若不设置，则会导致纹理为黑色
        // 设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        // 设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // 设置环绕方向 S，截取纹理坐标到 [1/2n,1-1/2n]。将导致永远不会与 border 融合
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        // 设置环绕方向 T，截取纹理坐标到 [1/2n,1-1/2n]。将导致永远不会与 border 融合
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        // 4. 通过 OpenGL 对象读取 Bitmap 数据，并且绑定到纹理对象上，之后就可以回收 Bitmap 对象
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // Note: Following code may cause an error to be reported in the
        // ADB log as follows: E/IMGSRV(20095): :0: HardwareMipGen:
        // Failed to generate texture mipmap levels (error=3)
        // No OpenGL error will be encountered (glGetError() will return
        // 0). If this happens, just squash the source image to be
        // square. It will look the same because of texture coordinates,
        // and mipmap generation will work.
        // 5. 生成 Mip 位图
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

        // 6. 回收 Bitmap 对象
        bean.width = bitmap.width
        bean.height = bitmap.height
        bitmap.recycle()

        // 7. 将纹理从 OpenGL 对象上解绑
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        // 所以整个流程中，OpenGL 对象类似一个容器或者中间者的方式，将 Bitmap 数据转移到 OpenGL 纹理上
        bean.textureId = textureObjectIds[0]
        return bean
    }
}
