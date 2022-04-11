package com.leovp.opengl_sdk.util

import android.opengl.GLES20
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import java.nio.ByteBuffer
import java.nio.IntBuffer

// https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6
// https://blog.csdn.net/sinat_23092639/article/details/103046553
// https://blog.csdn.net/mengks1987/article/details/104186060

private const val TAG = "OpenGL"

/**
 * 将图片数据绑定到纹理目标，适用于 UV 分量分开存储的（I420）
 * @param imageData YUV 数据的 Y/U/V 分量
 * @param width YUV 图片宽度
 * @param height YUV 图片高度
 */
fun textureYUV(imageData: ByteBuffer, width: Int, height: Int, texture: Int) {
    // 将纹理对象绑定到纹理目标
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    // 设置放大和缩小时，纹理的过滤选项为：线性过滤
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    // 设置纹理X,Y轴的纹理环绕选项为：边缘像素延伸
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    // 加载图像数据到纹理，GL_LUMINANCE 指明了图像数据的像素格式为只有亮度，虽然第三个和第七个参数都使用了 GL_LUMINANCE，
    // 但意义是不一样的，前者指明了纹理对象的颜色分量成分，后者指明了图像数据的像素格式
    // 获得纹理对象后，其每个像素的 r,g,b,a 值都为相同，为加载图像的像素亮度，在这里就是YUV某一平面的分量值
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        0,                   // 指定要 Mipmap 的等级
        GLES20.GL_LUMINANCE,      // GPU 内部格式，告诉 OpenGL 内部用什么格式存储和使用这个纹理数据。亮度，灰度图（这里就是只取一个亮度的颜色通道的意思，因这里只取yuv其中一个分量）
        width,                    // 加载的纹理宽度。最好为2的次幂
        height,                   // 加载的纹理高度。最好为2的次幂
        0,                 // 纹理边框
        GLES20.GL_LUMINANCE,     // 数据的像素格式 亮度，灰度图
        GLES20.GL_UNSIGNED_BYTE, // 一个像素点存储的数据类型
        imageData                // 纹理的数据
    )
}

/**
 * 将图片数据绑定到纹理目标，适用于 UV 分量交叉存储的（NV12、NV21）
 * @param imageData YUV 数据的 UV 分量
 * @param width YUV 图片宽度
 * @param height YUV 图片高度
 */
@Suppress("SameParameterValue")
fun textureNV12(imageData: ByteBuffer, width: Int, height: Int, texture: Int) {
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
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
 * 将图片数据绑定到纹理目标，适用于 UV 分量分开存储的（I420）
 * @param yPlane YUV 数据的 Y 分量
 * @param uPlane YUV 数据的 U 分量
 * @param vPlane YUV 数据的 V 分量
 * @param width YUV 图片宽度
 * @param height YUV 图片高度
 */
fun feedTextureWithImageData(yPlane: ByteBuffer, uPlane: ByteBuffer, vPlane: ByteBuffer, width: Int, height: Int, planarTexture: IntBuffer) {
    //根据YUV编码的特点，获得不同平面的基址
    textureYUV(yPlane, width, height, planarTexture[0])
    textureYUV(uPlane, width / 2, height / 2, planarTexture[1])
    textureYUV(vPlane, width / 2, height / 2, planarTexture[2])
}

/**
 * 将图片数据绑定到纹理目标，适用于 UV 分量交叉存储的（NV12、NV21）
 * @param yPlane YUV 数据的Y分量
 * @param uvPlane YUV 数据的UV分量
 * @param width YUV 图片宽度
 * @param height YUV 图片高度
 */
fun feedTextureWithImageData(yPlane: ByteBuffer, uvPlane: ByteBuffer, width: Int, height: Int, planarTexture: IntBuffer) {
    //根据YUV编码的特点，获得不同平面的基址
    textureYUV(yPlane, width, height, planarTexture[0])
    textureNV12(uvPlane, width / 2, height / 2, planarTexture[1])
}

/**
 * 编译着色器程序
 * @param type GLES20.GL_VERTEX_SHADER(0X8B31=35633)   -> vertex shader
 *             GLES20.GL_FRAGMENT_SHADER(0X8B30=35632) -> fragment shader
 * @param shaderCode 着色器程序代码
 *
 * https://www.jianshu.com/p/a772bfc2276b
 */
fun compileShader(type: Int, shaderCode: String): Int {
    // Create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)

    // 1. 创建一个新的着色器对象
    val shaderId = GLES20.glCreateShader(type)

    // 2. 获取创建状态
    // 在 OpenGL 中，都是通过整型值去作为 OpenGL 对象的引用。之后进行操作的时候都是将这个整型值传回给 OpenGL 进行操作。
    // 返回值 0 代表着创建对象失败。
    if (shaderId == GLES20.GL_FALSE) { // Failed
        LogContext.log.e(TAG, "Could not create new shader[$type].", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        return GLES20.GL_FALSE
    }

    // Add the source code to the shader and compile it
    // 3. 将着色器代码上传到着色器对象中
    GLES20.glShaderSource(shaderId, shaderCode)

    // 4. 编译着色器对象
    GLES20.glCompileShader(shaderId)

    //    LogContext.log.i(TAG, "Results of compiling.\n${GLES20.glGetShaderInfoLog(shaderId)}", outputType = ILog.OUTPUT_TYPE_SYSTEM)

    // 5. 获取编译状态：OpenGL 将想要获取的值放入长度为1的数组的首位
    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

    // 6.验证编译状态
    if (compileStatus[0] != GLES20.GL_TRUE) { // Failed
        LogContext.log.e(TAG, "Compilation of shader[$type] failed.", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        // 如果编译失败，则删除创建的着色器对象
        GLES20.glDeleteShader(shaderId)

        // 7.返回着色器对象：失败，为0
        return GLES20.GL_FALSE
    }

    // 7. 返回着色器对象：成功，非0
    return shaderId
}

/**
 * @return OpenGL ES Program ID
 */
fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
    // 1. 创建一个 OpenGL ES 程序对象
    // Create empty OpenGL ES Program
    val programObjId = GLES20.glCreateProgram()
    LogContext.log.i(TAG, "linkProgram() programObjId=$programObjId", outputType = ILog.OUTPUT_TYPE_SYSTEM)

    // 2. 检查创建状态
    checkGlError("glCreateProgram")
    // 返回值 0 代表着创建对象失败。
    if (programObjId == GLES20.GL_FALSE) { // Failed
        LogContext.log.e(TAG, "Could not create new program.", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        return GLES20.GL_FALSE
    }

    // 3. 将顶点着色器依附到 OpenGL ES Program 对象
    // Add the vertex shader to program
    GLES20.glAttachShader(programObjId, vertexShaderId)
    // 3. 将片段着色器依附到 OpenGL ES Program 对象
    // Add the fragment shader to program
    GLES20.glAttachShader(programObjId, fragmentShaderId)

    // Creates OpenGL ES program executables
    // 4. 将两个着色器链接到 OpenGL ES Program 对象
    GLES20.glLinkProgram(programObjId)

    // 5. 获取链接状态：OpenGL ES 将想要获取的值放入长度为1的数组的首位
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(programObjId, GLES20.GL_LINK_STATUS, linkStatus, 0)

    // 6. 验证链接状态
    if (linkStatus[0] != GLES20.GL_TRUE) {
        LogContext.log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(programObjId)} linkStatus=${linkStatus[0]}", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        // 链接失败则删除程序对象
        GLES20.glDeleteProgram(programObjId)

        // 7. 返回程序对象：失败，为0
        return GLES20.GL_FALSE
    }

    // 7. 返回程序对象：成功，非0
    return programObjId
}

/**
 * 检查 GL 操作是否有 error
 * @param op 当前检查前所做的操作
 */
fun checkGlError(op: String): Int {
    var error: Int = GLES20.glGetError()
    while (error != GLES20.GL_NO_ERROR) {
        LogContext.log.e(TAG, "checkGlError. $op: glError $error", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        error = GLES20.glGetError()
    }
    return error
}

/**
 * 验证OpenGL程序对象状态
 *
 * @param programObjectId OpenGL程序ID
 * @return 是否可用
 */
fun validateProgram(programObjectId: Int): Boolean {
    GLES20.glValidateProgram(programObjectId)
    val validateStatus = IntArray(1)
    GLES20.glGetProgramiv(programObjectId, GLES20.GL_VALIDATE_STATUS, validateStatus, 0)
    LogContext.log.i(TAG, "Results of validating program: ${validateStatus[0]}\nLog:${GLES20.glGetProgramInfoLog(programObjectId)}", outputType = ILog.OUTPUT_TYPE_SYSTEM)
    return validateStatus[0] != 0
}

/**
 * 顶点着色器程序
 * vertex shader 在每个顶点上都执行一次，通过不同世界的坐标系转化定位顶点的最终位置。
 * 它可以传递数据给 fragment shader，如纹理坐标、顶点坐标，变换矩阵等。
 *
 * https://www.jianshu.com/p/a772bfc2276b
 * https://blog.csdn.net/mengks1987/article/details/104186060
 * https://mp.weixin.qq.com/s?__biz=MzI0NzI0NDY2OQ==&mid=2652749998&idx=1&sn=73cb65f64e6c7ff6b823919d9c016b13&chksm=f25b92bcc52c1baa2c482431f111f98da33832d8fb2b1385e6fc197847b690de137601e23f68&token=1476949111&lang=zh_CN&scene=21#wechat_redirect
 *
 * attribute：只能用在顶点着色器中，一般用于表示顶点数据。
 *            一般用于各个顶点各不相同的量。如顶点位置、纹理坐标、法向量、颜色等等。
 * uniform  ：可用于顶点和片段着色器，一般用于对于物体中所有顶点或者所有的片段都相同的量。比如光源位置、统一变换矩阵、颜色等。
 * varying  ：可用于顶点和片段着色器，一般用于在着色器之间做数据传递。
 *            通常，varying 在顶点着色器中进行计算，片段着色器使用 varying 计算后的值。
 *            表示易变量，一般用于顶点着色器传递到片段着色器的量。
 * vec4     ：4个分量的向量：x、y、z、w
 */
//internal const val vertexShaderCode = """
//    uniform mat4 uMVPMatrix;
//    attribute vec4 a_Position; // 输入的顶点坐标，会在程序指定将数据输入到该字段
//    attribute vec2 a_TexCoord; // 输入的纹理坐标(2个分量，S 和 T 坐标)，会在程序指定将数据输入到该字段
//    varying vec2 v_TexCoord;   // 输出的纹理坐标，输入到片段着色器
//    void main() {
//        v_TexCoord = a_TexCoord;
//        gl_Position = a_Position; // gl_Position：GL中默认定义的输出变量，决定了当前顶点的最终位置
//
//        // 这里其实是将上下翻转过来（因为安卓图片会自动上下翻转，所以转回来。也可以在顶点坐标中就上下翻转）
//        // v_TexCoord = vec2(a_TexCoord.x, 1.0 - a_TexCoord.y);
//        // gl_Position = uMVPMatrix * a_Position;
//    }
//"""

/**
 * 片段着色器程序。
 * fragment shader 在每个像素上都会执行一次，通过插值确定像素的最终显示颜色。
 *
 * 作用：GPU 将 YUV 转 RGB
 *
 * https://www.jianshu.com/p/a772bfc2276b
 * // v_TexCoord = vec2((tv.x - 0.5) * (screenWidth / screenHeight), v_TexCoord.y - 0.5) * mat2(cos_factor, sin_factor, -sin_factor, cos_factor) + 0.5;
 * https://github.com/gre/gl-react-native-v2/issues/107#issuecomment-244106961
 * https://stackoverflow.com/a/37414763
 * https://github.com/kenneycode/OpenGLES2.0SamplesForAndroid/blob/0864ee22db/app/src/main/java/com/kenneycode/samples/renderer/SampleVertexShaderRenderer.kt
 * https://blog.csdn.net/junzia/article/details/68952183
 */
//internal const val fragmentShaderCode = """
//    // 定义所有浮点数据类型的默认精度。
//    // 有 lowp、mediump、highp 三种，但只有部分硬件支持片段着色器使用 highp。(顶点着色器默认 highp)
//    // https://www.jianshu.com/p/a772bfc2276b
//    // 注意: 在声明 vec 向量的时候，一定要标识其精度类型，否则会导致部分机型花屏，如红米 note2
//    precision mediump float;
//    uniform sampler2D samplerY;
//    uniform sampler2D samplerU;
//    uniform sampler2D samplerV;
//    uniform sampler2D samplerUV;
//    uniform int yuvType;
//    // 接收从顶点着色器、光栅化处理传来的纹理坐标数据
//    varying vec2 v_TexCoord;
//    void main() {
//        vec4 color = vec4((texture2D(samplerY, v_TexCoord).r - 16./255.) * 1.164);
//        vec4 U; vec4 V;
//        if (yuvType == 0) { // 0 -> I420
//          U = vec4(texture2D(samplerU, v_TexCoord).r - 128./255.);
//          V = vec4(texture2D(samplerV, v_TexCoord).r - 128./255.);
//        } else if (yuvType == 1) { // 1 -> NV12
//          U = vec4(texture2D(samplerUV, v_TexCoord).r - 128./255.);
//          V = vec4(texture2D(samplerUV, v_TexCoord).a - 128./255.);
//        } else { // 2 -> NV21
//          U = vec4(texture2D(samplerUV, v_TexCoord).a - 128./255.);
//          V = vec4(texture2D(samplerUV, v_TexCoord).r - 128./255.);
//        }
//        color += V * vec4(1.596, -0.813, 0, 0);
//        color += U * vec4(0, -0.392, 2.017, 0);
//        color.a = 1.0;
//        // gl_FragColor：GL中默认定义的输出变量，决定了当前片段的最终颜色
//        gl_FragColor = color;
//    }
//"""