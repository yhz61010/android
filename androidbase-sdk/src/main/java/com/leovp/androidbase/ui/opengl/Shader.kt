package com.leovp.androidbase.ui.opengl

import android.opengl.GLES20

// https://download.csdn.net/download/lkl22/11065372?spm=1001.2101.3001.6650.3&utm_medium=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&depth_1-utm_source=distribute.pc_relevant.none-task-download-2%7Edefault%7EBlogCommendFromBaidu%7ERate-3.pc_relevant_paycolumn_v3&utm_relevant_index=6
// https://blog.csdn.net/sinat_23092639/article/details/103046553
// https://blog.csdn.net/mengks1987/article/details/104186060

/**
 * 加载着色器程序
 * @param type GLES20.GL_VERTEX_SHADER   -> vertex shader
 *             GLES20.GL_FRAGMENT_SHADER -> fragment shader
 * @param shaderCode 着色器程序代码
 *
 * https://www.jianshu.com/p/a772bfc2276b
 */
internal fun loadShader(type: Int, shaderCode: String): Int {
    // Create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
    return GLES20.glCreateShader(type).also { shader ->
        // Add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
    }
}

/**
 * 顶点着色器程序
 * vertex shader 在每个顶点上都执行一次，通过不同世界的坐标系转化定位顶点的最终位置。
 * 它可以传递数据给 fragment shader，如纹理坐标、顶点坐标，变换矩阵等。
 *
 * https://www.jianshu.com/p/a772bfc2276b
 * https://blog.csdn.net/mengks1987/article/details/104186060
 *
 * attribute：一般用于各个顶点各不相同的量。如顶点位置、纹理坐标、法向量、颜色等等。
 * uniform  ：一般用于对于物体中所有顶点或者所有的片段都相同的量。比如光源位置、统一变换矩阵、颜色等。
 * varying  ：表示易变量，一般用于顶点着色器传递到片段着色器的量。
 * vec4     ：4个分量的向量：x、y、z、w
 */
internal const val vertexShaderCode = """
    uniform mat4 uMVPMatrix;
    attribute vec4 a_Position; // 输入的顶点坐标，会在程序指定将数据输入到该字段
    attribute vec2 a_TexCoord; // 输入的纹理坐标(2个分量，S 和 T 坐标)，会在程序指定将数据输入到该字段
    varying vec2 v_TexCoord;   // 输出的纹理坐标，输入到片段着色器
    void main() {
        v_TexCoord = a_TexCoord;
        gl_Position = a_Position; // gl_Position：GL中默认定义的输出变量，决定了当前顶点的最终位置
        
        // 这里其实是将上下翻转过来（因为安卓图片会自动上下翻转，所以转回来。也可以在顶点坐标中就上下翻转）
        // v_TexCoord = vec2(a_TexCoord.x, 1.0 - a_TexCoord.y);
        // gl_Position = uMVPMatrix * a_Position;
    }
"""

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
internal const val fragmentShaderCode = """
    // 定义所有浮点数据类型的默认精度。
    // 有 lowp、mediump、highp 三种，但只有部分硬件支持片段着色器使用 highp。(顶点着色器默认 highp)
    // https://www.jianshu.com/p/a772bfc2276b
    // 注意: 在声明 vec 向量的时候，一定要标识其精度类型，否则会导致部分机型花屏，如红米 note2
    precision mediump float;
    uniform sampler2D samplerY;
    uniform sampler2D samplerU;
    uniform sampler2D samplerV;
    uniform sampler2D samplerUV;
    uniform int yuvType;
    // 接收从顶点着色器、光栅化处理传来的纹理坐标数据
    varying vec2 v_TexCoord;
    void main() {
        vec4 color = vec4((texture2D(samplerY, v_TexCoord).r - 16./255.) * 1.164);
        vec4 U; vec4 V;
        if (yuvType == 0) { // 0 -> I420
          U = vec4(texture2D(samplerU, v_TexCoord).r - 128./255.);
          V = vec4(texture2D(samplerV, v_TexCoord).r - 128./255.);
        } else if (yuvType == 1) { // 1 -> NV12
          U = vec4(texture2D(samplerUV, v_TexCoord).r - 128./255.);
          V = vec4(texture2D(samplerUV, v_TexCoord).a - 128./255.);
        } else { // 2 -> NV21
          U = vec4(texture2D(samplerUV, v_TexCoord).a - 128./255.);
          V = vec4(texture2D(samplerUV, v_TexCoord).r - 128./255.);
        } 
        color += V * vec4(1.596, -0.813, 0, 0);
        color += U * vec4(0, -0.392, 2.017, 0);
        color.a = 1.0;
        // gl_FragColor：GL中默认定义的输出变量，决定了当前片段的最终颜色
        gl_FragColor = color;
    }
"""