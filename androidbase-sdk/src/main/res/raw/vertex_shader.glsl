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