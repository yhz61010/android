# Camera Performance Follow-up (2026-08-18)

## Scope

This follow-up closes seven Camera2Live and CameraX performance findings while preserving existing
public output behavior by default.

## Changes

1. Camera2Live recording frames are processed on `imageReaderHandler`; Camera2 control callbacks
   remain on `cameraHandler`.
2. `CameraAvcEncoder` retains available input-buffer IDs until a real frame arrives. Empty input
   buffers are no longer queued and presentation timestamps advance only for submitted frames.
3. Encoder output buffers are released immediately after copying. The independent encoded byte
   array is delivered on a dedicated serial callback thread.
4. `YuvUtil.transformI420()` performs rotation, optional horizontal mirroring, and I420/NV12 output
   in one JNI call. JNI uses critical array access without explicit native input/output clones and
   allocates native scratch memory only when multiple libyuv passes are required.
5. `Bitmap.toBytes()` now allocates one `ByteArray` and wraps it with `ByteBuffer.wrap()` instead of
   allocating a second full-size pixel buffer. The capture paths also recycle a distinct decoded
   source bitmap before allocating raw output bytes or encoding the transformed file.
6. CameraX capability diagnostics are disabled by default. Hosts may opt in through
   `CameraXActivity.isCameraDiagnosticsEnabled()`; expensive codec enumeration runs on
   `Dispatchers.Default` and its formatted result is cached per camera and display rotation.
7. `JpegOutputStrategy` makes JPEG behavior explicit:
   - `PIXEL_NORMALIZED` remains the default for backward compatibility.
   - `EXIF_ONLY` preserves compressed JPEG pixels and writes/retains orientation metadata, avoiding
     full-resolution bitmap decode, transform, and re-encode.

CameraX hosts may override `CameraXActivity.getJpegOutputStrategy()`. Camera2Live callers may set
`Camera2ComponentHelper.jpegOutputStrategy`.

## Verification

- `:yuv:assembleDebug` passed for all configured ABIs.
- `:camera2live:testDebugUnitTest`, `:camerax:testDebugUnitTest`, and
  `:lib-image:testDebugUnitTest` passed with `--rerun-tasks`.
- The target Kotlin modules compiled successfully with `--rerun-tasks`.

No device was connected. Recording direction/color, MediaCodec behavior across vendor codecs, and
`EXIF_ONLY` rendering in target gallery applications still require device regression testing.
