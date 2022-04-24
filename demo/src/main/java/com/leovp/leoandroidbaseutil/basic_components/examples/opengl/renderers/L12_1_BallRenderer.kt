package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.leovp.leoandroidbaseutil.R
import com.leovp.lib_common_android.exts.readAssetsFileAsString
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.THREE_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Point 绘制
 */
class L12_1_BallRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L12_1_BallRenderer::class.java.simpleName

    companion object {
        private const val STEP = 5
    }

    private lateinit var pointData: FloatArray
    private lateinit var vertexBuffer: FloatBuffer

    private var aPositionLocation = 0
    private var uMatrixLocation: Int = 0

    private val viewMatrix = FloatArray(16)
    private val projectMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private fun createBallPointData(): FloatArray {
        // 球以 (0,0,0) 为中心，以 R 为半径，则球上任意一点的坐标为
        // (R * cos(a) * sin(b), y0 = R * sin(a), R * cos(a) * cos(b))
        // 其中，a 为圆心到点的线段与 xz 平面的夹角，b 为圆心到点的线段在 xz 平面的投影与 z 轴的夹角
        val data = ArrayList<Float>()
        var r1: Float
        var r2: Float
        var h1: Float
        var h2: Float
        var sin: Float
        var cos: Float
        for (i in -90 until 90 + STEP step STEP) {
            r1 = cos(i * Math.PI / 180.0).toFloat()
            r2 = cos((i + STEP) * Math.PI / 180.0).toFloat()
            h1 = sin(i * Math.PI / 180.0).toFloat()
            h2 = sin((i + STEP) * Math.PI / 180.0).toFloat()
            // 固定纬度, 360 度旋转遍历一条纬线
            val step2: Int = STEP * 2
            for (j in 0 until 360 + STEP step step2) {
                cos = cos(j * Math.PI / 180.0).toFloat()
                sin = (-sin(j * Math.PI / 180.0)).toFloat()
                data.add(r2 * cos)
                data.add(h2)
                data.add(r2 * sin)
                data.add(r1 * cos)
                data.add(h1)
                data.add(r1 * sin)
            }
        }
        return data.toFloatArray()
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        makeProgram(ctx.readAssetsFileAsString(R.raw.ball_vertex), ctx.readAssetsFileAsString(R.raw.ball_fragment))

        pointData = createBallPointData()
        vertexBuffer = createFloatBuffer(pointData)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)

        // 计算宽高比
        val ratio: Float = width.toFloat() / height
        // 设置透视投影
        Matrix.frustumM(projectMatrix, 0,
            -ratio, ratio, -1f, 1f,
            3f, 20f)
        // 设置相机位置
        Matrix.setLookAtM(viewMatrix, 0,
            1.0f, -10.0f, -4.0f,
            0f, 0f, 0f,
            0f, 1.0f, 0.0f)
        // 计算变换矩阵
        Matrix.multiplyMM(mvpMatrix, 0, projectMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(unused: GL10) {
        // 使用 glClearColor 设置的颜色，刷新 Surface
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        uMatrixLocation = getUniform("u_Matrix")
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, mvpMatrix, 0)

        aPositionLocation = getAttrib("a_Position")

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(aPositionLocation, THREE_DIMEN_POS_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // GLES20.GL_TRIANGLE_STRIP
        // GLES20.GL_TRIANGLES
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, pointData.size / THREE_DIMEN_POS_COMPONENT_COUNT)
        GLES20.glDisableVertexAttribArray(aPositionLocation)

    }
}