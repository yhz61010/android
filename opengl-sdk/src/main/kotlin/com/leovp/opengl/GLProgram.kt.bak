package com.leovp.opengl_sdk

import android.content.Context
import android.opengl.GLES20
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.loadShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

// https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6
// https://blog.csdn.net/sinat_23092639/article/details/103046553
class GLProgram(context: Context) {
    companion object {
        private const val TAG = "GLProgram"

        // I420, YV12
        private const val THREE_PLANAR = 3

        // NV12, NV21
        private const val TWO_PLANAR = 2

        /**
         * Float 类型占 4 Byte
         */
        private const val BYTES_PER_FLOAT = 4

        /**
         * 坐标占用的向量个数
         * 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)
         */
        private const val POSITION_COMPONENT_COUNT = 2

        /**
         * 数据数组中每个顶点起始数据的间距：数组中每个顶点相关属性占的Byte值
         */
        private const val STRIDE = POSITION_COMPONENT_COUNT * BYTES_PER_FLOAT

        /**
         * OpenGL 的世界坐标系是 `[-1, -1, 1, 1]`
         *
         * 与 Android 中的 Canvas 或者屏幕坐标体系不同，GL的坐标起始位置在屏幕中心，
         * (0,0) 作为中心点，X 坐标从左到右，Y 坐标从下到上，在 `[-1,1]` 之间取值，再映射到屏幕上。
         *
         * ```
         * (-1,1)         (1,1)
         *       ┌────────┐
         *       │    ↑   │
         *       │ ───┼──→│ center (0,0)
         *       │    │   │
         *       └────────┘
         * (-1,-1)        (1,-1)
         * ```
         */
        val SQUARE_VERTICES = floatArrayOf(
            -1.0f, -1.0f, // lb
            1.0f, -1.0f,  // rb
            -1.0f, 1.0f,  // lt
            1.0f, 1.0f    // rt
        )
    }

    /**
     * 纹理的坐标系。取值范围在 `[0,0]` 到 `[1,1]` 内。
     *
     * 两个维度分别是 S、T，所以一般称为 ST 纹理坐标。而有些时候也叫UV坐标。
     * 纹理坐标方向性在 Android 上与我们平时熟悉的 Bitmap、canvas 等一致，都是顶点在左上角。
     *
     * ```
     * (0,0)────s──→(1,0)
     *   │  ┌───────┐
     *   t  │texture│
     *   │  │       │
     *   ↓  └───────┘
     * (0,1)        (1,1)
     * ```
     */
    private val coordVertices = floatArrayOf(
        0.0f, 1.0f, // lb
        1.0f, 1.0f, // rb
        0.0f, 0.0f, // lt
        1.0f, 0.0f  // rt
    )

    private var programObjId: Int

    private var planarTextureHandles = IntBuffer.wrap(IntArray(THREE_PLANAR))
    private val sampleHandle = IntArray(3)

    // handles
    private var positionHandle = -1
    private var coordHandle = -1
    private var mvpMatrixHandle: Int = -1

    // vertices buffer
    // OpenGL的世界坐标系范围是 [-1, -1, 1, 1]，纹理的坐标系范围是 [0, 0, 1, 1]
    private var vertexBuffer: FloatBuffer? = null
    private var coordBuffer: FloatBuffer? = null

    init {
        // 返回着色器对象：成功，非0
        val vertexShader: Int = loadShader(context, GLES20.GL_VERTEX_SHADER)
        // 返回着色器对象：成功，非0
        val fragmentShader: Int = loadShader(context, GLES20.GL_FRAGMENT_SHADER)
        LogContext.log.i(TAG, "vertexShader=$vertexShader fragmentShader=$fragmentShader", outputType = ILog.OUTPUT_TYPE_SYSTEM)

        // Create empty OpenGL ES Program
        // 将顶点着色器、片段着色器进行链接，组装成一个 OpenGL ES 程序
        programObjId = GLES20.glCreateProgram().also {
            checkGlError("glCreateProgram")
            // Add the vertex shader to program
            GLES20.glAttachShader(it, vertexShader)

            // Add the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader)

            // Creates OpenGL ES program executables
            // 将两个着色器链接到OpenGL程序对象
            GLES20.glLinkProgram(it)
        }

        // 获取链接状态：OpenGL ES 将想要获取的值放入长度为1的数组的首位
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programObjId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            LogContext.log.w(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(programObjId)}", outputType = ILog.OUTPUT_TYPE_SYSTEM)
            // 链接失败则删除程序对象
            GLES20.glDeleteProgram(programObjId)
            programObjId = 0
        }

        LogContext.log.d(TAG, "programObjId=$programObjId", outputType = ILog.OUTPUT_TYPE_SYSTEM)

        checkGlError("glCreateProgram")

        // 生成纹理句柄
        GLES20.glGenTextures(THREE_PLANAR, planarTextureHandles)

        checkGlError("glGenTextures")
    }

    fun drawTexture(mvpMatrix: FloatArray, type: GLRenderer.Yuv420Type) {
        // 激活渲染程序
        GLES20.glUseProgram(programObjId)
        checkGlError("glUseProgram")
        /*
         * 传入顶点坐标数组给顶点着色器
         *
         * get handle for "a_Position" and "a_TexCoord"
         *
         * OpenGL的世界坐标系是 [-1, -1, 1, 1]，纹理的坐标系为 [0, 0, 1, 1]
         */
        positionHandle = GLES20.glGetAttribLocation(programObjId, "a_Position").also {
            // Parameters:
            //   indx: 顶点着色器中 a_Position 变量的引用。
            //   size: 表示数组中 size 个数字表示一个顶点。
            //   GL_FLOAT: 表示数据类型是浮点数。
            //   normalized: 表示是否进行归一化。
            //   stride: 表示stride（跨距），在数组表示多种属性的时候使用到。数组中每个顶点相关属性占的Byte值。
            //   ptr: 表示所传入的顶点数组地址
            GLES20.glVertexAttribPointer(it, POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)
            // 通知 GL 程序使用指定的顶点属性索引
            GLES20.glEnableVertexAttribArray(it)
        }

        // 传纹理坐标给 fragment shader
        coordHandle = GLES20.glGetAttribLocation(programObjId, "a_TexCoord").also {
            GLES20.glVertexAttribPointer(it, POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, coordBuffer)
            GLES20.glEnableVertexAttribArray(it)
        }

        // get handle to shape's transformation matrix
        mvpMatrixHandle = GLES20.glGetUniformLocation(programObjId, "uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 传纹理的像素格式给fragment shader
        val yuvType = GLES20.glGetUniformLocation(programObjId, "yuvType")
        checkGlError("glGetUniformLocation yuvType")
        GLES20.glUniform1i(yuvType, type.value)

        // type: 0 是 I420, 1 是 NV12
        val planarCount: Int
        if (type == GLRenderer.Yuv420Type.I420) {
            // I420 有3个平面
            planarCount = THREE_PLANAR
            sampleHandle[0] = GLES20.glGetUniformLocation(programObjId, "samplerY")
            sampleHandle[1] = GLES20.glGetUniformLocation(programObjId, "samplerU")
            sampleHandle[2] = GLES20.glGetUniformLocation(programObjId, "samplerV")
        } else {
            // NV12、NV21 有两个平面
            planarCount = TWO_PLANAR
            sampleHandle[0] = GLES20.glGetUniformLocation(programObjId, "samplerY")
            sampleHandle[1] = GLES20.glGetUniformLocation(programObjId, "samplerUV")
        }
        (0 until planarCount).forEach { i ->
            // 激活每一层纹理，绑定到创建的纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, planarTextureHandles[i])
            GLES20.glUniform1i(sampleHandle[i], i)
        }

        // 调用这个函数后，vertex shader 先在每个顶点执行一次，之后 fragment shader 在每个像素执行一次，
        // 绘制后的图像存储在 render buffer 中。
        // 参数:
        // 第 1 个参数：绘制的图形类型
        // 第 2 个参数：从顶点数组读取的起点
        // 第 3 个参数：从顶点数组读取的数据长度
        // https://www.jianshu.com/p/a772bfc2276b
        // 注意：这里一定要先上色，再绘制图形，否则会导致颜色在当前这一帧使用失败，要下一帧才能生效。
        // GL_TRIANGLE_STRIP: 相邻3个点构成一个三角形,不包括首位两个点。例如：ABC、BCD、CDE、DEF
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, SQUARE_VERTICES.size / POSITION_COMPONENT_COUNT)
        GLES20.glFinish()

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(coordHandle)
    }

    /**
     * 将图片数据绑定到纹理目标，适用于 UV 分量分开存储的（I420）
     * @param yPlane YUV 数据的 Y 分量
     * @param uPlane YUV 数据的 U 分量
     * @param vPlane YUV 数据的 V 分量
     * @param width YUV 图片宽度
     * @param height YUV 图片高度
     */
    fun feedTextureWithImageData(yPlane: ByteBuffer, uPlane: ByteBuffer, vPlane: ByteBuffer, width: Int, height: Int) {
        //根据YUV编码的特点，获得不同平面的基址
        textureYUV(yPlane, width, height, 0)
        textureYUV(uPlane, width / 2, height / 2, 1)
        textureYUV(vPlane, width / 2, height / 2, 2)
    }

    /**
     * 将图片数据绑定到纹理目标，适用于 UV 分量交叉存储的（NV12、NV21）
     * @param yPlane YUV 数据的Y分量
     * @param uvPlane YUV 数据的UV分量
     * @param width YUV 图片宽度
     * @param height YUV 图片高度
     */
    fun feedTextureWithImageData(yPlane: ByteBuffer, uvPlane: ByteBuffer, width: Int, height: Int) {
        //根据YUV编码的特点，获得不同平面的基址
        textureYUV(yPlane, width, height, 0)
        textureNV12(uvPlane, width / 2, height / 2, 1)
    }

    /**
     * 将图片数据绑定到纹理目标，适用于 UV 分量分开存储的（I420）
     * @param imageData YUV 数据的 Y/U/V 分量
     * @param width YUV 图片宽度
     * @param height YUV 图片高度
     */
    private fun textureYUV(imageData: ByteBuffer, width: Int, height: Int, index: Int) {
        // 将纹理对象绑定到纹理目标
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, planarTextureHandles[index])
        // 设置放大和缩小时，纹理的过滤选项为：线性过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // 设置纹理X,Y轴的纹理环绕选项为：边缘像素延伸
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        // 加载图像数据到纹理，GL_LUMINANCE指明了图像数据的像素格式为只有亮度，虽然第三个和第七个参数都使用了GL_LUMINANCE，
        // 但意义是不一样的，前者指明了纹理对象的颜色分量成分，后者指明了图像数据的像素格式
        // 获得纹理对象后，其每个像素的r,g,b,a值都为相同，为加载图像的像素亮度，在这里就是YUV某一平面的分量值
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,                 // 指定要 Mipmap 的等级
            GLES20.GL_LUMINANCE,    // GPU 内部格式，告诉OpenGL内部用什么格式存储和使用这个纹理数据。亮度，灰度图（这里就是只取一个亮度的颜色通道的意思，因这里只取yuv其中一个分量）
            width,                  // 加载的纹理宽度。最好为2的次幂
            height,                 // 加载的纹理高度。最好为2的次幂
            0,               // 纹理边框
            GLES20.GL_LUMINANCE,    // 数据的像素格式 亮度，灰度图
            GLES20.GL_UNSIGNED_BYTE, // 一个像素点存储的数据类型
            imageData               // 纹理的数据
        )
    }

    /**
     * 将图片数据绑定到纹理目标，适用于 UV 分量交叉存储的（NV12、NV21）
     * @param imageData YUV 数据的 UV 分量
     * @param width YUV 图片宽度
     * @param height YUV 图片高度
     */
    @Suppress("SameParameterValue")
    private fun textureNV12(imageData: ByteBuffer, width: Int, height: Int, index: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, planarTextureHandles[index])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,                    // 指定要 Mipmap 的等级
            GLES20.GL_LUMINANCE_ALPHA, // GPU 内部格式，告诉OpenGL内部用什么格式存储和使用这个纹理数据。亮度，灰度图（这里就是只取一个亮度的颜色通道的意思，因这里只取yuv其中一个分量）
            width,                     // 加载的纹理宽度。最好为2的次幂
            height,                    // 加载的纹理高度。最好为2的次幂
            0,                  // 纹理边框
            GLES20.GL_LUMINANCE_ALPHA, // 数据的像素格式 亮度，灰度图
            GLES20.GL_UNSIGNED_BYTE,   // 一个像素点存储的数据类型
            imageData                  // 纹理的数据
        )
    }

    /**
     * 创建两个缓冲区用于保存顶点 -> 屏幕顶点和纹理顶点
     *
     * OpenGL的世界坐标系是 [-1, -1, 1, 1]，纹理的坐标系为 [0, 0, 1, 1]
     *
     * @param vert 屏幕顶点数据
     */
    fun createBuffers(vert: FloatArray) {
        vertexBuffer = ByteBuffer.allocateDirect(vert.size * BYTES_PER_FLOAT).run {
            // Use the device hardware's native byte order
            order(ByteOrder.nativeOrder())

            // Create a floating point buffer from the ByteBuffer
            asFloatBuffer().apply {
                // Add the coordinates to the FloatBuffer
                put(vert)
                // Set the buffer to read the first coordinate
                position(0)
            }
        }

        if (coordBuffer == null) {
            coordBuffer = ByteBuffer.allocateDirect(coordVertices.size * BYTES_PER_FLOAT).run {
                // Use the device hardware's native byte order
                order(ByteOrder.nativeOrder())

                // Create a floating point buffer from the ByteBuffer
                asFloatBuffer().apply {
                    // Add the coordinates to the FloatBuffer
                    put(coordVertices)
                    // Set the buffer to read the first coordinate
                    position(0)
                }
            }
        }
        LogContext.log.d(TAG, "createBuffers vertex_buffer=$vertexBuffer coord_buffer=$coordBuffer", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    /**
     * 检查GL操作是否有error
     * @param op 当前检查前所做的操作
     */
    private fun checkGlError(op: String) {
        var error: Int = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            LogContext.log.e(TAG, "checkGlError. $op: glError $error", outputType = ILog.OUTPUT_TYPE_SYSTEM)
            error = GLES20.glGetError()
        }
    }
}