@file:Suppress("unused")

package com.leovp.opengl_sdk

import android.content.Context
import android.opengl.GLES20
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.*
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_STRIDE_IN_FLOAT
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer 渲染类
 *
 * @see [Android OpenGL处理YUV数据（I420、NV12、NV21）](https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6)
 */
class GLRenderer(private val context: Context) : BaseRenderer() {
    override fun getTagName() = "GLRenderer"

    var keepRatio: Boolean = true

    // 预览 YUV 数据宽度
    private var videoWidth: Int = 0

    // 预览 YUV 数据高度
    private var videoHeight: Int = 0

    // mvpMatrix is an abbreviation for "Model View Projection Matrix"
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var pointCoord: FloatBuffer = createFloatBuffer(VerticesUtil.VERTICES_COORD)
    private val texVertices: FloatBuffer = createFloatBuffer(VerticesUtil.TEX_COORD)

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
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。
        // 这里不会立刻刷新，只有在 GLES20.glClear 调用时使用该颜色值才刷新。
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        makeProgram(context.readAssetsFileAsString(R.raw.yuv_vertex_shader), context.readAssetsFileAsString(R.raw.yuv_fragment_shader))

        // 生成纹理句柄
        GLES20.glGenTextures(THREE_PLANAR, planarTextureIntBuffer)
        checkGlError("glGenTextures")
        LogContext.log.d(tag, "=====> GLProgram created", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    private fun createCustomFloatBuffer(videoWidth: Int, videoHeight: Int, keepRatio: Boolean, screenWidth: Int, screenHeight: Int): FloatBuffer {
        return createFloatBuffer(createKeepRatioFloatArray(videoWidth, videoHeight, keepRatio, screenWidth, screenHeight))
    }

    //  Called if the geometry of the view changes, for example when the device's screen orientation changes.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        LogContext.log.w(tag, "=====> GLRenderer onSurfaceChanged()=$width x $height videoWidth=$videoWidth x $videoHeight", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        super.onSurfaceChanged(gl, width, height)

        // val ratio: Float = width.toFloat() / height.toFloat()

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        // Attention: "-ratio, ratio, -1f, 1f" means keep screen ratio. Width is fixed.
        // Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)

        if (videoWidth > 0 && videoHeight > 0) {
            pointCoord = createCustomFloatBuffer(videoWidth, videoHeight, keepRatio, outputWidth, outputHeight)
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
                    feedTextureWithImageData(y, u, v, videoWidth, videoHeight, planarTextureIntBuffer)
                } else {
                    uv.position(0)
                    feedTextureWithImageData(y, uv, videoWidth, videoHeight, planarTextureIntBuffer)
                }
                // Redraw background color
                // 使用 glClearColor 设置的颜色，刷新 Surface
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                // https://blog.csdn.net/yu540135101/article/details/102923212
                //                Matrix.rotateM(viewMatrix, 0, -90f, 1f, 0f, 0f)

                // Set the camera position (View matrix)
                //                Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 1f, 0.0f, 0.0f)

                // Calculate the projection and view transformation
                //                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                runCatching { drawTexture(mvpMatrix, yuv420Type) }.onFailure { LogContext.log.e(tag, it, outputType = ILog.OUTPUT_TYPE_SYSTEM) }
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
            pointCoord = createCustomFloatBuffer(width, height, keepRatio, screenWidth, screenAvailableHeight)

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

    // ====================

    private var planarTextureIntBuffer = IntBuffer.wrap(IntArray(THREE_PLANAR))
    private val sampleIntArray = IntArray(3)

    private var aPositionLocation = -1
    private var aTexCoordLocation = -1
    private var uMPVMatrix: Int = -1

    private fun drawTexture(mvpMatrix: FloatArray, type: Yuv420Type) {
        /*
         * 传入顶点坐标数组给顶点着色器
         *
         * get handle for "a_Position" and "a_TexCoord"
         *
         * OpenGL的世界坐标系是 [-1, -1, 1, 1]，纹理的坐标系为 [0, 0, 1, 1]
         */
        aPositionLocation = getAttrib("a_Position").also {
            // Parameters:
            //   indx: 顶点着色器中 a_Position 变量的引用。
            //   size: 表示数组中 size 个数字表示一个顶点。
            //   GL_FLOAT: 表示数据类型是浮点数。
            //   normalized: 表示是否进行归一化。
            //   stride: 表示stride（跨距），在数组表示多种属性的时候使用到。数组中每个顶点相关属性占的Byte值。
            //   ptr: 表示所传入的顶点数组地址
            GLES20.glVertexAttribPointer(it, TWO_DIMEN_POS_COMPONENT_COUNT, GLES20.GL_FLOAT, false, TWO_DIMEN_STRIDE_IN_FLOAT, pointCoord)
            // 通知 GL 程序使用指定的顶点属性索引
            GLES20.glEnableVertexAttribArray(it)
        }

        // 传纹理坐标给 fragment shader
        aTexCoordLocation = getAttrib("a_TexCoord").also {
            GLES20.glVertexAttribPointer(it, TWO_DIMEN_POS_COMPONENT_COUNT, GLES20.GL_FLOAT, false, TWO_DIMEN_STRIDE_IN_FLOAT, texVertices)
            GLES20.glEnableVertexAttribArray(it)
        }

        // Get handle to shape's transformation matrix
        uMPVMatrix = getUniform("uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(uMPVMatrix, 1, false, mvpMatrix, 0)

        // 传纹理的像素格式给 fragment shader
        val yuvType = getUniform("yuvType")
        checkGlError("glGetUniformLocation yuvType")
        GLES20.glUniform1i(yuvType, type.value)

        // type: 0 是 I420, 1 是 NV12
        val planarCount: Int
        if (type == Yuv420Type.I420) {
            // I420 有3个平面
            planarCount = THREE_PLANAR
            sampleIntArray[0] = getUniform("samplerY")
            sampleIntArray[1] = getUniform("samplerU")
            sampleIntArray[2] = getUniform("samplerV")
        } else {
            // NV12，NV21 有两个平面
            planarCount = TWO_PLANAR
            sampleIntArray[0] = getUniform("samplerY")
            sampleIntArray[1] = getUniform("samplerUV")
        }
        (0 until planarCount).forEach { i ->
            // 激活每一层纹理，绑定到创建的纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, planarTextureIntBuffer[i])
            GLES20.glUniform1i(sampleIntArray[i], i)
        }

        // 调用这个函数后，vertex shader 先在每个顶点执行一次，之后 fragment shader 在每个像素执行一次，
        // 绘制后的图像存储在 render buffer 中。
        // 参数:
        // 第 1 个参数：绘制的图形类型
        // 第 2 个参数：从顶点数组读取的起点
        // 第 3 个参数：从顶点数组读取的数据长度
        // https://www.jianshu.com/p/a772bfc2276b
        // 注意：这里一定要先上色，再绘制图形，否则会导致颜色在当前这一帧使用失败，要下一帧才能生效。
        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLE_STRIP: 每相邻三个顶点组成一个三角形，为一系列相接三角形构成。例如：ABC、BCD、CDE、DEF
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VerticesUtil.VERTICES_COORD.size / TWO_DIMEN_POS_COMPONENT_COUNT)
        GLES20.glFinish()

        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)
    }
}