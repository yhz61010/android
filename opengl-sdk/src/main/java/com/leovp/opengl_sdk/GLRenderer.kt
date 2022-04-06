@file:Suppress("unused")

package com.leovp.opengl_sdk

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.BufferUtil
import com.leovp.opengl_sdk.util.checkGlError
import com.leovp.opengl_sdk.util.feedTextureWithImageData
import com.leovp.opengl_sdk.util.readAssetsFileAsString
import java.nio.ByteBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer 渲染类
 *
 * @see [Android OpenGL处理YUV数据（I420、NV12、NV21）](https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6)
 */
class GLRenderer(private val context: Context) : AbsRenderer() {
    override fun getTagName() = "GLRenderer"

    var keepRatio: Boolean = true

    // GLSurfaceView 宽度
    private var screenWidth: Int = 0

    // GLSurfaceView 高度
    private var screenHeight: Int = 0

    // 预览 YUV 数据宽度
    private var videoWidth: Int = 0

    // 预览 YUV 数据高度
    private var videoHeight: Int = 0

    // mvpMatrix is an abbreviation for "Model View Projection Matrix"
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    // y分量数据
    private var y: ByteBuffer = ByteBuffer.allocate(0)

    // u分量数据
    private var u: ByteBuffer = ByteBuffer.allocate(0)

    // v分量数据
    private var v: ByteBuffer = ByteBuffer.allocate(0)

    // uv分量数据
    private var uv: ByteBuffer = ByteBuffer.allocate(0)

    // YUV数据格式 0 -> I420  1 -> NV12  2 -> NV21
    private var yuv420Type: Yuv420Type = Yuv420Type.I420

    // 标识 GLSurfaceView 是否准备好
    private var hasVisibility = false

    //  Called once to set up the view's OpenGL ES environment.
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        LogContext.log.w(tag, "=====> GLRenderer onSurfaceCreated()", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        // Set the background frame color
        // 设置刷新屏幕时候使用的颜色值,顺序是RGBA，值的范围从0~1。
        // 这里不会立刻刷新，只有在 GLES20.glClear 调用时使用该颜色值才刷新。
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        makeAndUseProgram(context.readAssetsFileAsString(R.raw.vertex_shader), context.readAssetsFileAsString(R.raw.fragment_shader))

        // 生成纹理句柄
        GLES20.glGenTextures(THREE_PLANAR, planarTextureHandles)
        checkGlError("glGenTextures")
        LogContext.log.d(tag, "=====> GLProgram created", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    //  Called if the geometry of the view changes, for example when the device's screen orientation changes.
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        LogContext.log.w(tag, "=====> GLRenderer onSurfaceChanged()=$width x $height videoWidth=$videoWidth x $videoHeight", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        super.onSurfaceChanged(unused, width, height)
        GLES20.glViewport(0, 0, width, height)

        screenWidth = width
        screenHeight = height
        val ratio: Float = width.toFloat() / height.toFloat()

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        // Attention: "-ratio, ratio, -1f, 1f" means keep screen ratio. Width is fixed.
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)

        if (videoWidth > 0 && videoHeight > 0) {
            createFloatBuffers(videoWidth, videoHeight, keepRatio, screenWidth, screenHeight)
        }
        hasVisibility = true
        LogContext.log.d(tag, "onSurfaceChanged: $width*$height", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    //  Called for each redraw of the view.
    override fun onDrawFrame(unused: GL10) {
        synchronized(this) {
            if (y.capacity() > 0) {
                y.position(0)
                if (yuv420Type == Yuv420Type.I420) {
                    u.position(0)
                    v.position(0)
                    feedTextureWithImageData(y, u, v, videoWidth, videoHeight, planarTextureHandles)
                } else {
                    uv.position(0)
                    feedTextureWithImageData(y, uv, videoWidth, videoHeight, planarTextureHandles)
                }
                // Redraw background color
                // 使用 glClearColor 设置的颜色，刷新 Surface
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                // https://blog.csdn.net/yu540135101/article/details/102923212
                //                Matrix.rotateM(viewMatrix, 0, -90f, 1f, 0f, 0f)

                // Set the camera position (View matrix)
                Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 1f, 0.0f, 0.0f)

                // Calculate the projection and view transformation
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                try {
                    drawTexture(mvpMatrix, yuv420Type)
                } catch (e: Exception) {
                    LogContext.log.e(tag, e, outputType = ILog.OUTPUT_TYPE_SYSTEM)
                }
            }
        }
    }

    /**
     * 设置渲染的YUV数据的宽高
     * @param width 宽度
     * @param height 高度
     */
    fun setVideoDimension(width: Int, height: Int, screenWidth: Int, screenAvailableHeight: Int) {
        LogContext.log.i(tag, "setVideoDimension width=$width x $width screen=$screenWidth x $screenAvailableHeight", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        if (width > 0 && height > 0) {
            // 调整比例
            createFloatBuffers(width, height, keepRatio, screenWidth, screenAvailableHeight)

            if (width != videoWidth && height != videoHeight) {
                this.videoWidth = width
                this.videoHeight = height
                val yArraySize = width * height
                val uArraySize = yArraySize / 4
                synchronized(this) {
                    y = ByteBuffer.allocate(yArraySize)
                    u = ByteBuffer.allocate(uArraySize)
                    v = ByteBuffer.allocate(uArraySize)
                    uv = ByteBuffer.allocate(uArraySize * 2)
                }
            }
        }
    }

    /**
     * 预览 YUV 格式数据
     * @param yuvData YUV 格式的数据
     * @param format YUV 数据的格式 0 -> I420  1 -> NV12  2 -> NV21
     */
    fun feedData(yuvData: ByteArray, format: Yuv420Type = Yuv420Type.I420) {
        synchronized(this) {
            if (hasVisibility) {
                this.yuv420Type = format
                if (format == Yuv420Type.I420) {
                    y.clear()
                    u.clear()
                    v.clear()
                    y.put(yuvData, 0, videoWidth * videoHeight)
                    u.put(yuvData, videoWidth * videoHeight, videoWidth * videoHeight / 4)
                    v.put(yuvData, videoWidth * videoHeight * 5 / 4, videoWidth * videoHeight / 4)
                } else {
                    y.clear()
                    uv.clear()
                    y.put(yuvData, 0, videoWidth * videoHeight)
                    uv.put(yuvData, videoWidth * videoHeight, videoWidth * videoHeight / 2)
                }
            }
        }
    }

    // 0 -> I420 (YUV420P)  YYYYYYYY UUVV
    // 1 -> NV12 (YUV420SP) YYYYYYYY UVUV
    // 2 -> NV21 (YUV420SP) YYYYYYYY VUVU
    enum class Yuv420Type(val value: Int) {
        I420(0),
        NV12(1),
        NV21(2);

        companion object {
            fun getType(value: Int) = values().first { it.value == value }
        }
    }

    // ====================

    private var planarTextureHandles = IntBuffer.wrap(IntArray(THREE_PLANAR))
    private val sampleHandle = IntArray(3)

    // handles
    private var positionHandle = -1
    private var coordHandle = -1
    private var mvpMatrixHandle: Int = -1

    private fun drawTexture(mvpMatrix: FloatArray, type: Yuv420Type) {
        /*
         * 传入顶点坐标数组给顶点着色器
         *
         * get handle for "a_Position" and "a_TexCoord"
         *
         * OpenGL的世界坐标系是 [-1, -1, 1, 1]，纹理的坐标系为 [0, 0, 1, 1]
         */
        positionHandle = getAttrib("a_Position").also {
            // Parameters:
            //   indx: 顶点着色器中 a_Position 变量的引用。
            //   size: 表示数组中 size 个数字表示一个顶点。
            //   GL_FLOAT: 表示数据类型是浮点数。
            //   normalized: 表示是否进行归一化。
            //   stride: 表示stride（跨距），在数组表示多种属性的时候使用到。数组中每个顶点相关属性占的Byte值。
            //   ptr: 表示所传入的顶点数组地址
            GLES20.glVertexAttribPointer(it, POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, squareVertices)
            // 通知 GL 程序使用指定的顶点属性索引
            GLES20.glEnableVertexAttribArray(it)
        }

        // 传纹理坐标给 fragment shader
        coordHandle = getAttrib("a_TexCoord").also {
            GLES20.glVertexAttribPointer(it, POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, coordVertices)
            GLES20.glEnableVertexAttribArray(it)
        }

        // get handle to shape's transformation matrix
        mvpMatrixHandle = getUniform("uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 传纹理的像素格式给 fragment shader
        val yuvType = getUniform("yuvType")
        checkGlError("glGetUniformLocation yuvType")
        GLES20.glUniform1i(yuvType, type.value)

        // type: 0 是 I420, 1 是 NV12
        val planarCount: Int
        if (type == Yuv420Type.I420) {
            // I420 有3个平面
            planarCount = THREE_PLANAR
            sampleHandle[0] = getUniform("samplerY")
            sampleHandle[1] = getUniform("samplerU")
            sampleHandle[2] = getUniform("samplerV")
        } else {
            // NV12，NV21 有两个平面
            planarCount = TWO_PLANAR
            sampleHandle[0] = getUniform("samplerY")
            sampleHandle[1] = getUniform("samplerUV")
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
     * 调整渲染纹理的缩放比例
     * @param width YUV数据宽度
     * @param height YUV数据高度
     */
    private fun createFloatBuffers(width: Int, height: Int, keepRatio: Boolean, screenWidth: Int, screenHeight: Int) {
        LogContext.log.d(tag, "createBuffers($width, $height, $keepRatio) screen=$screenWidth x $screenHeight", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        if (!keepRatio) {
            squareVertices = BufferUtil.createFloatBuffers(SQUARE_VERTICES)
        }

        if (screenWidth > 0 && screenHeight > 0) {
            val screenRatio = screenHeight.toFloat() / screenWidth.toFloat()
            val specificRatio = height.toFloat() / width.toFloat()
            when {
                screenRatio == specificRatio -> {
                    squareVertices = BufferUtil.createFloatBuffers(SQUARE_VERTICES)
                }
                screenRatio < specificRatio  -> {
                    val widthScale = screenRatio / specificRatio
                    squareVertices = BufferUtil.createFloatBuffers(
                        floatArrayOf(
                            -widthScale, -1.0f,
                            widthScale, -1.0f,
                            -widthScale, 1.0f,
                            widthScale, 1.0f
                        )
                    )
                }
                else                         -> {
                    val heightScale = specificRatio / screenRatio
                    squareVertices = BufferUtil.createFloatBuffers(
                        floatArrayOf(
                            -1.0f, -heightScale,
                            1.0f, -heightScale,
                            -1.0f, heightScale,
                            1.0f, heightScale
                        )
                    )
                }
            }
        }
    }
}