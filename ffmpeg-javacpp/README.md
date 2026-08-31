# FFmpeg JavaCPP ADPCM decoder

This module provides an ADPCM IMA QuickTime decoder through JavaCPP. The published module currently
supports **`arm64-v8a` only** and declares only the `android-arm64` FFmpeg and JavaCPP classifier
dependencies. Those native libraries are resolved transitively rather than embedded in this AAR.
Applications that require `armeabi-v7a`, `x86`, or `x86_64` must use another module.

`AdpcmImaQTDecoder` owns one reusable codec context, packet, and frame. It implements `Closeable`; use
`use {}` or call `close()`. Mono output is returned as `leftBytes to ByteArray(0)`.
