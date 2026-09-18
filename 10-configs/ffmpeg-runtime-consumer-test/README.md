# FFmpeg Runtime Capability Verification

A standalone consumer project that proves the three FFmpeg wrapper modules cannot be
pulled into the same app.

Chinese version: [README.zh-CN.md](README.zh-CN.md)

## Why this exists

These three published modules each bundle **the same native libraries**:

| Module | Contents |
|--------|----------|
| `adpcm-ima-qt-codec` | ADPCM IMA QT audio |
| `h264-hevc-decoder` | H.264 / HEVC video |
| `adpcm-ima-qt-codec-h264-hevc-decoder` | both of the above in one artifact |

Depending on any two of them puts two copies of identically named `.so` files into one APK.
Which copy wins is left to the packager and the loader, so the failure shows up at runtime,
on a device, as a crash or as silently wrong behaviour.

To turn that into a build failure instead, the root `build.gradle.kts` gives all three a shared
Gradle capability:

```kotlin
// build.gradle.kts, applied to every module in ffmpegNativeRuntimeModules
outgoing.capability("com.leovp.android:ffmpeg-native-runtime:${project.version}")
```

Gradle allows only one provider per capability, so requesting two of these modules fails during
dependency resolution with an explicit conflict message.

**This project verifies that the guard actually works** - against real published artifacts, not
against the local project graph, because capabilities live in the published Gradle Module Metadata
and a composite build would not exercise them.

## What `verify.sh` does

1. Reads `leo-version` from `gradle/libs.versions.toml`.
2. Publishes the three modules with `publishReleasePublicationToMavenLocal` into a **temporary**
   Maven repository created by `mktemp -d`. A `trap` removes it on exit, so `~/.m2` is never
   touched.
3. Asserts each published `.module` metadata file declares `"name": "ffmpeg-native-runtime"`.
4. **Positive cases** - builds this project's `:app` against each module on its own. All three
   must assemble.
5. **Negative cases** - builds `:app` against each pair (audio + video, audio + combined,
   video + combined). All three **must fail**, and the build log must mention the
   `ffmpeg-native-runtime` capability. A pair that assembles successfully makes the script
   `exit 1`: that would mean the guard is gone.

`app/build.gradle.kts` is an otherwise empty APK whose dependencies come from a Gradle property,
which is what lets one project cover all six cases:

```kotlin
implementation("com.leovp.android:$module:$ffmpegVersion")  // module from -PffmpegModules
```

## How to run

```bash
./10-configs/ffmpeg-runtime-consumer-test/verify.sh
```

Run it from anywhere; the script resolves the repository root from its own location. On success
it prints:

```
FFmpeg runtime capability verification passed.
```

Any other outcome is a failure - `set -euo pipefail` stops at the first problem.

### Requirements

- **JDK 17** and a working Android SDK, same as the main build.
- **ripgrep (`rg`)** on `PATH`. The script greps published metadata and build logs with it and
  will fail immediately without it.
- No device or emulator. Nothing here is executed on Android; only assembly and dependency
  resolution are checked.

Expect it to take a while: it publishes three modules and then runs six builds, all with
`--rerun-tasks`.

## Relationship to the main build

This is a **separate Gradle build**. It carries its own `settings.gradle.kts` and is deliberately
absent from the repository's, so:

- `./gradlew staticCheck`, `assembleDebug` and every other day-to-day task ignore it entirely.
- It is never published and is not part of any release artifact.
- It resolves from `mavenLocal()` first, which is how it sees the temporary repository.

## When to re-run it

Re-run after changing any of:

- the capability declaration in the root `build.gradle.kts`, or the
  `ffmpegNativeRuntimeModules` set it applies to;
- the publication setup of the three wrapper modules (coordinates, variants,
  Module Metadata);
- which native libraries those modules bundle, or how they are packaged.

## Reading a failure

| Symptom | Meaning |
|---------|---------|
| A positive case fails to assemble | That module is no longer consumable on its own: a publication or packaging regression. |
| A pair assembles successfully | **The guard is gone.** Two sets of identical `.so` files can now reach one APK. |
| A pair fails, but the log has no capability mention | It broke for some other reason, so the pair proves nothing. Read the log the script names. |
| `rg: command not found` | Install ripgrep; nothing was verified. |

## Files

| Path | Purpose |
|------|---------|
| `verify.sh` | The whole verification. Everything below exists only to serve it. |
| `settings.gradle.kts` | Marks this a standalone build; `mavenLocal()` first. |
| `build.gradle.kts` | Root build file for this project. |
| `app/build.gradle.kts` | Empty APK; dependencies come from `-PffmpegModules` / `-PffmpegVersion`. |
| `app/src/main/AndroidManifest.xml` | Minimal manifest so the APK can be assembled. |

## Background

Added by `e9ac8a093 fix(native): harden media modules and packaging`. The verification run is
recorded in `00-documents/2026-08-27-native-modules-remediation-plan-zh.md` §1.1.
