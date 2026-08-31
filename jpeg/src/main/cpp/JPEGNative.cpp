#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <csetjmp>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>

extern "C" {
#include "include/jpeglib.h"
#include "include/jerror.h"
}

#define LOG_TAG "LEO-JPEG"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define JPEG_PACKAGE_BASE "com/leovp/jpeg/"

namespace {

struct JpegErrorManager {
    jpeg_error_mgr manager;
    jmp_buf jumpBuffer;
    bool outOfMemory;
};

struct JpegWriteState {
    jpeg_compress_struct compressor;
    JpegErrorManager error;
    FILE *file;
    uint8_t *row;
    bool compressorCreated;
    bool success;
};

enum WriteResult {
    WRITE_SUCCESS = 0,
    WRITE_FAILED = -1,
    WRITE_OUT_OF_MEMORY = -2,
};

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

METHODDEF(void) JpegErrorExit(j_common_ptr compressor) {
    auto *error = reinterpret_cast<JpegErrorManager *>(compressor->err);
    char message[JMSG_LENGTH_MAX] = {};
    (*compressor->err->format_message)(compressor, message);
    LOGE("JPEG compression failed: %s", message);
    error->outOfMemory = compressor->err->msg_code == JERR_OUT_OF_MEMORY;
    longjmp(error->jumpBuffer, 1);
}

int WriteJpegFile(const uint8_t *bitmap_pixels, const AndroidBitmapInfo *bitmap_info,
                  jint quality, const char *output_path, jboolean optimize,
                  bool *file_was_opened) {
    *file_was_opened = false;
    auto *state = static_cast<JpegWriteState *>(std::calloc(1, sizeof(JpegWriteState)));
    if (state == nullptr) return WRITE_OUT_OF_MEMORY;

    state->compressor.err = jpeg_std_error(&state->error.manager);
    state->error.manager.error_exit = JpegErrorExit;
    if (setjmp(state->error.jumpBuffer) != 0) goto cleanup;

    state->compressorCreated = true;
    jpeg_create_compress(&state->compressor);
    state->file = std::fopen(output_path, "wb");
    if (state->file == nullptr) {
        LOGE("Unable to open JPEG output: %s", output_path);
        goto cleanup;
    }
    jpeg_stdio_dest(&state->compressor, state->file);

    state->compressor.image_width = bitmap_info->width;
    state->compressor.image_height = bitmap_info->height;
    state->compressor.input_components = 3;
    state->compressor.in_color_space = JCS_RGB;
    jpeg_set_defaults(&state->compressor);
    state->compressor.optimize_coding = optimize == JNI_TRUE;
    state->compressor.arith_code = FALSE;
    jpeg_set_quality(&state->compressor, quality, TRUE);

    state->row = static_cast<uint8_t *>(
            std::malloc(static_cast<size_t>(bitmap_info->width) * 3U));
    if (state->row == nullptr) {
        state->error.outOfMemory = true;
        goto cleanup;
    }

    jpeg_start_compress(&state->compressor, TRUE);
    while (state->compressor.next_scanline < state->compressor.image_height) {
        const auto *source_row = bitmap_pixels +
                                 static_cast<size_t>(state->compressor.next_scanline) *
                                 bitmap_info->stride;
        for (uint32_t x = 0; x < bitmap_info->width; ++x) {
            state->row[x * 3U] = source_row[x * 4U];
            state->row[x * 3U + 1U] = source_row[x * 4U + 1U];
            state->row[x * 3U + 2U] = source_row[x * 4U + 2U];
        }
        JSAMPROW row_pointer[1] = {state->row};
        if (jpeg_write_scanlines(&state->compressor, row_pointer, 1) != 1) goto cleanup;
    }
    jpeg_finish_compress(&state->compressor);
    state->success = true;

cleanup:
    *file_was_opened = state->file != nullptr;
    if (state->compressorCreated) jpeg_destroy_compress(&state->compressor);
    if (state->file != nullptr && std::fclose(state->file) != 0) state->success = false;
    std::free(state->row);
    const int result = state->success
                       ? WRITE_SUCCESS
                       : (state->error.outOfMemory ? WRITE_OUT_OF_MEMORY : WRITE_FAILED);
    std::free(state);
    return result;
}

}  // namespace

static jint CompressBitmap(JNIEnv *env, jobject, jobject bitmap, jint quality,
                           jstring output_path, jboolean optimize) {
    if (bitmap == nullptr || output_path == nullptr) {
        ThrowIllegalArgumentException(env, "Bitmap and output path must not be null");
        return -1;
    }
    if (quality < 0 || quality > 100) {
        ThrowIllegalArgumentException(env, "JPEG quality must be between 0 and 100");
        return -1;
    }

    AndroidBitmapInfo bitmap_info{};
    if (AndroidBitmap_getInfo(env, bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        ThrowIllegalStateException(env, "Unable to read Bitmap information");
        return -1;
    }
    if (bitmap_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        ThrowIllegalArgumentException(env, "Bitmap must use ARGB_8888 configuration");
        return -1;
    }
    const uint64_t minimum_stride = static_cast<uint64_t>(bitmap_info.width) * 4U;
    const uint64_t bitmap_span =
            static_cast<uint64_t>(bitmap_info.stride) * bitmap_info.height;
    const uint64_t jpeg_row_size = static_cast<uint64_t>(bitmap_info.width) * 3U;
    if (bitmap_info.width == 0 || bitmap_info.height == 0 ||
        bitmap_info.stride < minimum_stride ||
        bitmap_span > std::numeric_limits<size_t>::max() ||
        jpeg_row_size > std::numeric_limits<size_t>::max()) {
        ThrowIllegalArgumentException(env, "Bitmap dimensions or stride are invalid");
        return -1;
    }

    void *bitmap_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &bitmap_pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        bitmap_pixels == nullptr) {
        ThrowIllegalStateException(env, "Unable to lock Bitmap pixels");
        return -1;
    }

    const char *path = env->GetStringUTFChars(output_path, nullptr);
    if (path == nullptr) {
        AndroidBitmap_unlockPixels(env, bitmap);
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to access output path");
        return -1;
    }
    if (path[0] == '\0') {
        env->ReleaseStringUTFChars(output_path, path);
        AndroidBitmap_unlockPixels(env, bitmap);
        ThrowIllegalArgumentException(env, "Output path must not be empty");
        return -1;
    }

    bool file_was_opened = false;
    const int result = WriteJpegFile(static_cast<const uint8_t *>(bitmap_pixels),
                                     &bitmap_info, quality, path, optimize,
                                     &file_was_opened);
    if (result != WRITE_SUCCESS && file_was_opened) std::remove(path);
    env->ReleaseStringUTFChars(output_path, path);
    const int unlock_result = AndroidBitmap_unlockPixels(env, bitmap);
    if (unlock_result != ANDROID_BITMAP_RESULT_SUCCESS) {
        ThrowIllegalStateException(env, "Unable to unlock Bitmap pixels");
        return -1;
    }
    if (result == WRITE_OUT_OF_MEMORY) {
        ThrowOutOfMemoryError(env, "Unable to allocate JPEG compression memory");
        return -1;
    }
    if (result != WRITE_SUCCESS) {
        ThrowIllegalStateException(env, "JPEG compression failed");
        return -1;
    }
    return 0;
}

static JNINativeMethod methods[] = {
        {"compressBitmap", "(Landroid/graphics/Bitmap;ILjava/lang/String;Z)I",
         reinterpret_cast<void *>(CompressBitmap)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(JPEG_PACKAGE_BASE "JPEGUtil");
    if (clazz == nullptr) {
        env->ExceptionClear();
        return JNI_ERR;
    }
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
