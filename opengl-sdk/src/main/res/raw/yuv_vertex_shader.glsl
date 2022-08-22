/**
 * 顶点着色器程序
 * 顶点着色器用于定义绘制的形状。
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

uniform mat4 uMVPMatrix;
attribute vec4 a_Position;// 输入的顶点坐标，会在程序指定将数据输入到该字段
attribute vec2 a_TexCoord;// 输入的纹理坐标(2 个分量，S 和 T 坐标)，会在程序指定将数据输入到该字段
varying vec2 v_TexCoord;// 输出的纹理坐标，输入到片段着色器
void main() {
    v_TexCoord = a_TexCoord;
    gl_Position = a_Position;// gl_Position：GL中默认定义的输出变量，决定了当前顶点的最终位置

    // 这里其实是将上下翻转过来（因为安卓图片会自动上下翻转，所以转回来。也可以在顶点坐标中就上下翻转）
    // v_TexCoord = vec2(a_TexCoord.x, 1.0 - a_TexCoord.y);
    // gl_Position = uMVPMatrix * a_Position;
}