# camera2live / camerax Performance Remediation — Decisions and Progress (2026-08-13)

> This document records the runtime performance review of `camera2live` and `camerax`, including the shared
> `androidbase/.../media/YuvUtil.kt`, the selected remediation, the rationale for each decision, and delivery progress.
> Related branch: `fix/eight-module-remediation`.
> Related documents: `2026-08-11-remediation-progress-and-review-zh.md` (eight-module remediation) and
> `superpowers/specs/` (CX-5 / LB-3 designs).

---

## 1. Review Method

Two read-only Kotlin review agents reviewed the modules in parallel and reported runtime performance problems only:
per-frame allocations, main-thread stalls, duplicate calculations, unbounded buffers, and missing reuse. The four HIGH
findings were then checked manually line by line. The review produced **11 findings: 4 HIGH and 7 MEDIUM**.

> The two hottest findings, H4 and M5, are in the shared `androidbase/.../media/YuvUtil.kt`. They are outside the two
> camera module directories but are called by those modules on every frame. The maintainer therefore included that
> shared utility in the review scope.

---

## 2. Complete Findings

| ID | Location | Problem | Frequency |
|----|----------|---------|-----------|
| H1 | `camerax/utils/CodecExt.kt:35-53` and `BaseCameraXFragment.outputCameraParameters` | `createEncoderByType()` created native codecs only to read capabilities and never released them; every bind also created four MediaCodec instances on the main thread for diagnostics | Every bind |
| H2 | `camerax/analyzer/LuminosityAnalyzer.kt:72` | `planes[0].buffer.toByteArray()` allocated a full Y-plane array for every frame (about 9 MB/s) | Every frame |
| H3 | `camera2live/.../EncoderStrategyYuv420Sp.kt:48-57` | The front camera used per-pixel Kotlin mirror and rotation while the back camera used native libyuv, costing several times more CPU for front-camera frames | Every front-camera frame |
| H4 | `androidbase/.../media/YuvUtil.kt:155-172` | Strided chroma planes were extracted byte by byte in Kotlin | Every frame |
| M1 | `LuminosityAnalyzer.kt:22,56` | `ArrayDeque<Long>` boxed a `Long` for every frame | Every frame |
| M2 | `LuminosityAnalyzer.kt:80-85` | Full-resolution luma was summed byte by byte without sampling | Every frame |
| M3 | `CameraFragment.kt:384` | `log.v("Average luminosity: $luma")` built a string unconditionally | Every frame |
| M4 | `BaseCameraXFragment.kt:806-843` / `CameraFragment.kt:280-303` | One bind queried `CameraCharacteristics` repeatedly and computed supported sizes twice | Every bind |
| M5 | `androidbase/.../media/YuvUtil.kt:118` | A new `rowData` scratch buffer was allocated for every frame although row stride is stable during a session | Every frame |
| M6 | `camera2live/codec/CameraAvcEncoder.kt:157-158` | `ByteArray(info.size)` was allocated for every encoded frame | Every encoded frame |
| M7 | `camera2live/Camera2ComponentHelper.kt:926-927` | The previous implementation queried `SENSOR_ORIENTATION` for every frame | Every frame |

---

## 3. Work Breakdown

The findings were divided by location and risk and handled from lower to higher risk:

1. **Subproject 1 (camerax infrastructure)**: H1 and M4. M3 was later moved to subproject 2. These changes preserve
   behavior and do not depend on a device.
2. **Subproject 2 (LuminosityAnalyzer)**: H2, M1, M2, and M3, including the decision on whether the analyzer should remain
   enabled.
3. **Subproject 3 (camera2live and shared YuvUtil)**: H3, H4, M5, M6, and M7. This was the highest-risk group because it
   affects per-frame YUV correctness. It was split further:
   - **3a (low-risk mechanical change)**: M7.
   - **3b (YUV correctness; device verification required)**: H3 and the related front-camera orientation/color fix.
     H4 and M5 were not implemented; see Sections 4 and 6.
   - **M6** was evaluated separately and skipped.

---

## 4. Decisions and Rationale

### Subproject 1 — Complete (`78e4553a6`, with ktlint cleanup in `0deaafbf0`)

- **H1a (release codecs in CodecExt)**: Each of the four capability-query functions now releases its codec in
  `try/finally`. The capability arrays are Java-side copies and remain valid after release.
- **H1b (cache codec capabilities, not the complete diagnostic string)**:
  - Caching the complete string per camera ID would be cheaper but would freeze the dynamic `deviceRotation` field at
    its first-bind value.
  - The selected design caches device-global, runtime-stable codec capabilities by MIME type and rebuilds the cheap
    diagnostic string for each bind. This preserves current dynamic fields while eliminating the four codec creations.
- **M4 (memoize characteristics and supported sizes by camera ID)**: Both are immutable for a selected camera. Shared
  results are used by `showAvailableRatio`, `getMaxPreviewSize`, `outputCameraParameters`, and
  `CameraFragment.bindCameraUseCases`, removing duplicate binder queries and size calculations within one bind.

### Subproject 2 — Complete (initial commit `ab4d0cdbc`)

- **Direction: option C, disabled by default and optimized internally**:
  - `LuminosityAnalyzer` is `internal`; its only repository consumer was a verbose log in `CameraFragment`.
  - Keeping it active and merely optimizing it would preserve unnecessary per-frame work. Disabling it alone would
    leave H2/M1/M2 in dormant code.
  - Option C removes the analysis stream from the default use-case combination while keeping an efficient opt-in path.
- **M3 (per-frame logging)** was moved from subproject 1 because it is tightly coupled to whether the analyzer runs.
  With option C, the default logging cost disappears naturally.
- **Disable behavior**: `ENABLE_LUMINOSITY_ANALYSIS` defaults to `false`. In that state, `imageAnalyzer` is `null` and
  `ImageAnalysis` is not included in the lifecycle binding list. Enabling the switch creates, configures, and binds it.
- **H2 and M2**: `averageLuma` now reads the `ByteBuffer` directly and samples using `LUMA_SAMPLE_STRIDE`. Its public
  behavior remains exact with the default stride of 1, and non-positive stride values are rejected and tested.
- **M1**: FPS tracking uses a primitive `LongArray` ring buffer instead of `ArrayDeque<Long>`.

### Subproject 3a — Complete, later superseded by 3b (`b1ff02064`)

- **M7 (sensor-orientation cache)**: 3a cached the value in `initializeParameters()`. Subproject 3b instead locks
  `relativeOrientation` when recording starts, so the built-in YUV420P/SP strategies no longer read that cache and the
  field was removed. The `IDataProcessStrategy` parameter remains temporarily for public API compatibility; the helper
  passes an unused `-1` sentinel. It is planned for removal in the next breaking release and is not used as the source
  of dynamic logical-camera orientation on API 32+ foldables.

### Individual Decisions

- **M6 (pool encoded-frame arrays) — skipped**: `encodedBytes` is passed to the public
  `dataUpdateCallback.onCallback(...)`. Reusing an array is safe only if every consumer reads or copies it synchronously;
  asynchronous retention would corrupt the data on the next frame. Changing that ownership contract is breaking, and
  encoded frames are only a few KB, so the benefit is not worth the risk.
- **M5 (`rowData` reuse) — skipped after investigation**: `androidbase/YuvUtil` is a stateless `object`; local per-frame
  allocation is what keeps it thread-safe. Reuse would require a `ThreadLocal` or caller-provided scratch storage. The
  roughly 1–2 KB scratch array is small compared with the unavoidable roughly 460 KB frame array, so mutable shared
  state is not justified.
- **H4 (strided chroma extraction) — skipped after investigation**: Native code has no entry point that consumes
  `Image` planes. A proper native implementation would require a new JNI API and is a separate project; a smaller Kotlin
  optimization would still alter bytes while offering limited benefit.

---

## 5. Progress and Commits

| Subproject | Scope | Status | Commits |
|------------|-------|--------|---------|
| 1 | camerax H1 / M4 | Pushed | `78e4553a6`, `0deaafbf0` (ktlint) |
| 2 | LuminosityAnalyzer H2/M1/M2/M3, option C | Pushed | `ab4d0cdbc` (initial), `5a23d6c93` (final disable/unbind behavior) |
| 3a | camera2live M7 | Pushed | `b1ff02064`, `5a23d6c93` (comments/formatting) |
| 3b | **H3 plus front-camera orientation/color correctness**; H4/M5 skipped | Pushed; current device combinations verified, front camera with `SENSOR_ORIENTATION` 90 still pending release regression | `117e26213`, `8afc8a845` (cancellation handling); see `superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md` |
| M6 | Encoded-frame pooling | Skipped | — |

---

## 6. Subproject 3b — Front-Camera Orientation and Color Correctness

After source investigation and device tests, 3b converged on H3 plus correctness fixes in the same path. H4 and M5 were
not implemented. See `superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md` for the design.

- **H3 and correctness fix**: Device testing proved that the old front-camera path encoded landscape captures as
  portrait and produced wrong color on YUV420SP devices. The orientation problem came from a fixed 270-degree transform
  and old encoder dimensions. The color problem came from passing I420 data to NV21 chroma functions. The new path uses
  native I420 rotation, mirrors the rotated I420 frame horizontally using its rotated dimensions, outputs NV12 explicitly
  for YUV420SP, and keeps I420 for YUV420P. `OrientationLiveData` already accounts for sensor direction, so the transform
  uses the relative angle locked when recording starts instead of separate fixed branches by sensor orientation.
- **H4 was skipped** because extracting planes from `Image` still requires Kotlin without a new planes-based JNI API.
- **M5 was skipped** because local allocation preserves the stateless utility's thread safety and the scratch buffer is
  small relative to the frame allocation.

### Device Verification and Remaining Coverage

`SENSOR_ORIENTATION` belongs to a specific camera rather than the entire device. Front cameras commonly use 270 degrees,
back cameras commonly use 90 degrees, and logical cameras on API 32+ foldables may change orientation with device state.
On 2026-08-17, the maintainer verified photos, preview, and recording on an available device whose front camera reports
`SENSOR_ORIENTATION=270`: photo orientation and mirroring were correct; back-camera landscape recording retained correct
orientation and color; front-camera landscape output was landscape, horizontally mirrored, correctly colored, and free
from corruption. The device gate for that combination was therefore cleared.

The front-camera `SENSOR_ORIENTATION=90` combination, including devices such as Nexus 6/6P, remains unverified because no
such device was available. The new code should cover it through `relativeOrientation`, whose extracted formula has JVM
tests for all 2×2×4 combinations of sensor 90/270, front/back, and four device directions. Formula tests do not replace
device YUV validation, so this remains a release regression item, as does broader YUV420P/YUV420SP coverage.

Recommended regression records include device model, `cameraId`, `lensFacing`, `SENSOR_ORIENTATION`, `deviceState`, and
YUV420P/SP, with orientation, mirroring, and color results for every combination.

The current `OrientationLiveData` rereads `CameraCharacteristics.SENSOR_ORIENTATION` for physical orientation events but
does not listen independently for a fold-state-only change. Dynamic logical cameras on API 32+ foldables are therefore
outside this release's verified support. Future support should use an application-level fold-state source such as
AndroidX Window and rebuild or refresh the orientation source when state changes; it must not restore a permanent sensor
orientation cache.

### Remaining Validation Notes

- Subprojects 1 and 3a preserve behavior. Subproject 2 intentionally changes the default by disabling luminosity logs and
  removing `ImageAnalysis` from the default use-case set. This can affect use-case and resolution negotiation. The
  maintainer completed a CameraX device smoke test covering preview, photo capture, recording, and camera switching.
- The original working environment did not compile locally; detekt, ktlint, and tests were run by the maintainer.

### 2026-08-17 CameraX Device Verification

#### Passed

- Photo capture worked for front and back cameras, preview, and both landscape directions.
- Basic recording worked for both cameras without preview stretching or distortion.

#### Reported Problem

`CameraXDemoActivity` is fixed to `userPortrait`. A back-camera recording made with the device physically at 90 or 270
degrees initially played as portrait in the file preview. Its aspect ratio was correct but did not match landscape
capture. The intended behavior was to keep the capture UI portrait, save the file with correct landscape orientation,
and center landscape video vertically with black bars in the portrait preview page.

#### First Attempt, Later Reverted

The first implementation used `OrientationEventListener`, converted physical orientation through
`UseCase.snapToSurfaceRotation()`, and updated `VideoCapture.targetRotation`, `Preview.targetRotation`, and preview aspect
ratio together. This incorrectly tied output orientation to UI layout, rotating the capture view to landscape. It also
did not update the file-preview `VideoView`, so landscape video remained top-aligned.

#### Final Verified Implementation

- **Decouple recording orientation from the camera view**: `VideoFragment` keeps its orientation listener, but physical
  orientation updates only `VideoCapture.targetRotation`. `Preview` continues to use `viewFinder.display.rotation`, and
  the view aspect ratio follows `resources.configuration.orientation`, keeping the capture UI portrait.
- **Lifecycle**: The listener is enabled in `onStart()` and disabled in `onStop()`. Dynamic target rotation in CameraX
  1.4.1 affects recordings started afterward, not the active recording.
- **Center file previews**: `PhotoFragment` now places `VideoView` with `Gravity.CENTER` inside a black full-screen
  `FrameLayout`, preserving aspect ratio and displaying black bars above and below landscape content.
- **Cleanup**: `PhotoFragment.onDestroyView()` stops playback and releases references to `VideoView` and
  `MediaController`.

Regression covered both back-camera landscape directions, front-camera landscape, portrait recording, rotation during a
recording, and rapid navigation across image/video preview pages.

#### Front-Camera Landscape Thumbnail Fix

The saved front-camera photo was correct, but the gallery-button thumbnail could show the wrong orientation. The file
callback fired before background bitmap rotation, mirroring, and JPEG rewrite completed, allowing Coil to read and cache
the original file. The success callback was moved into the same IO block after all transformations and writes complete;
decode or write failure now reports an error instead of reporting an unfinished file as successful. Regression covers
front and back cameras in portrait, 90 degrees, and 270 degrees, including rapid consecutive captures.

### 2026-08-17 Camera2Live Device-Test Closure

Camera2Live photo and raw H.264 paths required multiple test-and-fix cycles:

1. **Back-camera landscape photos still appeared portrait** because the request used `Display.rotation` while the host UI
   stayed portrait. The shutter path now locks `relativeOrientation.value` and uses the same value in the request and
   `CombinedCaptureResult`, falling back to the old display calculation only when no physical orientation is available.
2. **Photo left/right relationships remained wrong after the first fix**, especially for the front camera, because the
   computed mirror metadata was not applied to saved bytes. Ordinary JPEG is now decoded on IO, transformed through
   `decodeExifOrientation()`, and rewritten so pixels are normalized. `DEPTH_JPEG` remains untouched. Unknown orientation,
   the 316–359 degree range, and orientation-source rebuilding after camera switching were also fixed.
3. **Back-camera raw H.264 landscape recording still appeared portrait**. Raw H.264 has no container rotation matrix;
   `ffprobe` showed a portrait `1080×1920` SPS. Recording now locks a relative rotation, swaps dimensions only for 90/270
   degrees, rotates each I420 frame by the same angle, and initializes the encoder from camera input dimensions.
4. **Black bars are not part of raw-stream output**. Letterboxing belongs to player layout or explicitly padded pixels.
   This change guarantees correct SPS dimensions, pixel orientation, and aspect ratio only.
5. **After the back-camera fix, the front path was still portrait and incorrectly colored** because it retained a fixed
   270-degree transform and interpreted planar I420 chroma with NV21 functions. Both cameras now use the locked relative
   angle; front output is mirrored horizontally after valid I420 rotation, with I420 output for YUV420P and explicit NV12
   conversion for YUV420SP.

Final testing passed portrait and both landscape photo directions and mirroring for both cameras. Back-camera landscape
raw H.264 orientation, dimensions, and color were correct; front-camera landscape orientation, horizontal mirroring,
color, and geometry were correct. Camera switching, preview, photo capture, and recording also passed. Broader camera ID,
sensor orientation, device state, and YUV format combinations remain in the release matrix.

### Camera2Live Landscape Photo Fix Details

The no-argument `takePhoto()` API was retained and an overload accepting JPEG rotation was added. The base fragment passes
the current `relativeOrientation`; callers using the helper directly retain the old fallback. Request orientation and
`CombinedCaptureResult.orientation` use the same captured value. The orientation source is rebuilt after camera switching,
`ORIENTATION_UNKNOWN` is ignored, and 316–359 degrees correctly maps to `Surface.ROTATION_0`.

Ordinary JPEG files are decoded and transformed using `decodeExifOrientation(result.orientation)` and rewritten at JPEG
quality 100, making orientation and mirroring independent of EXIF viewer behavior. The work runs on `Dispatchers.IO`, all
source and transformed bitmaps are recycled on success and failure, incomplete output is deleted, and `DEPTH_JPEG` is
written unchanged. Device testing passed front/back portrait and both landscape directions, camera switching, preview,
and recording regression.

### Camera2Live Raw H.264 Landscape Fix Details

Problem evidence included `ffprobe` results of `width=1080`, `height=1920`, and unavailable SAR/DAR for a landscape raw
stream. The old P and SP strategies fixed back-camera rotation at 90 degrees; the encoder used display-derived
`previewSize`; the hot-path strategy API did not carry the recording's locked angle; and the front path retained fixed
270-degree handling. The old front SP branch also passed I420 data to NV21 mirror/rotation functions.

The recording button now reads `relativeOrientation.value` once and passes it into encoder initialization. The existing
`extraInitializeCameraForRecording(bitrate)` remains for compatibility and a rotation-aware overload was added. Without
an explicit angle, legacy portrait defaults remain: 90 degrees for back and 270 for front. Invalid angles fail fast.

The locked angle applies to both lenses: 0/180 keeps dimensions and 90/270 swaps them. Encoder dimensions come directly
from `selectedSizeFromCamera` and that angle. The public `IDataProcessStrategy.doProcess()` signature is unchanged; the
angle is supplied through strategy construction and an internal factory path.

`EncoderStrategyYuv420P` and `EncoderStrategyYuv420Sp` now use fused `YuvUtil.transformI420()` to perform locked rotation,
front-camera horizontal mirroring, and I420/NV12 output in one JNI call. This removes repeated JNI crossings and
intermediate Java arrays. The zero-degree, non-mirrored I420 path reuses the input array. Native critical-array access
avoids explicit native input/output copies and allocates native scratch buffers only when libyuv requires multiple steps.
Unit tests cover dimension retention for 0/180, swapping for 90/270, invalid angles, and non-positive dimensions.

The thread, MediaCodec, memory, and JPEG strategy follow-up from 2026-08-18 is documented in the maintainer-approved
Chinese-only [`2026-08-18-camera-performance-follow-up-zh.md`](2026-08-18-camera-performance-follow-up-zh.md).

The device matrix includes back and front portrait/90/270 recordings, stable SPS and orientation when rotating during an
active recording, both YUV420P and YUV420SP devices, correct color, and absence of corruption or stretching. The available
device passed the Camera2Live recording tests on 2026-08-17; cross-device sensor-orientation and YUV-format coverage remains
a release regression item.

---

## 7. Review Record

### `5a23d6c93` — Codex Review Remediation (Verified)

The SP2/3a and 3b specification follow-up passed a file-by-file manual review with two substantive corrections:

- **SP2 disable behavior**: The earlier implementation still created and bound `imageAnalyzer` but omitted
  `setAnalyzer`. CameraX 1.4.1 sends frames only after an analyzer is set, but binding `ImageAnalysis` still creates and
  negotiates the analysis pipeline and output surface and participates in use-case and resolution selection. With
  `ENABLE_LUMINOSITY_ANALYSIS=false`, `imageAnalyzer` is now `null` and the lifecycle list contains only preview and image
  capture by default. The optimized analyzer remains available when explicitly enabled.
- **`averageLuma`** now requires a positive stride, with tests for zero and negative values.
- **Two 3b design corrections**:
  1. Combining the old 90/270 branches into one “mirror plus rotate 270” branch was unsafe because their mirror axes and
     operation order differed. The temporary design preserved both sensor-orientation branches until device tests proved
     that the old path itself had orientation and color defects. It was then replaced by relative recording rotation plus
     final horizontal mirroring.
  2. The old specification claimed byte-for-byte equivalence, but NV21 chroma functions were processing I420 data. That
     path could only use device output as its baseline, not byte equality.
- **3a/M7, changelog, and progress table** were corrected to refer to `b1ff02064`. The 3b implementation superseded the
  cache for built-in strategies; the public parameter remains only until the next breaking release.

The opt-in SP2 analyzer is still executable code and is not dead code. After the 3b native replacement, the old front
Kotlin branch has no execution path and remains available through Git history instead of a large commented-out block;
unused imports were also removed for zero-tolerance detekt compliance.

### `32d3af9a2` — Seven Camera Performance Findings (Verified)

Native and Kotlin reviews found no CRITICAL or HIGH problem in the remediation:

1. Recording frames use `imageReaderHandler`, which belongs to a different HandlerThread than `cameraHandler`, without
   changing `image.close()` ordering.
2. `CameraAvcEncoder` returns when no frame is available, retains the input buffer ID, does not queue empty frames, and
   advances PTS only for real frames.
3. Rotation, mirroring, and I420/NV12 conversion are fused into `YuvUtil.transformI420()`. The **back-camera + YUV420P +
   zero-degree rotation** path is an identity fast path with unchanged bytes. Parameter order matches `([BIIIZI)[B`.
4. `Bitmap.toBytes()` uses one allocation with `ByteBuffer.wrap()`. Intermediate photo bitmaps are recycled early and
   `recycledSafety()` prevents duplicate recycling on exceptional paths.
5. Output buffers are released in `finally` immediately after copying; encoded-byte callbacks run on a dedicated serial
   thread.
6. CameraX diagnostics are disabled by default. Codec enumeration runs on `Dispatchers.Default`, results are cached by
   `cameraId:rotation` under synchronization, and the old HEVC-as-AVC query was corrected.
7. `JpegOutputStrategy` defaults both modules to `PIXEL_NORMALIZED`, preserving verified behavior; `EXIF_ONLY` is opt-in.

The fused JNI is byte-for-byte equivalent to the already-corrected **I420 rotation → I420 mirror → NV12 conversion**
sequence in the parent of `32d3af9a2`. The implementation uses correctly nested/released critical regions, RAII scratch
buffers, length checks, and 64-bit overflow protection. One maintainability LOW remains: document the invariant around
nested critical regions in code. This is a separate code-review item and must not be described as a device-observable
regression.

The Kotlin review found one MEDIUM: removing an input buffer ID before `getInputBuffer`, capacity checks, and
`queueInputBuffer` completed could orphan the ID on exceptions and eventually starve the encoder. Commit `af35be538`
fixed it.

### `af35be538` — Input Buffer Leak Fix (MEDIUM, Verified)

- **Core fix**: `PendingInputBuffers.drain()` uses peek-then-remove. It reads `bufferIds.first()` and removes the ID only
  after successful submission. A non-fatal frame rejection calls `onFailure`, returns, and keeps the ID for the next
  frame. A real codec failure follows the failure policy below and clears the pool instead of reusing the ID.
- **Failure classification**: An oversized frame (`IllegalArgumentException`) drops only that frame and retains the ID.
  `CodecException`, `IllegalStateException`, and `onError` call `stopAcceptingFrames()`, which clears frames and IDs and
  permanently rejects new frames.
- **Lifecycle**: `acceptingFrames`, `stopped`, and `released` are `AtomicBoolean`s. `offerDataIntoQueue` and
  `onInputBufferAvailable` perform a second lifecycle check around their respective critical sections. `stop()` and
  `release()` use `compareAndSet` for idempotence, and late teardown frames are rejected cleanly.
- **Concurrency**: Drain runs under `inputBufferLock`. Re-entering synchronized code from `onFailure` is safe, the catch
  returns immediately, and `clear()` does not race with iteration. No reference to the old
  `availableInputBufferIds` field remains.
- **Tests**: `PendingInputBuffersTest` covers retaining and reusing the ID after an oversized frame and consuming the ID
  after a normal submission.
- **Follow-up document**: The maintainer explicitly approved deleting
  `2026-08-18-camera-performance-follow-up.md`; its Chinese counterpart remains and has updated change #2 wording and
  validation items.

> Baseline recording orientation and color passed on the available device on 2026-08-17. After the fused-JNI change,
> cross-device regression still needs the available-device rerun, a front camera with `SENSOR_ORIENTATION=90`, and
> different YUV output formats. Different vendor MediaCodec behavior and target-gallery rendering of `EXIF_ONLY` also
> remain to be verified.
