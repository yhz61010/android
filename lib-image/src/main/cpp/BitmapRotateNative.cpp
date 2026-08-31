#include "BitmapRotateNative.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <new>
#include <utility>

#define IMAGE_PACKAGE_BASE "com/leovp/image/"

namespace {

void ThrowException(JNIEnv *env, const char *class_name, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
        env->DeleteLocalRef(exception_class);
    }
}

void ThrowIllegalArgumentException(JNIEnv *env, const char *message) {
    ThrowException(env, "java/lang/IllegalArgumentException", message);
}

void ThrowIllegalStateException(JNIEnv *env, const char *message) {
    ThrowException(env, "java/lang/IllegalStateException", message);
}

void ThrowOutOfMemoryError(JNIEnv *env, const char *message) {
    ThrowException(env, "java/lang/OutOfMemoryError", message);
}

bool CheckedPixelCount(jint width, jint height, size_t *pixel_count) {
    if (width <= 0 || height <= 0) return false;
    const uint64_t count = static_cast<uint64_t>(width) * static_cast<uint64_t>(height);
    if (count > std::numeric_limits<size_t>::max() / sizeof(uint32_t)) return false;
    *pixel_count = static_cast<size_t>(count);
    return true;
}

JniBitmap *GetBitmap(JNIEnv *env, jlong handle) {
    if (handle == 0) {
        ThrowIllegalStateException(env, "BitmapProcessor is closed");
        return nullptr;
    }
    auto *bitmap = reinterpret_cast<JniBitmap *>(static_cast<intptr_t>(handle));
    if (bitmap->pixels == nullptr || bitmap->bitmapInfo.width == 0 ||
        bitmap->bitmapInfo.height == 0) {
        ThrowIllegalStateException(env, "Native bitmap data is unavailable");
        return nullptr;
    }
    return bitmap;
}

std::unique_ptr<uint32_t[]> AllocatePixels(JNIEnv *env, jint width, jint height,
                                           size_t *pixel_count) {
    if (!CheckedPixelCount(width, height, pixel_count)) {
        ThrowIllegalArgumentException(env, "Bitmap dimensions are invalid or too large");
        return nullptr;
    }
    std::unique_ptr<uint32_t[]> pixels(new(std::nothrow) uint32_t[*pixel_count]);
    if (pixels == nullptr) ThrowOutOfMemoryError(env, "Unable to allocate native bitmap pixels");
    return pixels;
}

uint8_t InterpolateChannel(uint32_t top_left, uint32_t top_right, uint32_t bottom_left,
                           uint32_t bottom_right, int shift, double x_fraction,
                           double y_fraction) {
    const double top = ((top_left >> shift) & 0xffU) * (1.0 - x_fraction) +
                       ((top_right >> shift) & 0xffU) * x_fraction;
    const double bottom = ((bottom_left >> shift) & 0xffU) * (1.0 - x_fraction) +
                          ((bottom_right >> shift) & 0xffU) * x_fraction;
    return static_cast<uint8_t>(std::round(top * (1.0 - y_fraction) + bottom * y_fraction));
}

uint32_t InterpolatePixel(uint32_t top_left, uint32_t top_right, uint32_t bottom_left,
                          uint32_t bottom_right, double x_fraction, double y_fraction) {
    uint32_t result = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
        result |= static_cast<uint32_t>(InterpolateChannel(
                top_left, top_right, bottom_left, bottom_right, shift,
                x_fraction, y_fraction)) << shift;
    }
    return result;
}

}  // namespace

static jlong NativeSetBitmapData(JNIEnv *env, jobject, jobject bitmap) {
    if (bitmap == nullptr) {
        ThrowIllegalArgumentException(env, "Bitmap must not be null");
        return 0;
    }

    AndroidBitmapInfo bitmap_info{};
    if (AndroidBitmap_getInfo(env, bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        ThrowIllegalStateException(env, "Unable to read Bitmap information");
        return 0;
    }
    if (bitmap_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        ThrowIllegalArgumentException(env, "Bitmap must use ARGB_8888 configuration");
        return 0;
    }
    size_t pixel_count;
    if (!CheckedPixelCount(static_cast<jint>(bitmap_info.width),
                           static_cast<jint>(bitmap_info.height), &pixel_count) ||
        bitmap_info.stride < static_cast<uint64_t>(bitmap_info.width) * sizeof(uint32_t)) {
        ThrowIllegalArgumentException(env, "Bitmap dimensions or stride are invalid");
        return 0;
    }

    std::unique_ptr<JniBitmap> native_bitmap(new(std::nothrow) JniBitmap());
    if (native_bitmap == nullptr) {
        ThrowOutOfMemoryError(env, "Unable to allocate native Bitmap state");
        return 0;
    }
    native_bitmap->pixels.reset(new(std::nothrow) uint32_t[pixel_count]);
    if (native_bitmap->pixels == nullptr) {
        ThrowOutOfMemoryError(env, "Unable to allocate native bitmap pixels");
        return 0;
    }

    void *bitmap_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &bitmap_pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        bitmap_pixels == nullptr) {
        ThrowIllegalStateException(env, "Unable to lock Bitmap pixels");
        return 0;
    }
    const size_t row_bytes = static_cast<size_t>(bitmap_info.width) * sizeof(uint32_t);
    for (uint32_t row = 0; row < bitmap_info.height; ++row) {
        const auto *source_row = static_cast<const uint8_t *>(bitmap_pixels) +
                                 static_cast<size_t>(row) * bitmap_info.stride;
        std::memcpy(native_bitmap->pixels.get() + static_cast<size_t>(row) * bitmap_info.width,
                    source_row, row_bytes);
    }
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        ThrowIllegalStateException(env, "Unable to unlock Bitmap pixels");
        return 0;
    }

    native_bitmap->bitmapInfo = bitmap_info;
    native_bitmap->bitmapInfo.stride = static_cast<uint32_t>(row_bytes);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(native_bitmap.release()));
}

static jobject NativeGetBitmap(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *native_bitmap = GetBitmap(env, handle);
    if (native_bitmap == nullptr) return nullptr;

    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    if (bitmap_class == nullptr || config_class == nullptr) {
        if (bitmap_class != nullptr) env->DeleteLocalRef(bitmap_class);
        if (config_class != nullptr) env->DeleteLocalRef(config_class);
        if (!env->ExceptionCheck()) {
            ThrowIllegalStateException(env, "Unable to resolve Android Bitmap classes");
        }
        return nullptr;
    }
    jfieldID argb_field = env->GetStaticFieldID(
            config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jmethodID create_method = env->GetStaticMethodID(
            bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (argb_field == nullptr || create_method == nullptr || env->ExceptionCheck()) {
        env->DeleteLocalRef(config_class);
        env->DeleteLocalRef(bitmap_class);
        return nullptr;
    }
    jobject config = env->GetStaticObjectField(config_class, argb_field);
    if (config == nullptr) {
        env->DeleteLocalRef(config_class);
        env->DeleteLocalRef(bitmap_class);
        if (!env->ExceptionCheck()) {
            ThrowIllegalStateException(env, "Unable to resolve ARGB_8888 Bitmap configuration");
        }
        return nullptr;
    }
    jobject bitmap = env->CallStaticObjectMethod(bitmap_class, create_method,
                                                  native_bitmap->bitmapInfo.width,
                                                  native_bitmap->bitmapInfo.height, config);
    if (config != nullptr) env->DeleteLocalRef(config);
    env->DeleteLocalRef(config_class);
    env->DeleteLocalRef(bitmap_class);
    if (bitmap == nullptr || env->ExceptionCheck()) {
        if (!env->ExceptionCheck()) {
            ThrowIllegalStateException(env, "Unable to create output Bitmap");
        }
        return nullptr;
    }

    AndroidBitmapInfo output_info{};
    if (AndroidBitmap_getInfo(env, bitmap, &output_info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        output_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        output_info.stride < static_cast<uint64_t>(output_info.width) * sizeof(uint32_t)) {
        env->DeleteLocalRef(bitmap);
        ThrowIllegalStateException(env, "Created Bitmap has invalid pixel storage");
        return nullptr;
    }

    void *bitmap_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &bitmap_pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        bitmap_pixels == nullptr) {
        env->DeleteLocalRef(bitmap);
        ThrowIllegalStateException(env, "Unable to lock created Bitmap pixels");
        return nullptr;
    }
    const size_t row_bytes = static_cast<size_t>(native_bitmap->bitmapInfo.width) * sizeof(uint32_t);
    for (uint32_t row = 0; row < native_bitmap->bitmapInfo.height; ++row) {
        auto *destination_row = static_cast<uint8_t *>(bitmap_pixels) +
                                static_cast<size_t>(row) * output_info.stride;
        std::memcpy(destination_row,
                    native_bitmap->pixels.get() +
                    static_cast<size_t>(row) * native_bitmap->bitmapInfo.width,
                    row_bytes);
    }
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->DeleteLocalRef(bitmap);
        ThrowIllegalStateException(env, "Unable to unlock created Bitmap pixels");
        return nullptr;
    }
    return bitmap;
}

static void NativeFreeBitmapData(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<JniBitmap *>(static_cast<intptr_t>(handle));
}

static void NativeRotateBitmapCcw90(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    const jint old_width = static_cast<jint>(bitmap->bitmapInfo.width);
    const jint old_height = static_cast<jint>(bitmap->bitmapInfo.height);
    size_t pixel_count;
    auto output = AllocatePixels(env, old_height, old_width, &pixel_count);
    if (output == nullptr) return;
    for (jint y = 0; y < old_height; ++y) {
        for (jint x = 0; x < old_width; ++x) {
            const size_t source_index = static_cast<size_t>(y) * old_width + x;
            const size_t destination_index =
                    static_cast<size_t>(old_width - 1 - x) * old_height + y;
            output[destination_index] = bitmap->pixels[source_index];
        }
    }
    bitmap->pixels = std::move(output);
    bitmap->bitmapInfo.width = static_cast<uint32_t>(old_height);
    bitmap->bitmapInfo.height = static_cast<uint32_t>(old_width);
    bitmap->bitmapInfo.stride = static_cast<uint32_t>(old_height * sizeof(uint32_t));
}

static void NativeRotateBitmapCw90(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    const jint old_width = static_cast<jint>(bitmap->bitmapInfo.width);
    const jint old_height = static_cast<jint>(bitmap->bitmapInfo.height);
    size_t pixel_count;
    auto output = AllocatePixels(env, old_height, old_width, &pixel_count);
    if (output == nullptr) return;
    for (jint y = 0; y < old_height; ++y) {
        for (jint x = 0; x < old_width; ++x) {
            const size_t source_index = static_cast<size_t>(y) * old_width + x;
            const size_t destination_index =
                    static_cast<size_t>(x) * old_height + (old_height - 1 - y);
            output[destination_index] = bitmap->pixels[source_index];
        }
    }
    bitmap->pixels = std::move(output);
    bitmap->bitmapInfo.width = static_cast<uint32_t>(old_height);
    bitmap->bitmapInfo.height = static_cast<uint32_t>(old_width);
    bitmap->bitmapInfo.stride = static_cast<uint32_t>(old_height * sizeof(uint32_t));
}

static void NativeRotateBitmap180(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    size_t pixel_count;
    if (!CheckedPixelCount(static_cast<jint>(bitmap->bitmapInfo.width),
                           static_cast<jint>(bitmap->bitmapInfo.height), &pixel_count)) {
        ThrowIllegalStateException(env, "Stored Bitmap dimensions are invalid");
        return;
    }
    std::reverse(bitmap->pixels.get(), bitmap->pixels.get() + pixel_count);
}

static void NativeCropBitmap(JNIEnv *env, jobject, jlong handle, jint left,
                             jint top, jint right, jint bottom) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    const jint old_width = static_cast<jint>(bitmap->bitmapInfo.width);
    const jint old_height = static_cast<jint>(bitmap->bitmapInfo.height);
    if (left < 0 || top < 0 || left >= right || top >= bottom ||
        right > old_width || bottom > old_height) {
        ThrowIllegalArgumentException(env, "Crop rectangle must be inside the Bitmap");
        return;
    }
    const jint new_width = right - left;
    const jint new_height = bottom - top;
    size_t pixel_count;
    auto output = AllocatePixels(env, new_width, new_height, &pixel_count);
    if (output == nullptr) return;
    for (jint row = 0; row < new_height; ++row) {
        const size_t source_offset = static_cast<size_t>(top + row) * old_width + left;
        const size_t destination_offset = static_cast<size_t>(row) * new_width;
        std::memcpy(output.get() + destination_offset, bitmap->pixels.get() + source_offset,
                    static_cast<size_t>(new_width) * sizeof(uint32_t));
    }
    bitmap->pixels = std::move(output);
    bitmap->bitmapInfo.width = static_cast<uint32_t>(new_width);
    bitmap->bitmapInfo.height = static_cast<uint32_t>(new_height);
    bitmap->bitmapInfo.stride = static_cast<uint32_t>(new_width * sizeof(uint32_t));
}

static void NativeScaleNNBitmap(JNIEnv *env, jobject, jlong handle,
                                jint new_width, jint new_height) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    size_t pixel_count;
    auto output = AllocatePixels(env, new_width, new_height, &pixel_count);
    if (output == nullptr) return;
    const size_t old_width = bitmap->bitmapInfo.width;
    const size_t old_height = bitmap->bitmapInfo.height;
    for (size_t y = 0; y < static_cast<size_t>(new_height); ++y) {
        const size_t source_y = std::min(y * old_height / static_cast<size_t>(new_height),
                                         old_height - 1);
        for (size_t x = 0; x < static_cast<size_t>(new_width); ++x) {
            const size_t source_x = std::min(x * old_width / static_cast<size_t>(new_width),
                                             old_width - 1);
            output[y * static_cast<size_t>(new_width) + x] =
                    bitmap->pixels[source_y * old_width + source_x];
        }
    }
    bitmap->pixels = std::move(output);
    bitmap->bitmapInfo.width = static_cast<uint32_t>(new_width);
    bitmap->bitmapInfo.height = static_cast<uint32_t>(new_height);
    bitmap->bitmapInfo.stride = static_cast<uint32_t>(new_width * sizeof(uint32_t));
}

static void NativeScaleBIBitmap(JNIEnv *env, jobject, jlong handle,
                                jint new_width, jint new_height) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    size_t pixel_count;
    auto output = AllocatePixels(env, new_width, new_height, &pixel_count);
    if (output == nullptr) return;
    const size_t old_width = bitmap->bitmapInfo.width;
    const size_t old_height = bitmap->bitmapInfo.height;
    for (size_t y = 0; y < static_cast<size_t>(new_height); ++y) {
        const double source_y = new_height == 1
                                ? 0.0
                                : static_cast<double>(y) * (old_height - 1) / (new_height - 1);
        const size_t y0 = static_cast<size_t>(source_y);
        const size_t y1 = std::min(y0 + 1, old_height - 1);
        const double y_fraction = source_y - static_cast<double>(y0);
        for (size_t x = 0; x < static_cast<size_t>(new_width); ++x) {
            const double source_x = new_width == 1
                                    ? 0.0
                                    : static_cast<double>(x) * (old_width - 1) / (new_width - 1);
            const size_t x0 = static_cast<size_t>(source_x);
            const size_t x1 = std::min(x0 + 1, old_width - 1);
            const double x_fraction = source_x - static_cast<double>(x0);
            output[y * static_cast<size_t>(new_width) + x] = InterpolatePixel(
                    bitmap->pixels[y0 * old_width + x0], bitmap->pixels[y0 * old_width + x1],
                    bitmap->pixels[y1 * old_width + x0], bitmap->pixels[y1 * old_width + x1],
                    x_fraction, y_fraction);
        }
    }
    bitmap->pixels = std::move(output);
    bitmap->bitmapInfo.width = static_cast<uint32_t>(new_width);
    bitmap->bitmapInfo.height = static_cast<uint32_t>(new_height);
    bitmap->bitmapInfo.stride = static_cast<uint32_t>(new_width * sizeof(uint32_t));
}

static void NativeFlipBitmapHorizontal(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    const size_t width = bitmap->bitmapInfo.width;
    const size_t height = bitmap->bitmapInfo.height;
    for (size_t y = 0; y < height; ++y) {
        std::reverse(bitmap->pixels.get() + y * width,
                     bitmap->pixels.get() + (y + 1) * width);
    }
}

static void NativeFlipBitmapVertical(JNIEnv *env, jobject, jlong handle) {
    JniBitmap *bitmap = GetBitmap(env, handle);
    if (bitmap == nullptr) return;
    const size_t width = bitmap->bitmapInfo.width;
    const size_t height = bitmap->bitmapInfo.height;
    for (size_t y = 0; y < height / 2; ++y) {
        const size_t opposite_y = height - 1 - y;
        for (size_t x = 0; x < width; ++x) {
            std::swap(bitmap->pixels[y * width + x],
                      bitmap->pixels[opposite_y * width + x]);
        }
    }
}

static JNINativeMethod methods[] = {
        {"nativeSetBitmapData", "(Landroid/graphics/Bitmap;)J",
         reinterpret_cast<void *>(NativeSetBitmapData)},
        {"nativeGetBitmap", "(J)Landroid/graphics/Bitmap;",
         reinterpret_cast<void *>(NativeGetBitmap)},
        {"nativeFreeBitmapData", "(J)V", reinterpret_cast<void *>(NativeFreeBitmapData)},
        {"nativeRotateBitmapCcw90", "(J)V", reinterpret_cast<void *>(NativeRotateBitmapCcw90)},
        {"nativeRotateBitmapCw90", "(J)V", reinterpret_cast<void *>(NativeRotateBitmapCw90)},
        {"nativeRotateBitmap180", "(J)V", reinterpret_cast<void *>(NativeRotateBitmap180)},
        {"nativeCropBitmap", "(JIIII)V", reinterpret_cast<void *>(NativeCropBitmap)},
        {"nativeScaleNNBitmap", "(JII)V", reinterpret_cast<void *>(NativeScaleNNBitmap)},
        {"nativeScaleBIBitmap", "(JII)V", reinterpret_cast<void *>(NativeScaleBIBitmap)},
        {"nativeFlipBitmapHorizontal", "(J)V",
         reinterpret_cast<void *>(NativeFlipBitmapHorizontal)},
        {"nativeFlipBitmapVertical", "(J)V", reinterpret_cast<void *>(NativeFlipBitmapVertical)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(IMAGE_PACKAGE_BASE "BitmapProcessor");
    if (clazz == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
