# lib-image Native 构建与生命周期

此模块通过 CMake 构建 `libleo-bitmap.so`，Native 源码位于 `src/main/cpp/`。执行以下命令构建：

```bash
./gradlew :lib-image:assembleRelease
```

`BitmapProcessor` 通过私有 `Long` handle 唯一持有 Native 像素，调用方不能读取或替换该 handle。
应使用 `use {}` 或显式调用 `close()`；重复关闭不会产生副作用，关闭后继续操作会抛出
`IllegalStateException`。

本轮已删除原公开属性 `bitmapByteBuffer` 及其 JVM getter/setter，这是源码和二进制不兼容变更。
调用方应只使用 `BitmapProcessor` 的公开变换函数与 `bitmap` 结果，并通过 `close()/use {}` 管理生命周期。

模块构建 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。Native 链接参数为 64 位 ABI 提供 16 KB
LOAD segment 对齐，可用 `readelf -lW` 检查 `Align` 列。
