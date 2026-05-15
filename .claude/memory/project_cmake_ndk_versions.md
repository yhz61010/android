---
name: cmake-ndk-versions
description: CMake 3.22.1, NDK 29.0.14206865 — versions managed via libs.versions.toml
type: feedback
---

CMake 版本使用 3.22.1，NDK 版本使用 29.0.14206865。

**Why:** CMake 从 4.1.2 降为 3.22.1 是因为 JitPack 不支持 4.1.2。版本统一通过 `gradle/libs.versions.toml` 管理。

**How to apply:** 在 build.gradle.kts 中使用 `libs.versions.ndk.sdk.get()` 和 `libs.versions.cmake.get()` 引用版本，不要硬编码。CMakeLists.txt 中 `cmake_minimum_required(VERSION 3.22.1)` 可直接写版本号。
