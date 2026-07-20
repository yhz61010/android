---
name: 本机构建环境配置
description: 在本机构建/测试需先建 local.properties 并补装 SDK platform-36/build-tools 36
type: project
---

在本机（`/home/coding04/...`）构建或跑单测前的一次性环境配置：

1. 创建 `local.properties`（已在 `.gitignore`，不会提交）：`sdk.dir=/home/coding04/Android/Sdk`
2. 本机 SDK 初始只有 platform 33/34/35、build-tools ≤35、NDK 23.1，但项目需 **compileSdk/targetSdk 36**。
   需补装：`$SDK/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0"`
   （先 `yes | sdkmanager --licenses`）。
3. `:androidbase` 的 JVM 单测**不需要 NDK/CMake**（原生模块仅在实际编译原生代码时才需 NDK 29/CMake 3.22）。
   跑 cipher 单测命令可用：
   `./gradlew :androidbase:testDebugUnitTest --tests "com.leovp.androidbase.AESUtilTest" --tests "com.leovp.androidbase.RSAUtilTest"`
4. 单测在纯 JVM 下 `Build.VERSION.SDK_INT` = 0（`isReturnDefaultValues=true`），加密走 SHA1 新格式路径。

**Why:** 首次构建因缺 SDK 位置和 platform-36 失败，排查耗时；记录避免重复踩坑。
**How to apply:** 新会话若要构建/测试，先确认上述配置到位再跑 Gradle。
