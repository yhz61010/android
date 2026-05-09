# Native Handle Pattern (`nativeHandle`)

## Overview

The `nativeHandle` field is a `Long` (JNI `jlong`) stored on each Kotlin/Java object that uses native (C++) resources. It holds the memory address of the corresponding C++ object, allowing the native side to retrieve the correct instance on every JNI call.

This replaces the previous global-variable approach, which only supported a single instance per process and was not thread-safe.

## Why It's Needed

JNI native methods receive `JNIEnv*` and `jobject` (the Java `this`) on every call, but have no built-in way to associate a Java object with a specific C++ object. The `nativeHandle` pattern solves this by:

1. Storing the C++ object pointer as a `jlong` field on the Java object during `init()`
2. Reading it back on every subsequent native call (`encode()`, `decode()`, `release()`)
3. Clearing it to `0` on `release()` to prevent use-after-free

## Kotlin Side

Add a `nativeHandle` field to the class. It is accessed exclusively by native code via JNI reflection:

```kotlin
@Keep
class AdpcmImaQtEncoder private constructor() {
    // Stores the native C++ object pointer. Accessed by JNI only.
    @Suppress("unused")
    private var nativeHandle: Long = 0L

    constructor(sampleRate: Int, channels: Int, bitRate: Int) : this() {
        init(sampleRate, channels, bitRate)
    }

    private external fun init(sampleRate: Int, channels: Int, bitRate: Int): Int
    external fun encode(pcmBytes: ByteArray)
    external fun release()
}
```

Key points:
- `@Suppress("unused")` — the field is only accessed from native code, not Kotlin
- `@Keep` on the class — prevents ProGuard/R8 from removing the field
- Default value `0L` — indicates no native object is attached

## Native (C++) Side

### Helper Functions

```cpp
// Get the JNI field ID for "nativeHandle"
static jfieldID getHandleField(JNIEnv *env, jobject obj) {
    jclass clazz = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);  // Prevent JNI local reference leak
    return fid;
}

// Read the C++ object pointer from the Java object
static AdpcmImaQtEncoder *getEncoder(JNIEnv *env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<AdpcmImaQtEncoder *>(handle);
}
```

### init() — Create C++ Object, Store Pointer

```cpp
JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj,
                            jint sampleRate, jint channels, jint bitRate) {
    // Prevent double init
    if (getEncoder(env, obj) != nullptr) return -1;

    auto *pEncoder = new(std::nothrow) AdpcmImaQtEncoder(sampleRate, channels, bitRate);
    if (pEncoder == nullptr || !pEncoder->isValid()) {
        delete pEncoder;
        return -2;
    }

    // Store the pointer into the Java field
    env->SetLongField(obj, getHandleField(env, obj),
                      reinterpret_cast<jlong>(pEncoder));
    return 0;
}
```

### encode() — Retrieve C++ Object, Use It

```cpp
JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    // Retrieve the C++ object for THIS Java instance
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder == nullptr) return;

    // Use pEncoder->encode(...)
    ...
}
```

### release() — Delete C++ Object, Clear Handle

```cpp
JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder != nullptr) {
        delete pEncoder;
        // Set to 0 to prevent use-after-free
        env->SetLongField(obj, getHandleField(env, obj), 0L);
    }
}
```

## Lifecycle Diagram

```
Kotlin: val encoder = AdpcmImaQtEncoder(44100, 2, 64000)
        |
        v
Native: init() -> new AdpcmImaQtEncoder(...) -> SetLongField(nativeHandle, pointer)
        |
        |  nativeHandle = 0x7F12345678 (C++ object address)
        |
Kotlin: encoder.encode(pcmBytes)
        |
        v
Native: encode() -> GetLongField(nativeHandle) -> reinterpret_cast -> pEncoder->encode(...)
        |
Kotlin: encoder.release()
        |
        v
Native: release() -> GetLongField(nativeHandle) -> delete pEncoder -> SetLongField(nativeHandle, 0)
        |
        |  nativeHandle = 0 (no native object)
```

## Multi-Instance Support

Each Java object has its own `nativeHandle`, so multiple instances work independently:

```kotlin
val encoder1 = AdpcmImaQtEncoder(44100, 2, 64000)  // nativeHandle -> C++ object A
val encoder2 = AdpcmImaQtEncoder(16000, 1, 32000)  // nativeHandle -> C++ object B

encoder1.encode(pcm1)  // native reads encoder1.nativeHandle -> uses object A
encoder2.encode(pcm2)  // native reads encoder2.nativeHandle -> uses object B

encoder1.release()  // deletes object A, encoder1.nativeHandle = 0
encoder2.release()  // deletes object B, encoder2.nativeHandle = 0
```

## Modules Using This Pattern

| Module | Kotlin Class | C++ Class | Native Lib |
|--------|-------------|-----------|------------|
| adpcm-ima-qt-codec | `AdpcmImaQtEncoder` | `AdpcmImaQtEncoder` | `libadpcm-ima-qt-encoder.so` |
| adpcm-ima-qt-codec | `AdpcmImaQtDecoder` | `AdpcmImaQtDecoder` | `libadpcm-ima-qt-decoder.so` |
| h264-hevc-decoder | `H264HevcDecoder` | `H264HevcDecoderContext` | `libh264-hevc-decoder.so` |
| adpcm-ima-qt-codec-h264-hevc-decoder | (same classes as above, bundled together) | | |

---

# Native Handle 模式 (`nativeHandle`)

## 概述

`nativeHandle` 是一个 `Long`（JNI `jlong`）字段，存储在每个使用 native (C++) 资源的 Kotlin/Java 对象上。它保存了对应 C++ 对象的内存地址，使得 native 侧在每次 JNI 调用时能够取回正确的实例。

该模式替代了之前的全局变量方案。全局变量方案在整个进程中只支持单个实例，且不具备线程安全性。

## 为什么需要

JNI native 方法在每次调用时会接收到 `JNIEnv*` 和 `jobject`（Java 侧的 `this`），但没有内置机制将 Java 对象与特定的 C++ 对象关联起来。`nativeHandle` 模式通过以下方式解决这个问题：

1. 在 `init()` 时将 C++ 对象指针作为 `jlong` 存储到 Java 对象的字段中
2. 在后续每次 native 调用（`encode()`、`decode()`、`release()`）时读取该指针
3. 在 `release()` 时将其置为 `0`，防止 use-after-free

## Kotlin 侧

在类中添加 `nativeHandle` 字段。该字段仅由 native 代码通过 JNI 反射访问：

```kotlin
@Keep
class AdpcmImaQtEncoder private constructor() {
    // 存储 native C++ 对象指针，仅由 JNI 访问
    @Suppress("unused")
    private var nativeHandle: Long = 0L

    constructor(sampleRate: Int, channels: Int, bitRate: Int) : this() {
        init(sampleRate, channels, bitRate)
    }

    private external fun init(sampleRate: Int, channels: Int, bitRate: Int): Int
    external fun encode(pcmBytes: ByteArray)
    external fun release()
}
```

要点：
- `@Suppress("unused")` — 该字段仅被 native 代码访问，Kotlin 侧不直接使用
- 类上的 `@Keep` — 防止 ProGuard/R8 移除该字段
- 默认值 `0L` — 表示没有关联的 native 对象

## Native (C++) 侧

### 辅助函数

```cpp
// 获取 "nativeHandle" 字段的 JNI field ID
static jfieldID getHandleField(JNIEnv *env, jobject obj) {
    jclass clazz = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);  // 防止 JNI 本地引用泄漏
    return fid;
}

// 从 Java 对象中读取 C++ 对象指针
static AdpcmImaQtEncoder *getEncoder(JNIEnv *env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<AdpcmImaQtEncoder *>(handle);
}
```

### init() — 创建 C++ 对象，存储指针

```cpp
JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj,
                            jint sampleRate, jint channels, jint bitRate) {
    // 防止重复初始化
    if (getEncoder(env, obj) != nullptr) return -1;

    auto *pEncoder = new(std::nothrow) AdpcmImaQtEncoder(sampleRate, channels, bitRate);
    if (pEncoder == nullptr || !pEncoder->isValid()) {
        delete pEncoder;
        return -2;
    }

    // 将指针存入 Java 字段
    env->SetLongField(obj, getHandleField(env, obj),
                      reinterpret_cast<jlong>(pEncoder));
    return 0;
}
```

### encode() — 取回 C++ 对象，使用它

```cpp
JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    // 取回当前 Java 实例对应的 C++ 对象
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder == nullptr) return;

    // 使用 pEncoder->encode(...)
    ...
}
```

### release() — 删除 C++ 对象，清除 Handle

```cpp
JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder != nullptr) {
        delete pEncoder;
        // 置为 0 防止 use-after-free
        env->SetLongField(obj, getHandleField(env, obj), 0L);
    }
}
```

## 生命周期图

```
Kotlin: val encoder = AdpcmImaQtEncoder(44100, 2, 64000)
        |
        v
Native: init() -> new AdpcmImaQtEncoder(...) -> SetLongField(nativeHandle, 指针)
        |
        |  nativeHandle = 0x7F12345678 (C++ 对象地址)
        |
Kotlin: encoder.encode(pcmBytes)
        |
        v
Native: encode() -> GetLongField(nativeHandle) -> reinterpret_cast -> pEncoder->encode(...)
        |
Kotlin: encoder.release()
        |
        v
Native: release() -> GetLongField(nativeHandle) -> delete pEncoder -> SetLongField(nativeHandle, 0)
        |
        |  nativeHandle = 0 (无 native 对象)
```

## 多实例支持

每个 Java 对象都有自己的 `nativeHandle`，因此多个实例可以独立工作：

```kotlin
val encoder1 = AdpcmImaQtEncoder(44100, 2, 64000)  // nativeHandle -> C++ 对象 A
val encoder2 = AdpcmImaQtEncoder(16000, 1, 32000)  // nativeHandle -> C++ 对象 B

encoder1.encode(pcm1)  // native 读取 encoder1.nativeHandle -> 使用对象 A
encoder2.encode(pcm2)  // native 读取 encoder2.nativeHandle -> 使用对象 B

encoder1.release()  // 删除对象 A，encoder1.nativeHandle = 0
encoder2.release()  // 删除对象 B，encoder2.nativeHandle = 0
```

## 使用该模式的模块

| 模块 | Kotlin 类 | C++ 类 | Native 库 |
|------|----------|--------|----------|
| adpcm-ima-qt-codec | `AdpcmImaQtEncoder` | `AdpcmImaQtEncoder` | `libadpcm-ima-qt-encoder.so` |
| adpcm-ima-qt-codec | `AdpcmImaQtDecoder` | `AdpcmImaQtDecoder` | `libadpcm-ima-qt-decoder.so` |
| h264-hevc-decoder | `H264HevcDecoder` | `H264HevcDecoderContext` | `libh264-hevc-decoder.so` |
| adpcm-ima-qt-codec-h264-hevc-decoder | （与上述类相同，打包在一起） | | |
