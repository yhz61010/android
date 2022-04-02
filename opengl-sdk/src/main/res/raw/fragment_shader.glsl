/**
 * 片段着色器程序。
 * "片段" 可以简单理解为像素。片段着色器总是在顶点着色器之后执行。
 * fragment shader 在每个"片段"上都会执行一次，通过插值确定像素的最终显示颜色。
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