#ifndef LEOANDROIDBASEUTIL_BITMAPROTATE_H
#define LEOANDROIDBASEUTIL_BITMAPROTATE_H

#include <android/bitmap.h>
#include <jni.h>

#include <cstdint>
#include <memory>

class JniBitmap {
public:
    AndroidBitmapInfo bitmapInfo{};
    std::unique_ptr<uint32_t[]> pixels;
};

#endif  // LEOANDROIDBASEUTIL_BITMAPROTATE_H
