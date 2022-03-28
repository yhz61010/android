#include <jni.h>
#include <cstring>
#include <cstdlib>
#include "YuvConvert.h"

#define YUV_PACKAGE_BASE "com/leovp/yuv_sdk/"

JNIEXPORT jbyteArray Android420_To_I420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                        jbyteArray src_android420, jint src_pixel_stride_uv, jint width, jint height, jboolean vertically_flip, jint degree) {
    int src_android420_len = env->GetArrayLength(src_android420);
    auto *src_android420_data = new uint8_t[src_android420_len];
    env->GetByteArrayRegion(src_android420, 0, src_android420_len, reinterpret_cast<jbyte *>(src_android420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    android420ToI420(src_android420_data, src_pixel_stride_uv, width, height, dst_i420_data, vertically_flip, degree);
    delete[] src_android420_data;

    jbyteArray dst_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(dst_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return dst_i420_array;
}

/**
 * Vertically flip yuv data first then do rotate.
 *
 * @param format The [yuvData] format.
 *               1: I420
 *               2: NV21
 *               3: NV12
 *               4: YUY2
 *
 * @param degree The yuv data should be rotated by degree.
 *                 0: No rotation.
 *                 90: Rotate 90 degrees clockwise.
 *                180: Rotate 180 degrees.
 *                270: Rotate 270 degrees clockwise.
 */
JNIEXPORT jbyteArray Convert_To_I420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                     jbyteArray yuvSrc, jint format, jint width, jint height, jboolean vertically_flip, jint degree) {
    int src_yuv_len = env->GetArrayLength(yuvSrc);
    auto *src_i420_data = new uint8_t[src_yuv_len];
    env->GetByteArrayRegion(yuvSrc, 0, src_yuv_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    convertToI420(src_i420_data, src_yuv_len, format, width, height, dst_i420_data, vertically_flip, degree);
    delete[] src_i420_data;

    jbyteArray dst_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(dst_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return dst_i420_array;
}

JNIEXPORT jbyteArray MirrorI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    mirrorI420(src_i420_data, width, height, dst_i420_data);
    delete[] src_i420_data;

    jbyteArray mirror_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(mirror_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return mirror_i420_array;
}

JNIEXPORT jbyteArray FlipVerticallyI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                        jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    flipVerticallyI420(src_i420_data, width, height, dst_i420_data);
    delete[] src_i420_data;

    jbyteArray vertically_flip_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(vertically_flip_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return vertically_flip_i420_array;
}

/**
 * @param degree    0: No rotation.
 *                 90: Rotate 90 degrees clockwise.
 *                180: Rotate 180 degrees.
 *                270: Rotate 270 degrees clockwise.
 */
JNIEXPORT jbyteArray RotateI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray i420Src, jint width, jint height, jint degree) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    rotateI420(src_i420_data, width, height, dst_i420_data, degree);
    delete[] src_i420_data;

    jbyteArray rotate_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(rotate_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return rotate_i420_array;
}

/**
 * @param mode
 *              kFilterNone = 0,      // Point sample; Fastest.
 *              kFilterLinear = 1,    // Filter horizontally only.
 *              kFilterBilinear = 2,  // Faster than box, but lower quality scaling down.
 *              kFilterBox = 3        // Highest quality.
 */
JNIEXPORT jbyteArray ScaleI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                               jbyteArray i420Src, jint width, jint height,
                               jint dst_width, jint dst_height, jint mode) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * dst_width * dst_height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    scaleI420(src_i420_data, width, height, dst_i420_data, dst_width, dst_height, mode);
    delete[] src_i420_data;

    jbyteArray scale_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(scale_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[] dst_i420_data;
    return scale_i420_array;
}

JNIEXPORT jbyteArray CropI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                              jbyteArray i420Src, jint width, jint height,
                              jint dst_width, jint dst_height, jint left, jint top) {
    if (left + dst_width > width || top + dst_height > height) {
        return nullptr;
    }
    if (left % 2 != 0 || top % 2 != 0) {
        return nullptr;
    }

    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = (int) sizeof(uint8_t) * dst_width * dst_height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    cropI420(src_i420_data, src_i420_len, width, height, dst_i420_data, dst_width, dst_height, left, top);
    delete[] src_i420_data;

    jbyteArray crop_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(crop_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return crop_i420_array;
}

JNIEXPORT jbyteArray I420ToNV21(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_nv21_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_nv21_data = new uint8_t[dst_nv21_len];

    i420ToNv21(src_i420_data, width, height, dst_nv21_data);
    delete[] src_i420_data;

    jbyteArray nv21_array = env->NewByteArray(dst_nv21_len);
    env->SetByteArrayRegion(nv21_array, 0, dst_nv21_len, reinterpret_cast<const jbyte *>(dst_nv21_data));
    delete[]  dst_nv21_data;
    return nv21_array;
}

JNIEXPORT jbyteArray I420ToNV12(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_nv12_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_nv12_data = new uint8_t[dst_nv12_len];

    i420ToNv12(src_i420_data, width, height, dst_nv12_data);
    delete[] src_i420_data;

    jbyteArray nv12_array = env->NewByteArray(dst_nv12_len);
    env->SetByteArrayRegion(nv12_array, 0, dst_nv12_len, reinterpret_cast<const jbyte *>(dst_nv12_data));
    delete[]  dst_nv12_data;
    return nv12_array;
}

JNIEXPORT jbyteArray NV21ToI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray nv21Src, jint width, jint height) {
    int src_nv21_len = env->GetArrayLength(nv21Src);
    auto *src_nv21_data = new uint8_t[src_nv21_len];
    env->GetByteArrayRegion(nv21Src, 0, src_nv21_len, reinterpret_cast<jbyte *>(src_nv21_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    nv21ToI420(src_nv21_data, width, height, dst_i420_data);
    delete[] src_nv21_data;

    jbyteArray i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return i420_array;
}

JNIEXPORT jbyteArray NV12ToI420(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray nv12Src, jint width, jint height, jint degree) {
    int src_nv12_len = env->GetArrayLength(nv12Src);
    auto *src_nv12_data = new uint8_t[src_nv12_len];
    env->GetByteArrayRegion(nv12Src, 0, src_nv12_len, reinterpret_cast<jbyte *>(src_nv12_data));

    int dst_i420_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_i420_data = new uint8_t[dst_i420_len];

    nv12ToI420(src_nv12_data, width, height, dst_i420_data, degree);
    delete[] src_nv12_data;

    jbyteArray i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return i420_array;
}

JNIEXPORT jbyteArray MirrorNV12(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray nv12Src, jint width, jint height) {
    int src_nv12_len = env->GetArrayLength(nv12Src);
    auto *src_nv12_data = new uint8_t[src_nv12_len];
    env->GetByteArrayRegion(nv12Src, 0, src_nv12_len, reinterpret_cast<jbyte *>(src_nv12_data));

    int dst_nv12_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_nv12_data = new uint8_t[dst_nv12_len];

    mirrorNV12(src_nv12_data, width, height, dst_nv12_data);
    delete[] src_nv12_data;

    jbyteArray mirror_nv12_array = env->NewByteArray(dst_nv12_len);
    env->SetByteArrayRegion(mirror_nv12_array, 0, dst_nv12_len, reinterpret_cast<const jbyte *>(dst_nv12_data));
    delete[] dst_nv12_data;
    return mirror_nv12_array;
}

/**
 * @param mode
 *              kFilterNone = 0,      // Point sample; Fastest.
 *              kFilterLinear = 1,    // Filter horizontally only.
 *              kFilterBilinear = 2,  // Faster than box, but lower quality scaling down.
 *              kFilterBox = 3        // Highest quality.
 */
JNIEXPORT jbyteArray ScaleNV12(JNIEnv *env, __attribute__((unused)) jobject thiz,
                               jbyteArray nv12Src, jint width, jint height,
                               jint dst_width, jint dst_height, jint mode) {
    if (dst_width % 8 != 0 || dst_height % 8 != 0) {
        jclass jcls = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(jcls, "Both dst_width and dst_height must be multiple of 8.");
    }

    int src_nv12_len = env->GetArrayLength(nv12Src);
    auto *src_nv12_data = new uint8_t[src_nv12_len];
    env->GetByteArrayRegion(nv12Src, 0, src_nv12_len, reinterpret_cast<jbyte *>(src_nv12_data));

    int dst_nv12_len = (int) sizeof(uint8_t) * dst_width * dst_height * 3 / 2;
    auto *dst_nv12_data = new uint8_t[dst_nv12_len];

    scaleNV12(src_nv12_data, width, height, dst_nv12_data, dst_width, dst_height, mode);
    delete[] src_nv12_data;

    jbyteArray scale_nv12_array = env->NewByteArray(dst_nv12_len);
    env->SetByteArrayRegion(scale_nv12_array, 0, dst_nv12_len, reinterpret_cast<const jbyte *>(dst_nv12_data));
    delete[] dst_nv12_data;
    return scale_nv12_array;
}

JNIEXPORT jbyteArray NV21ToNV12(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray nv21Src, jint width, jint height) {
    int src_nv21_len = env->GetArrayLength(nv21Src);
    auto *src_nv21_data = new uint8_t[src_nv21_len];
    env->GetByteArrayRegion(nv21Src, 0, src_nv21_len, reinterpret_cast<jbyte *>(src_nv21_data));

    int dst_nv12_len = (int) sizeof(uint8_t) * width * height * 3 / 2;
    auto *dst_nv12_data = new uint8_t[dst_nv12_len];

    nv21ToNV12(src_nv21_data, width, height, dst_nv12_data);
    delete[] src_nv21_data;

    jbyteArray dst_nv12_array = env->NewByteArray(dst_nv12_len);
    env->SetByteArrayRegion(dst_nv12_array, 0, dst_nv12_len, reinterpret_cast<const jbyte *>(dst_nv12_data));
    delete[]  dst_nv12_data;
    return dst_nv12_array;
}

// --------------------

JNIEXPORT jbyteArray I420ToRGB24(JNIEnv *env, __attribute__((unused)) jobject thiz,
                                jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    auto *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_rgb24_len = (int) sizeof(uint8_t) * width * height * 3;
    auto *dst_rgb24_data = new uint8_t[dst_rgb24_len];

    i420ToRgb24(src_i420_data, width, height, dst_rgb24_data, dst_rgb24_len);
    delete[] src_i420_data;

    jbyteArray dst_rgb24_array = env->NewByteArray(dst_rgb24_len);
    env->SetByteArrayRegion(dst_rgb24_array, 0, dst_rgb24_len, reinterpret_cast<const jbyte *>(dst_rgb24_data));
    delete[]  dst_rgb24_data;
    return dst_rgb24_array;
}

// =============================

static JNINativeMethod methods[] = {
        {"android420ToI420",   "([BIIIZI)[B",  (void *) Android420_To_I420},
        {"convertToI420",      "([BIIIZI)[B",  (void *) Convert_To_I420},
        {"mirrorI420",         "([BII)[B",     (void *) MirrorI420},
        {"flipVerticallyI420", "([BII)[B",     (void *) FlipVerticallyI420},
        {"rotateI420",         "([BIII)[B",    (void *) RotateI420},
        {"scaleI420",          "([BIIIII)[B",  (void *) ScaleI420},
        {"cropI420",           "([BIIIIII)[B", (void *) CropI420},
        {"i420ToNv21",         "([BII)[B",     (void *) I420ToNV21},
        {"i420ToNv12",         "([BII)[B",     (void *) I420ToNV12},
        {"nv21ToI420",         "([BII)[B",     (void *) NV21ToI420},
        {"nv12ToI420",         "([BIII)[B",    (void *) NV12ToI420},
        {"mirrorNv12",         "([BII)[B",     (void *) MirrorNV12},
        {"scaleNv12",          "([BIIIII)[B",  (void *) ScaleNV12},
        {"nv21ToNv12",         "([BII)[B",     (void *) NV21ToNV12},
        {"i420ToRgb24",        "([BII)[B",     (void *) I420ToRGB24},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clz = env->FindClass(YUV_PACKAGE_BASE"YuvUtil");
    if (clz == nullptr) {
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}