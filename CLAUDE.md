# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LeoAndroidBaseUtil** is a multi-module Android library project (`com.leovp.android`) published via JitPack. It contains 40+ modules providing reusable utilities for Android development: byte operations, JSON, image processing, networking, MVVM architecture, Jetpack Compose components, media codecs (H.264/HEVC, ADPCM), camera, NFC, WebRTC, and more.

## Build & Development

- **Kotlin-only** codebase with Kotlin DSL for Gradle
- **Requires**: JDK 17, Android SDK 36, NDK 25.2.9519653
- **Setup**: Copy `gradle.properties.template` to `gradle.properties` and configure keystore properties

### Common Commands

```bash
./gradlew assembleDebug                    # Build debug APK (demo app)
./gradlew assembleRelease                  # Build release APK
./gradlew testDebugUnitTest                # Run all unit tests
./gradlew :lib-bytes:testDebugUnitTest     # Run tests for a single module
./gradlew staticCheck                      # Run all quality checks (lint, detekt, ktlint, tests)
./gradlew detekt                           # Run detekt code analysis
./gradlew ktlintCheck                      # Run ktlint style check
./gradlew ktlintFormat                     # Auto-fix ktlint issues
./gradlew dependencyUpdates                # Check for dependency updates
./gradlew clean                            # Clean build outputs
```

## Build Architecture

All build configuration is centralized in the root `build.gradle.kts`:
- `configureApplication()` — applied to modules with the `android.application` plugin (demo app)
- `configureLibrary()` — applied to all library modules automatically
- `configureCompileTasks()` — Java/Kotlin compiler settings applied to all projects
- Java 17 source/target compatibility, JUnit Platform for all test tasks
- Detekt config lives at `10-configs/detekt.yml` with zero-issue tolerance
- Ktlint runs in Android mode with checkstyle reporter

Version catalog at `gradle/libs.versions.toml` manages all dependency versions, SDK versions, and defines dependency bundles (e.g., `androidx-full`, `lifecycle-full`, `test`, `android-test`).

## Module Organization

Key module categories:
- **lib-*** — Pure utility libraries (lib-bytes, lib-json, lib-compress, lib-common-kotlin, lib-common-android, lib-image, lib-reflection, lib-network)
- **lib-mvvm** — MVVM architecture components (BaseViewModel, BaseState, BaseAction, UiEventManager)
- **lib-compose** — Jetpack Compose extensions and composables
- **androidbase** — Core Android utilities, depends on many lib-* modules
- **Media modules** — audio, h264-hevc-decoder, adpcm-ima-qt-codec, ffmpeg-javacpp, ffmpeg-sdk, yuv, jpeg, x264
- **Feature modules** — camerax, camera2live, opengl, nfc, webrtc, screencapture, basenetty, floatview
- **pref** — Preferences (MMKV-based)
- **log** — Logging system
- **demo** — Main demo application showcasing all modules

## Architecture Pattern

MVVM + Clean Architecture (Presentation → Domain → Data layers). Key components in `lib-mvvm`:
- `BaseViewModel` with `BaseState` and `BaseAction` interfaces
- `UiEventManager` for UI event handling
- Compose integration via `lib-compose`

DI uses Koin (not Hilt).

## Testing

- **JUnit 5** (Jupiter) as the primary test framework
- **Mockk** for mocking, **Kluent** for assertions, **Robolectric** for Android unit tests
- Tests run in parallel: `maxParallelForks = availableProcessors / 2`
- `unitTests.isReturnDefaultValues = true` and `isIncludeAndroidResources = true` set globally

## Code Quality

Detekt and Ktlint are applied to **all projects** (including root). Both are strict — `ignoreFailures = false` and detekt max issues = 0. Run `./gradlew staticCheck` to execute the full verification suite locally.

## Signing

Release builds use keystore properties from either `gradle.properties` (local) or environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) for CI/CD. V1–V4 signing enabled.

## SDK Constraints

`minSdk` is 21 (Android 5.0) due to external project constraints. This limits some dependency upgrades.
