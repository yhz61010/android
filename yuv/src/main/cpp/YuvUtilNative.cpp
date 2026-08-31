#include <jni.h>

#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <new>

#include "YuvConvert.h"

#define YUV_PACKAGE_BASE "com/leovp/yuv/"

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

bool CheckedMul(int64_t left, int64_t right, int64_t *result) {
    if (left < 0 || right < 0 || (right != 0 && left > INT64_MAX / right)) return false;
    *result = left * right;
    return true;
}

bool CheckedFrameSize(jint width, jint height, jint bytes_numerator, jint bytes_denominator,
                      jsize *size) {
    int64_t pixels;
    if (width <= 0 || height <= 0 ||
        !CheckedMul(static_cast<int64_t>(width), height, &pixels) ||
        pixels > INT64_MAX / bytes_numerator) {
        return false;
    }
    const int64_t bytes = pixels * bytes_numerator / bytes_denominator;
    if (bytes <= 0 || bytes > std::numeric_limits<jsize>::max()) return false;
    *size = static_cast<jsize>(bytes);
    return true;
}

bool CheckedI420Size(jint width, jint height, jsize *size) {
    return (width & 1) == 0 && (height & 1) == 0 &&
           CheckedFrameSize(width, height, 3, 2, size);
}

bool CheckedRgb24Size(jint width, jint height, jsize *size) {
    return CheckedFrameSize(width, height, 3, 1, size);
}

bool IsRotationValid(jint degree) {
    return degree == 0 || degree == 90 || degree == 180 || degree == 270;
}

bool IsFilterModeValid(jint mode) {
    return mode >= 0 && mode <= 3;
}

bool RequireI420Dimensions(JNIEnv *env, jint width, jint height, jsize *size) {
    if (!CheckedI420Size(width, height, size)) {
        ThrowIllegalArgumentException(env, "Width and height must be positive even values without size overflow");
        return false;
    }
    return true;
}

bool RequireArrayLength(JNIEnv *env, jbyteArray source, int64_t required, const char *message) {
    if (source == nullptr) {
        ThrowIllegalArgumentException(env, "Source array must not be null");
        return false;
    }
    if (required < 0 || required > std::numeric_limits<jsize>::max() ||
        env->GetArrayLength(source) < required) {
        ThrowIllegalArgumentException(env, message);
        return false;
    }
    return true;
}

bool CheckedPlaneSpan(jint row_stride, jint pixel_stride, jint width, jint height,
                      int64_t *required_span) {
    if (row_stride <= 0 || pixel_stride <= 0 || width <= 0 || height <= 0) return false;
    int64_t last_pixel_offset;
    int64_t last_row_offset;
    if (!CheckedMul(static_cast<int64_t>(width - 1), pixel_stride, &last_pixel_offset) ||
        last_pixel_offset + 1 > row_stride ||
        !CheckedMul(static_cast<int64_t>(height - 1), row_stride, &last_row_offset) ||
        last_row_offset > INT64_MAX - last_pixel_offset - 1) {
        return false;
    }
    *required_span = last_row_offset + last_pixel_offset + 1;
    return true;
}

template<typename Operation>
jbyteArray ProcessByteArray(JNIEnv *env, jbyteArray source_array, jsize required_source_size,
                            jsize output_size, Operation operation) {
    if (!RequireArrayLength(env, source_array, required_source_size,
                            "Source array is smaller than the requested frame")) {
        return nullptr;
    }

    jbyteArray output_array = env->NewByteArray(output_size);
    if (output_array == nullptr) {
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to allocate output array");
        return nullptr;
    }

    jbyte *source = env->GetByteArrayElements(source_array, nullptr);
    if (source == nullptr) {
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to access source array");
        env->DeleteLocalRef(output_array);
        return nullptr;
    }
    jbyte *output = env->GetByteArrayElements(output_array, nullptr);
    if (output == nullptr) {
        env->ReleaseByteArrayElements(source_array, source, JNI_ABORT);
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to access output array");
        env->DeleteLocalRef(output_array);
        return nullptr;
    }

    const int status = operation(reinterpret_cast<const uint8_t *>(source),
                                 reinterpret_cast<uint8_t *>(output));
    env->ReleaseByteArrayElements(output_array, output, status == 0 ? 0 : JNI_ABORT);
    env->ReleaseByteArrayElements(source_array, source, JNI_ABORT);
    if (status != 0) {
        env->DeleteLocalRef(output_array);
        ThrowIllegalStateException(env, "Native YUV conversion failed");
        return nullptr;
    }
    return output_array;
}

}  // namespace

JNIEXPORT jbyteArray Android420Direct_To_I420(
        JNIEnv *env, jobject, jobject y_buffer, jobject u_buffer, jobject v_buffer,
        jint y_row_stride, jint u_row_stride, jint v_row_stride,
        jint y_pixel_stride, jint u_pixel_stride, jint v_pixel_stride,
        jint width, jint height, jboolean vertically_flip, jint degree) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    if (y_buffer == nullptr || u_buffer == nullptr || v_buffer == nullptr ||
        y_pixel_stride != 1 || u_pixel_stride != v_pixel_stride) {
        ThrowIllegalArgumentException(env, "Direct YUV planes have unsupported pixel strides");
        return nullptr;
    }

    int64_t y_span;
    int64_t u_span;
    int64_t v_span;
    const jint chroma_width = width / 2;
    const jint chroma_height = height / 2;
    if (!CheckedPlaneSpan(y_row_stride, y_pixel_stride, width, height, &y_span) ||
        !CheckedPlaneSpan(u_row_stride, u_pixel_stride, chroma_width, chroma_height, &u_span) ||
        !CheckedPlaneSpan(v_row_stride, v_pixel_stride, chroma_width, chroma_height, &v_span)) {
        ThrowIllegalArgumentException(env, "Plane dimensions or strides are invalid");
        return nullptr;
    }

    auto *source_y = static_cast<const uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    auto *source_u = static_cast<const uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    auto *source_v = static_cast<const uint8_t *>(env->GetDirectBufferAddress(v_buffer));
    const jlong y_capacity = env->GetDirectBufferCapacity(y_buffer);
    const jlong u_capacity = env->GetDirectBufferCapacity(u_buffer);
    const jlong v_capacity = env->GetDirectBufferCapacity(v_buffer);
    if (source_y == nullptr || source_u == nullptr || source_v == nullptr ||
        y_capacity < y_span || u_capacity < u_span || v_capacity < v_span) {
        ThrowIllegalArgumentException(env, "Direct plane buffer is smaller than required");
        return nullptr;
    }

    jbyteArray output_array = env->NewByteArray(frame_size);
    if (output_array == nullptr) {
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to allocate I420 output");
        return nullptr;
    }
    jbyte *output = env->GetByteArrayElements(output_array, nullptr);
    if (output == nullptr) {
        if (!env->ExceptionCheck()) ThrowOutOfMemoryError(env, "Unable to access I420 output");
        env->DeleteLocalRef(output_array);
        return nullptr;
    }

    auto *destination_y = reinterpret_cast<uint8_t *>(output);
    auto *destination_u = destination_y + static_cast<int64_t>(width) * height;
    auto *destination_v = destination_u + static_cast<int64_t>(chroma_width) * chroma_height;
    const jint output_width = degree == 90 || degree == 270 ? height : width;
    const jint signed_height = vertically_flip == JNI_TRUE ? -height : height;
    const int status = libyuv::Android420ToI420Rotate(
            source_y, y_row_stride,
            source_u, u_row_stride,
            source_v, v_row_stride,
            u_pixel_stride,
            destination_y, output_width,
            destination_u, output_width / 2,
            destination_v, output_width / 2,
            width, signed_height,
            static_cast<libyuv::RotationMode>(degree));
    env->ReleaseByteArrayElements(output_array, output, status == 0 ? 0 : JNI_ABORT);
    if (status != 0) {
        env->DeleteLocalRef(output_array);
        ThrowIllegalStateException(env, "Native YUV conversion failed");
        return nullptr;
    }
    return output_array;
}

JNIEXPORT jbyteArray Android420_To_I420(JNIEnv *env, jobject, jbyteArray source,
                                        jint pixel_stride_uv, jint width, jint height,
                                        jboolean vertically_flip, jint degree) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    if (pixel_stride_uv != 1) {
        ThrowIllegalArgumentException(env, "The legacy API only accepts tightly packed planar I420 data");
        return nullptr;
    }
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return android420ToI420(input, pixel_stride_uv, width, height,
                                                        output, vertically_flip, degree);
                            });
}

JNIEXPORT jbyteArray Convert_To_I420(JNIEnv *env, jobject, jbyteArray source, jint format,
                                     jint width, jint height, jboolean vertically_flip,
                                     jint degree) {
    jsize output_size;
    if (!RequireI420Dimensions(env, width, height, &output_size)) return nullptr;
    if (format < 1 || format > 4) {
        ThrowIllegalArgumentException(env, "Format must be I420, NV21, NV12, or YUY2");
        return nullptr;
    }
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    jsize input_size;
    const bool valid_size = format == 4
                            ? CheckedFrameSize(width, height, 2, 1, &input_size)
                            : CheckedI420Size(width, height, &input_size);
    if (!valid_size) {
        ThrowIllegalArgumentException(env, "Input dimensions overflow the supported array size");
        return nullptr;
    }
    return ProcessByteArray(env, source, input_size, output_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return convertToI420(input, input_size, format, width, height,
                                                     output, vertically_flip, degree);
                            });
}

JNIEXPORT jbyteArray MirrorI420(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return mirrorI420(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray FlipVerticallyI420(JNIEnv *env, jobject, jbyteArray source,
                                        jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return flipVerticallyI420(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray RotateI420(JNIEnv *env, jobject, jbyteArray source, jint width,
                                jint height, jint degree) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return rotateI420(input, width, height, output, degree);
                            });
}

JNIEXPORT jbyteArray TransformI420(JNIEnv *env, jobject, jbyteArray source, jint width,
                                   jint height, jint degree, jboolean mirror_horizontally,
                                   jint output_format) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    if (output_format != 1 && output_format != 3) {
        ThrowIllegalArgumentException(env, "Output format must be I420 or NV12");
        return nullptr;
    }

    const bool needs_rotation = degree != 0;
    const bool needs_mirror = mirror_horizontally == JNI_TRUE;
    const bool needs_transform = needs_rotation || needs_mirror;
    const bool needs_first_scratch =
            (output_format == 1 && needs_rotation && needs_mirror) ||
            (output_format == 3 && needs_transform);
    const bool needs_second_scratch = output_format == 3 && needs_rotation && needs_mirror;
    std::unique_ptr<uint8_t[]> first_scratch(
            needs_first_scratch ? new(std::nothrow) uint8_t[frame_size] : nullptr);
    std::unique_ptr<uint8_t[]> second_scratch(
            needs_second_scratch ? new(std::nothrow) uint8_t[frame_size] : nullptr);
    if ((needs_first_scratch && first_scratch == nullptr) ||
        (needs_second_scratch && second_scratch == nullptr)) {
        ThrowOutOfMemoryError(env, "Unable to allocate I420 transform buffer");
        return nullptr;
    }

    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=, &first_scratch, &second_scratch](const uint8_t *input,
                                                                uint8_t *output) {
                                const jint output_width =
                                        needs_rotation && (degree == 90 || degree == 270)
                                        ? height
                                        : width;
                                const jint output_height =
                                        needs_rotation && (degree == 90 || degree == 270)
                                        ? width
                                        : height;
                                int status = 0;
                                if (output_format == 1) {
                                    if (!needs_transform) {
                                        std::memcpy(output, input, frame_size);
                                    } else if (!needs_mirror) {
                                        status = rotateI420(input, width, height, output, degree);
                                    } else if (!needs_rotation) {
                                        status = mirrorI420(input, width, height, output);
                                    } else {
                                        status = rotateI420(input, width, height,
                                                            first_scratch.get(), degree);
                                        if (status == 0) {
                                            status = mirrorI420(first_scratch.get(), output_width,
                                                               output_height, output);
                                        }
                                    }
                                    return status;
                                }
                                if (!needs_transform) return i420ToNv12(input, width, height, output);

                                uint8_t *transformed = first_scratch.get();
                                if (!needs_mirror) {
                                    status = rotateI420(input, width, height, transformed, degree);
                                } else if (!needs_rotation) {
                                    status = mirrorI420(input, width, height, transformed);
                                } else {
                                    status = rotateI420(input, width, height,
                                                        first_scratch.get(), degree);
                                    if (status == 0) {
                                        status = mirrorI420(first_scratch.get(), output_width,
                                                           output_height, second_scratch.get());
                                        transformed = second_scratch.get();
                                    }
                                }
                                return status == 0
                                       ? i420ToNv12(transformed, output_width, output_height, output)
                                       : status;
                            });
}

JNIEXPORT jbyteArray ScaleI420(JNIEnv *env, jobject, jbyteArray source, jint width,
                               jint height, jint dst_width, jint dst_height, jint mode) {
    jsize source_size;
    jsize output_size;
    if (!RequireI420Dimensions(env, width, height, &source_size) ||
        !RequireI420Dimensions(env, dst_width, dst_height, &output_size)) {
        return nullptr;
    }
    if (!IsFilterModeValid(mode)) {
        ThrowIllegalArgumentException(env, "Scale filter mode must be between 0 and 3");
        return nullptr;
    }
    return ProcessByteArray(env, source, source_size, output_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return scaleI420(input, width, height, output,
                                                 dst_width, dst_height, mode);
                            });
}

JNIEXPORT jbyteArray CropI420(JNIEnv *env, jobject, jbyteArray source, jint width,
                              jint height, jint dst_width, jint dst_height, jint left,
                              jint top) {
    jsize source_size;
    jsize output_size;
    if (!RequireI420Dimensions(env, width, height, &source_size) ||
        !RequireI420Dimensions(env, dst_width, dst_height, &output_size)) {
        return nullptr;
    }
    if (left < 0 || top < 0 || (left & 1) != 0 || (top & 1) != 0 ||
        left > width || top > height || dst_width > width - left ||
        dst_height > height - top) {
        ThrowIllegalArgumentException(env, "Crop rectangle must be even and inside the source frame");
        return nullptr;
    }
    return ProcessByteArray(env, source, source_size, output_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return cropI420(input, source_size, width, height, output,
                                                dst_width, dst_height, left, top);
                            });
}

JNIEXPORT jbyteArray I420ToNV21(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return i420ToNv21(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray I420ToNV12(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return i420ToNv12(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray NV21ToI420(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return nv21ToI420(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray NV12ToI420(JNIEnv *env, jobject, jbyteArray source, jint width,
                                jint height, jint degree) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    if (!IsRotationValid(degree)) {
        ThrowIllegalArgumentException(env, "Rotation must be 0, 90, 180, or 270 degrees");
        return nullptr;
    }
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return nv12ToI420(input, width, height, output, degree);
                            });
}

JNIEXPORT jbyteArray MirrorNV12(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return mirrorNV12(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray ScaleNV12(JNIEnv *env, jobject, jbyteArray source, jint width,
                               jint height, jint dst_width, jint dst_height, jint mode) {
    jsize source_size;
    jsize output_size;
    if (!RequireI420Dimensions(env, width, height, &source_size) ||
        !RequireI420Dimensions(env, dst_width, dst_height, &output_size)) {
        return nullptr;
    }
    if ((dst_width & 7) != 0 || (dst_height & 7) != 0) {
        ThrowIllegalArgumentException(env, "Destination dimensions must be multiples of 8");
        return nullptr;
    }
    if (!IsFilterModeValid(mode)) {
        ThrowIllegalArgumentException(env, "Scale filter mode must be between 0 and 3");
        return nullptr;
    }
    return ProcessByteArray(env, source, source_size, output_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return scaleNV12(input, width, height, output,
                                                 dst_width, dst_height, mode);
                            });
}

JNIEXPORT jbyteArray NV21ToNV12(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize frame_size;
    if (!RequireI420Dimensions(env, width, height, &frame_size)) return nullptr;
    return ProcessByteArray(env, source, frame_size, frame_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return nv21ToNV12(input, width, height, output);
                            });
}

JNIEXPORT jbyteArray I420ToRGB24(JNIEnv *env, jobject, jbyteArray source, jint width, jint height) {
    jsize source_size;
    jsize output_size;
    if (!RequireI420Dimensions(env, width, height, &source_size) ||
        !CheckedRgb24Size(width, height, &output_size)) {
        if (!env->ExceptionCheck()) {
            ThrowIllegalArgumentException(env, "RGB24 output size exceeds the supported array size");
        }
        return nullptr;
    }
    return ProcessByteArray(env, source, source_size, output_size,
                            [=](const uint8_t *input, uint8_t *output) {
                                return i420ToRgb24(input, width, height, output);
                            });
}

static JNINativeMethod methods[] = {
        {"android420ToI420Direct",
         "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIIIIIIZI)[B",
         reinterpret_cast<void *>(Android420Direct_To_I420)},
        {"android420ToI420",   "([BIIIZI)[B",  reinterpret_cast<void *>(Android420_To_I420)},
        {"convertToI420",      "([BIIIZI)[B",  reinterpret_cast<void *>(Convert_To_I420)},
        {"mirrorI420",         "([BII)[B",     reinterpret_cast<void *>(MirrorI420)},
        {"flipVerticallyI420", "([BII)[B",     reinterpret_cast<void *>(FlipVerticallyI420)},
        {"rotateI420",         "([BIII)[B",    reinterpret_cast<void *>(RotateI420)},
        {"transformI420",      "([BIIIZI)[B",  reinterpret_cast<void *>(TransformI420)},
        {"scaleI420",          "([BIIIII)[B",  reinterpret_cast<void *>(ScaleI420)},
        {"cropI420",           "([BIIIIII)[B", reinterpret_cast<void *>(CropI420)},
        {"i420ToNv21",         "([BII)[B",     reinterpret_cast<void *>(I420ToNV21)},
        {"i420ToNv12",         "([BII)[B",     reinterpret_cast<void *>(I420ToNV12)},
        {"nv21ToI420",         "([BII)[B",     reinterpret_cast<void *>(NV21ToI420)},
        {"nv12ToI420",         "([BIII)[B",    reinterpret_cast<void *>(NV12ToI420)},
        {"mirrorNv12",         "([BII)[B",     reinterpret_cast<void *>(MirrorNV12)},
        {"scaleNv12",          "([BIIIII)[B",  reinterpret_cast<void *>(ScaleNV12)},
        {"nv21ToNv12",         "([BII)[B",     reinterpret_cast<void *>(NV21ToNV12)},
        {"i420ToRgb24",        "([BII)[B",     reinterpret_cast<void *>(I420ToRGB24)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass clazz = env->FindClass(YUV_PACKAGE_BASE "YuvUtil");
    if (clazz == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
