# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-module Android project built with Gradle Kotlin DSL. Shared build logic and version constraints live in [build.gradle.kts](/home/yhz61010/StudioProjects/android/build.gradle.kts), [settings.gradle.kts](/home/yhz61010/StudioProjects/android/settings.gradle.kts), and [gradle/libs.versions.toml](/home/yhz61010/StudioProjects/android/gradle/libs.versions.toml).

Included Gradle modules are defined in `settings.gradle.kts`. The current module layout is easiest to understand by category:

- Demo apps: `demo`, `demo-dex`
- Core and shared libraries: `androidbase`, `log`, `pref`, `http`, `lib-common-android`, `lib-common-kotlin`, `lib-bytes`, `lib-json`, `lib-compress`, `lib-network`, `lib-reflection`, `lib-image`, `lib-exif`, `lib-mvvm`, `lib-compose`
- Media and codec modules: `audio`, `ffmpeg-javacpp`, `adpcm-ima-qt-codec`, `h264-hevc-decoder`, `adpcm-ima-qt-codec-h264-hevc-decoder`, `yuv`, `jpeg`
- Device, graphics, and feature modules: `camerax`, `camera2live`, `screencapture`, `draw-on-screen`, `floatview`, `opengl`, `nfc`, `basenetty`, `aidl-client`, `dex`, `circle-progressbar`

Some root directories contain native sources, build helpers, or archived experiments but are not active Gradle modules unless they are included in `settings.gradle.kts`. Examples include `ffmpeg-sdk`, `webrtc`, `x264`, `libjpeg-turbo`, and `libyuv`.

Android resources usually live under `src/main/res`. Native build entry points may live either under `src/main/cpp` or in a module-root `CMakeLists.txt`, depending on the module.

## Build, Test, and Development Commands
Use JDK 17 and the checked-in wrapper. The current version catalog targets `compileSdk`/`targetSdk` 36, `minSdk` 21, NDK `29.0.14206865`, and CMake `3.22.1`.

- `./gradlew assemble`: build all configured modules.
- `./gradlew :demo:assembleDevDebug`: build the demo app’s main debug variant.
- `./gradlew testDebugUnitTest`: run debug unit tests across Android modules.
- `./gradlew :androidbase:testDebugUnitTest`: run one library module’s unit tests. Substitute another module name as needed.
- `./gradlew :demo:testDevDebugUnitTest`: run unit tests for the demo app’s `devDebug` variant.
- `./gradlew :demo:connectedDevDebugAndroidTest`: run instrumentation tests on a connected device/emulator.
- `./gradlew ktlintCheck detekt`: run formatting and static analysis.
- `./gradlew clean`: remove Gradle build outputs.

Before building, copy `gradle.properties.template` to `gradle.properties`. Install Git LFS as noted in `README.md` and `00-documents/git-lfs-guide.md`.

## Coding Style & Naming Conventions
Follow Kotlin-first conventions with 4-space indentation and Gradle Kotlin DSL for build files. Keep package names under `com.leovp.*`. Match existing naming: classes and objects in `UpperCamelCase`, functions and properties in `lowerCamelCase`, constants in `UPPER_SNAKE_CASE`. Test classes typically end with `Test` or `UnitTest`. Run `ktlintCheck` and `detekt` before submitting changes; root config uses `10-configs/detekt.yml`. Detekt and ktlint are applied from the root project across all modules.

## Testing Guidelines
JUnit 5 is enabled for all Gradle `Test` tasks through `useJUnitPlatform()`. Android unit tests are configured with `isReturnDefaultValues = true` and `isIncludeAndroidResources = true`. The `demo` app uses `AndroidJUnitRunner` with `de.mannodermaus.junit5.AndroidJUnit5Builder` for instrumentation tests. Place JVM tests in `src/test/kotlin` or `src/test/java`; place device tests in `src/androidTest`. Prefer focused tests near the affected module, for example `androidbase/src/test/.../RSAUtilTest.kt`.

## Commit & Pull Request Guidelines
Recent history mixes plain imperative subjects with Conventional Commit-style prefixes such as `docs(readme): ...`. Prefer short imperative commit messages and use a scoped prefix when it adds clarity, for example `fix(lib-network): handle empty response`. Keep pull requests narrow, describe impacted modules, list verification commands, and attach screenshots for UI or demo-app changes. Call out changes to signing, native libraries, or Gradle configuration explicitly.

## Agent-Specific Instructions
Use Chinese for contributor conversations in this repository. Write documentation, Markdown content, and code comments in English.

## Claude And CodeX Interop
Keep all existing Claude Code files, including [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) and everything under `.claude/`, unchanged unless the user explicitly asks otherwise.

For CodeX in this repository:

- Treat [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) as supplemental project guidance in addition to this file.
- Reuse the existing Claude materials by reading them on demand instead of duplicating them blindly.
- When the task is about personal or repository working style, read `.claude/rules/personal-style.md` and `.claude/memory/MEMORY.md`.
- When the task is about Android UI or UX design, read `.claude/skills/mobile-android-design/SKILL.md`.
- When the user asks about finding, creating, or installing skills, read `.claude/skills/find-skills/SKILL.md`.
- Preserve `.claude/commands`, `.claude/memory`, `.claude/rules`, `.claude/skills`, and Claude-generated files unless the user explicitly requests changes to them.

The following rules are important enough to be enforced directly by CodeX without depending on extra files:

- Use Chinese for contributor conversations.
- Use English for code comments, documentation, Markdown content, and git commit messages.
- When creating documentation files, generate both English and Chinese versions; keep English as the primary Markdown/code-comment language unless the file is the Chinese companion document.
- Save documentation under `00-documents` instead of `docs`.
- Do not add `Co-Authored-By` trailers to commit messages.

If Claude-specific and CodeX-specific instructions overlap, follow the stricter rule. If they conflict, follow direct system, developer, and [AGENTS.md](/home/yhz61010/StudioProjects/android/AGENTS.md) instructions first, then use [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) and `.claude/**` as supplemental guidance.
