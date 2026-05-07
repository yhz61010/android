# Android Native Build Output Directories

## English

When building native libraries (.so) with CMake via Android Gradle Plugin, multiple intermediate directories are generated. Here's what each one does, in pipeline order:

| Directory | Purpose |
|-----------|---------|
| `cxx/RelWithDebInfo/.../obj/` | Raw CMake compilation output with full debug symbols — the original .so |
| `merged_jni_libs/.../out/` | Merges all JNI library sources (CMake output + third-party .so + jniLibs directories) |
| `merged_native_libs/.../out/` | Further merges on top of merged_jni_libs (includes native libs from dependency modules) |
| `stripped_native_libs/.../out/` | Strips debug symbols from merged .so files to reduce size for final packaging |
| `library_and_local_jars_jni/.../jni/` | Final JNI libraries packaged into the AAR for downstream consumers |

### Build Pipeline Flow

```
CMake compile (cxx/)
  → Merge (merged_jni_libs → merged_native_libs)
    → Strip symbols (stripped_native_libs)
      → Package into AAR (library_and_local_jars_jni)
```

### Which .so to use?

- **With debug symbols** (for ndk-stack crash analysis): `cxx/RelWithDebInfo/.../obj/`
- **For release** (stripped): `stripped_native_libs/release/.../out/`

---

## 中文

使用 Android Gradle Plugin 通过 CMake 构建 native library (.so) 时，会生成多个中间产物目录。以下按构建流水线顺序说明各目录的作用：

| 目录 | 作用 |
|------|------|
| `cxx/RelWithDebInfo/.../obj/` | CMake 的**原始编译输出**，包含完整调试符号，是最原始的 .so |
| `merged_jni_libs/.../out/` | 合并所有 JNI 库来源（CMake 产出 + 第三方 .so + jniLibs 目录）到一起 |
| `merged_native_libs/.../out/` | 在 merged_jni_libs 基础上进一步合并（含依赖模块的 native libs） |
| `stripped_native_libs/.../out/` | 对合并后的 .so **去除调试符号**（strip），减小体积，用于最终打包 |
| `library_and_local_jars_jni/.../jni/` | 最终打入 **AAR** 的 JNI 库，供下游模块/消费者使用 |

### 构建流水线

```
CMake 编译 (cxx/)
  → 合并 (merged_jni_libs → merged_native_libs)
    → Strip 符号 (stripped_native_libs)
      → 打包进 AAR (library_and_local_jars_jni)
```

### 如何选择 .so 文件？

- **带调试符号**（用于 ndk-stack 分析 crash）：`cxx/RelWithDebInfo/.../obj/`
- **最终发布用**（已 strip）：`stripped_native_libs/release/.../out/`
