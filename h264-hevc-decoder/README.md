# H.264/HEVC decoder

This AAR contains only the FFmpeg H.264 and HEVC software decoders. It supports `arm64-v8a`,
`armeabi-v7a`, `x86`, and `x86_64`.

## Choose exactly one FFmpeg runtime

Use this artifact only when the application needs video decoding without ADPCM IMA QT. Use
`adpcm-ima-qt-codec-h264-hevc-decoder` when both audio and video features are required. Never combine
this artifact with that combined artifact or with `adpcm-ima-qt-codec`.

Gradle consumers are protected by a shared capability in Gradle Module Metadata. Maven/POM-only
consumers must enforce the same rule themselves. Do not use `pickFirst` to hide duplicate FFmpeg
libraries.

## Lifecycle

`H264HevcDecoder` implements `Closeable`. Use an instance for one stream at a time, serialize decode
operations through that instance, and release it with `use {}` or `close()`. Closing is idempotent;
decoding before initialization or after closing fails with `IllegalStateException`.

## Decoder output contract

`decode(packet)` remains available for callers that consume at most one frame per call. FFmpeg may
produce zero or multiple frames for one packet, so the decoder retains additional frames instead of
discarding them. New code that needs complete output should use `decodeFrames(packet)`. If compatibility
`decode()` calls retained extra frames, `decodeFrames()` returns those first, followed by all frames made
available while processing the current packet.

After the final packet, call `drain()` exactly as the end-of-stream step. It returns retained frames
and frames delayed internally by the codec, including frames delayed by B-frame reordering. Once
draining has started, the instance does not accept more input; close and reinitialize it, or use a new
instance, for another stream.
