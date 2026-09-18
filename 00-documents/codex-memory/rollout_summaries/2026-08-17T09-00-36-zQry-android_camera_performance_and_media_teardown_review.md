thread_id: 01a00ef3-880a-7020-99eb-8cbd42908814
updated_at: 2026-09-07T00:51:56+00:00
git_branch: review/native-modules

# Android repository memory wake-up, camera performance review, and media teardown remediation

Rollout context: `当前仓库`; Chinese collaboration, read-only memory loading first. The rollout began with a memory wake-up, then reviewed `camera2live`/`camerax` performance, and later continued with broader audio/screencapture/MediaCodec remediation and verification.

## Task 1: 唤醒仓库记忆

Outcome: success

Preference signals:
- 用户要求“唤醒记忆” -> future sessions should first read the repository guidance and relevant memory, verify current checkout facts, and avoid treating historical branch/build data as current.
- Repository collaboration defaults to Chinese; code comments and Git commit messages remain English, with no AI attribution.
- Generated documentation belongs under `00-documents/`; do not run Gradle verification by default unless explicitly requested.

Key steps:
- Read Codex memory, `CLAUDE.md`, `.claude/memory/MEMORY.md`, and selected project memory files.
- Identified an old `/home/coding04/...` build-environment entry as stale relative to the current checkout.

Reusable knowledge:
- Current repo toolchain documented in `CLAUDE.md`: JDK 17, SDK 36, minSdk 21, Gradle 9.4.0/AGP 9.0.1/Kotlin 2.3.10.
- Existing memory records important Android facts: `AESUtil.decrypt` is strict AES-GCM while `decryptLegacy` handles old CBC; libraries require module-root `consumer-rules.pro`; release scope must exclude local uncommitted changes.

References:
- `CLAUDE.md`
- `.claude/memory/MEMORY.md`
- `本机主 Codex 记忆索引`

## Task 2: 审查 `camera2live` 与 `camerax` 性能

Outcome: partial

Preference signals:
- 用户要求“检查 camera2live，camerax 中的性能问题” -> review should cover capture threads, backpressure, MediaCodec, YUV/Bitmap allocations, resource release, and clearly separate static findings from device-validated behavior.
- Existing uncommitted changes must be preserved and considered separately from baseline behavior.

Key steps:
- Confirmed branch `fix/eight-module-remediation`, HEAD `8afc8a845`, with an existing uncommitted change in `camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt`.
- Inspected camera capture, ImageReader callbacks, YUV conversion, encoder queues, CameraX analyzers, bitmap processing, executors, and lifecycle teardown without initially running Gradle.

Reusable knowledge:
- `camera2live` recording callback currently runs on `cameraHandler`, while a dedicated `imageReaderHandler` exists and is used elsewhere; the recording path performs `DataProcessContext.doProcess()` (YUV conversion/rotation/mirroring) directly on the camera handler. This is a high-value latency/CPU finding: move frame processing to the image-reader worker while keeping camera control callbacks on `cameraHandler`.
- `CameraAvcEncoder` uses a bounded queue of capacity 5 and drops the oldest frame with warning telemetry. Its callback path repeatedly queues a zero-size input buffer when no frame is available, which can create codec/CPU churn and should instead wait for real frames or use explicit input-buffer ownership/backpressure.
- CameraX already uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`; `LuminosityAnalyzer` samples every 10th luma byte and closes `ImageProxy` with `use`, reducing hot-path copying.
- CameraX photo processing is largely off-main-thread, but `BaseCameraXFragment` still performs full bitmap decode/rotate/encode/write work for captured images; this is memory- and CPU-heavy at high resolutions and needs device profiling rather than assumptions.
- CameraX caches camera characteristics and supported sizes per camera ID, avoiding repeated camera-service lookups during rebinding.
- Static review found no Gradle validation in the initial performance-review phase; later remediation work did run targeted verification.

Failures and how to do differently:
- Do not claim complete performance validation from static inspection alone; camera performance requires device traces/FPS/drop/latency measurements across sensor orientations and YUV formats.
- Avoid broad searches over generated `build/` artifacts; restrict `rg`/file enumeration to source directories.

References:
- `camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt:903-980`
- `camera2live/src/main/kotlin/com/leovp/camera2live/codec/CameraAvcEncoder.kt:126-145,219-303`
- `camera2live/src/main/kotlin/com/leovp/camera2live/base/encodestrategies/EncoderStrategyYuv420P.kt`
- `camera2live/src/main/kotlin/com/leovp/camera2live/base/encodestrategies/EncoderStrategyYuv420Sp.kt`
- `camerax/src/main/kotlin/com/leovp/camerax/analyzer/LuminosityAnalyzer.kt`
- `camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt:371-385`
- `camerax/src/main/kotlin/com/leovp/camerax/fragments/base/BaseCameraXFragment.kt:262-447`

## Task 3: MediaCodec/audio/screencapture teardown remediation

Outcome: success

Key steps:
- Reworked AAC/OPUS file playback terminal states, error callbacks, cancellation preservation, EOS timeout handling, queue/backpressure, and deterministic `releaseAndJoin()` behavior.
- Hardened synchronous/asynchronous MediaCodec buffer ownership, including returning dequeued buffers on failure/cancellation and handling EOS flags even when `getOutputBuffer()` returns null.
- Serialized screenshot EGL/MediaCodec ownership on a dedicated thread; added `releaseAndJoin()` and synchronized callback/encoder detachment.
- Updated the screen-recording demo to wait for teardown before closing output, handle codec failure, and clean up on Activity destruction.
- Fixed `ShellUtil` toybox `ps` header parsing and added regression tests.

Validation:
- `:audio:testDebugUnitTest`: 42 tests passed.
- audio, screencapture, lib-common-android, and demo ktlint/detekt/compile checks passed.
- `:demo:assembleDevDebug --rerun-tasks`: successful.
- P3H Android 11 device: unsupported HEVC initialization produced an error callback, reset/disabled the toggle, exited without crash, and left the process alive.
- H.264 success recording, rapid-stop/exit, API 21–25 EGL behavior, and heavy real-device codec fault scenarios remain unverified.

Failures and how to do differently:
- Preserve `CancellationException` separately from ordinary failures; broad catches must not convert cancellation into error logging or lose the original exception.
- For MediaCodec input queues, remove an input-buffer ID only after successful `queueInputBuffer`; never use unconditional zero-byte submission as generic recovery.
- Do not release EGL, codec, or output streams from a different thread while frame callbacks may still be active; use ownership-thread cleanup plus a join barrier.

References:
- `audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt`
- `audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronous.kt`
- `audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronous.kt`
- `audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt`
- `audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt`
- `screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt`
- `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/RecordSingleAppScreenActivity.kt`
- `CHANGELOG.md`
- `00-documents/2026-09-02-audio-media-teardown-followup-fixes_cc.md`

Final repository state from the rollout: branch `review/native-modules` at `d1c87e65e`, with 22 modified files and changes not committed or pushed.
