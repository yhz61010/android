@file:Suppress("unused")

package com.leovp.opengl_sdk

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer 渲染类
 *
 * @see [Android OpenGL处理YUV数据（I420、NV12、NV21）](https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6)
 */
class GLRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "GLRenderer"
    }

    var keepRatio: Boolean = true

    private lateinit var program: GLProgram

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
        LogContext.log.w(TAG, "=====> GLRenderer onSurfaceCreated()", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        // Set the background frame color
        // 设置刷新屏幕时候使用的颜色值,顺序是RGBA，值的范围从0~1。
        // 这里不会立刻刷新，只有在 GLES20.glClear 调用时使用该颜色值才刷新。
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 配置OpenGL ES 环境
        program = GLProgram(context)
        LogContext.log.d(TAG, "=====> GLProgram created", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    //  Called if the geometry of the view changes, for example when the device's screen orientation changes.
    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        LogContext.log.w(TAG, "=====> GLRenderer onSurfaceChanged()=$width x $height", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        GLES20.glViewport(0, 0, width, height)

        screenWidth = width
        screenHeight = height
        val ratio: Float = width.toFloat() / height.toFloat()

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        // Attention: "-ratio, ratio, -1f, 1f" means keep screen ratio. Width is fixed.
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)

        if (videoWidth > 0 && videoHeight > 0) {
            createBuffers(videoWidth, videoHeight, keepRatio)
        }
        hasVisibility = true
        LogContext.log.d(TAG, "onSurfaceChanged: $width*$height", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    }

    //  Called for each redraw of the view.
    override fun onDrawFrame(unused: GL10) {
        synchronized(this) {
            if (y.capacity() > 0) {
                y.position(0)
                if (yuv420Type == Yuv420Type.I420) {
                    u.position(0)
                    v.position(0)
                    program.feedTextureWithImageData(y, u, v, videoWidth, videoHeight)
                } else {
                    uv.position(0)
                    program.feedTextureWithImageData(y, uv, videoWidth, videoHeight)
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
                    program.drawTexture(mvpMatrix, yuv420Type)
                } catch (e: Exception) {
                    LogContext.log.w(TAG, e, outputType = ILog.OUTPUT_TYPE_SYSTEM)
                }
            }
        }
    }

    /**
     * 设置渲染的YUV数据的宽高
     * @param width 宽度
     * @param height 高度
     */
    fun setVideoDimension(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            // 调整比例
            createBuffers(width, height, keepRatio)

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
     * 调整渲染纹理的缩放比例
     * @param width YUV数据宽度
     * @param height YUV数据高度
     */
    private fun createBuffers(width: Int, height: Int, keepRatio: Boolean) {
        LogContext.log.d(TAG, "createBuffers($width, $height, $keepRatio) screen=$screenWidth x $screenHeight", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        if (!keepRatio && ::program.isInitialized) {
            program.createBuffers(GLProgram.SQUARE_VERTICES)
            return
        }

        if (screenWidth > 0 && screenHeight > 0) {
            val screenRatio = screenHeight.toFloat() / screenWidth.toFloat()
            val specificRatio = height.toFloat() / width.toFloat()
            when {
                screenRatio == specificRatio -> {
                    program.createBuffers(GLProgram.SQUARE_VERTICES)
                }
                screenRatio < specificRatio  -> {
                    val widthScale = screenRatio / specificRatio
                    program.createBuffers(
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
                    program.createBuffers(
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
}