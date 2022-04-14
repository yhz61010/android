## 几何图形相关定义

### 顶点的模式

- GL_POINTS：绘制独立的点。
  
- GL_LINE_STRIP：绘制一系列线段。
- GL_LINE_LOOP：类似 `GL_LINE_STRIP`，但是首尾相连，构成一个封闭曲线。
- GL_LINES：顶点两两连接，为多条线段构成。

- GL_TRIANGLES：每隔三个顶点构成一个三角形。例如：ABC，DEF，GHI
- GL_TRIANGLE_STRIP: 根据顶点序号的奇偶性（序号从0开始），按特定规则组成一系列三角形。例如：ABC、CBD、CDE、EDF
- GL_TRIANGLE_FAN：第一个点和之后所有相邻的 2 个点构成一个三角形。
  
- GL_QUADS
- GL_QUAD_STRIP
- GL_POLYGON

### 模式详解

#### GL_TRIANGLES

#### GL_TRIANGLE_STRIP

根据顶点序号的奇偶性（序号从0开始），按特定规则组成一系列三角形。例如：ABC、CBD、CDE、EDF。

**注意**：如果顶点的总数不是 3 的倍数，那么最后的 1 个或者 2 个顶点会被忽略。

**如果当前顶点是奇数：**

组成三角形的顶点排列顺序：T=[n, n+1, n+2]

**如果当前顶点是偶数：**

组成三角形的顶点排列顺序：T=[n+1, n, n+2]

例如：

若顶点数据如下：A(v0)，B(v1)，C(v2)，D(v3)，E(v4)，F(v5)。则构成的三角形有：

```
ABC - v0, v1, v2    n=0: T=[n  , n+1, n+2]=[v0, v1, v2]
CBD - v2, v1, v3    n=1: T=[n+1, n  , n+2]=[v2, v1, v3]
CDE - v2, v3, v4    n=2: T=[n  , n+1, n+2]=[v2, v3, v4]
EDF - v4, v3, v5    n=3: T=[n+1, n  , n+2]=[v4, v3, v5]
```

如下图：

<img src="http://lib.leovp.com/resources/opengl/Triangle_Strip_Small.png" alt="GL_TRIANGLE_STRIP" style="zoom: 200%;" />

需要注意上述示例中的顶点顺序，这个顺序是为了保证所有的三角形都是按照相同的方向绘制的，使这个三角形串能够正确形成表面的一部分。对于某些操作，维持方向是很重要的，比如剔除。

| <img src="http://lib.leovp.com/resources/opengl/Triangle_Strip_In_OpenGL_Vertice4.svg" alt="Triangle Strip In OpenGL - Vertice4" style="zoom:200%;" /> | <img src="http://lib.leovp.com/resources/opengl/Triangles_Strip_In_OpenGL.svg" alt="Triangles Strip In OpenGL - Vertice5" style="zoom: 200%;" /> |
| ------------------------------------------------------------ | ------------------------------------------------------------ |




#### GL_TRIANGLE_FAN



### Android 渲染方法

OpenGL ES 提供了两类方法来绘制一个空间几何图形：

- public abstract void glDrawArrays(int mode, int first, int count) 使用 VetexBuffer 来绘制，顶点的顺序由 vertexBuffer 中的顺序指定。
- public abstract void glDrawElements(int mode, int count, int type, Buffer indices) ，可以重新定义顶点的顺序，顶点的顺序由 indices Buffer 指定。

其中 mode 为上述解释顶点的模式。

## 参考文献
- http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
- https://blog.csdn.net/xiajun07061225/article/details/7455283
- https://en.wikipedia.org/wiki/Triangle_strip