#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "logger.h"
#include "YuvConvert.h"

#define YUV_PACKAGE_BASE "com/leovp/yuv_sdk/"

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
JNIEXPORT jbyteArray Convert_To_I420(JNIEnv *env, jobject thiz, jbyteArray yuvSrc, jint format, jint w, jint h, jboolean vertically_flip, jint degree) {
    int yuv_len = env->GetArrayLength(yuvSrc);
    uint8_t *src_yuv_data = new uint8_t[yuv_len];
    env->GetByteArrayRegion(yuvSrc, 0, yuv_len, reinterpret_cast<jbyte *>(src_yuv_data));

    int Ysize = w * h;
    size_t src_size = Ysize * 3 / 2;

    uint8_t *i420_data = new uint8_t[yuv_len];

    uint8_t *pDstY = i420_data;
    uint8_t *pDstU = i420_data + Ysize;
    uint8_t *pDstV = pDstU + (Ysize / 4);

    uint32_t fourcc = libyuv::FOURCC_I420;
    switch (format) {
        case 2:
            fourcc = libyuv::FOURCC_NV21;
            break;
        case 3:
            fourcc = libyuv::FOURCC_NV12;
            break;
        case 4:
            fourcc = libyuv::FOURCC_YUY2;
            break;
    }

    int verticalFlip = 1;
    if (JNI_TRUE == vertically_flip) verticalFlip = -1;
    int base_dst_stride_dimension = w;
    if (90 == degree || 270 == degree) base_dst_stride_dimension = h;
    int retVal = libyuv::ConvertToI420(src_yuv_data, src_size,
                                       pDstY, base_dst_stride_dimension,
                                       pDstU, base_dst_stride_dimension / 2,
                                       pDstV, base_dst_stride_dimension / 2,
                                       0, 0,
                                       w, verticalFlip * h,
                                       w, h,
                                       (libyuv::RotationMode) degree, fourcc);
    if (retVal < 0) {
        return nullptr;
    }

    /*libyuv::ConvertToI420(const uint8* src_frame, size_t src_size,
     uint8* dst_y, int dst_stride_y,
     uint8* dst_u, int dst_stride_u,
     uint8* dst_v, int dst_stride_v,
     int crop_x, int crop_y,
     int src_width, int src_height,
     int crop_width, int crop_height,
     enum RotationMode rotation,
     uint32 format);*/

    jbyteArray dst_i420_data = env->NewByteArray(yuv_len);
    env->SetByteArrayRegion(dst_i420_data, 0, yuv_len, reinterpret_cast<jbyte *>(i420_data));

    delete[] i420_data;
    delete[] src_yuv_data;

    return dst_i420_data;
}

JNIEXPORT jbyteArray MirrorI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

    mirrorI420(src_i420_data, width, height, dst_i420_data);
    delete[] src_i420_data;

    jbyteArray mirror_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(mirror_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return mirror_i420_array;
}

/**
 * @param degree    0: No rotation.
 *                 90: Rotate 90 degrees clockwise.
 *                180: Rotate 180 degrees.
 *                270: Rotate 270 degrees clockwise.
 */
JNIEXPORT jbyteArray RotateI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height, jint degree) {
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

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
JNIEXPORT jbyteArray ScaleI420(JNIEnv *env, jobject thiz,
                               jbyteArray i420Src, jint width, jint height,
                               jint dst_width, jint dst_height, jint mode) {
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = sizeof(jbyte) * dst_width * dst_height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

    scaleI420(src_i420_data, width, height, dst_i420_data, dst_width, dst_height, mode);
    delete[] src_i420_data;

    jbyteArray scale_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(scale_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[] dst_i420_data;
    return scale_i420_array;
}

JNIEXPORT jbyteArray CropI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height, jint dst_width, jint dst_height, jint left, jint top) {
    if (left + dst_width > width || top + dst_height > height) {
        return nullptr;
    }
    if (left % 2 != 0 || top % 2 != 0) {
        return nullptr;
    }
    
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_i420_len = sizeof(jbyte) * dst_width * dst_height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

    cropI420(src_i420_data, src_i420_len, width, height, dst_i420_data, dst_width, dst_height, left, top);
    delete[] src_i420_data;

    jbyteArray crop_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(crop_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return crop_i420_array;
}

JNIEXPORT jbyteArray I420ToNV21(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_nv21_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_nv21_data = new uint8_t[dst_nv21_len];

    i420ToNv21(src_i420_data, width, height, dst_nv21_data);
    delete[] src_i420_data;

    jbyteArray nv21_array = env->NewByteArray(dst_nv21_len);
    env->SetByteArrayRegion(nv21_array, 0, dst_nv21_len, reinterpret_cast<const jbyte *>(dst_nv21_data));
    delete[]  dst_nv21_data;
    return nv21_array;
}

JNIEXPORT jbyteArray I420ToNV12(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height) {
    int src_i420_len = env->GetArrayLength(i420Src);
    uint8_t *src_i420_data = new uint8_t[src_i420_len];
    env->GetByteArrayRegion(i420Src, 0, src_i420_len, reinterpret_cast<jbyte *>(src_i420_data));

    int dst_nv12_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_nv12_data = new uint8_t[dst_nv12_len];

    i420ToNv12(src_i420_data, width, height, dst_nv12_data);
    delete[] src_i420_data;

    jbyteArray nv12_array = env->NewByteArray(dst_nv12_len);
    env->SetByteArrayRegion(nv12_array, 0, dst_nv12_len, reinterpret_cast<const jbyte *>(dst_nv12_data));
    delete[]  dst_nv12_data;
    return nv12_array;
}

JNIEXPORT jbyteArray NV21ToI420(JNIEnv *env, jobject thiz, jbyteArray nv21Src, jint width, jint height) {
    int src_nv21_len = env->GetArrayLength(nv21Src);
    uint8_t *src_nv21_data = new uint8_t[src_nv21_len];
    env->GetByteArrayRegion(nv21Src, 0, src_nv21_len, reinterpret_cast<jbyte *>(src_nv21_data));

    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

    nv21ToI420(src_nv21_data, width, height, dst_i420_data);
    delete[] src_nv21_data;

    jbyteArray i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return i420_array;
}

JNIEXPORT jbyteArray NV12ToI420(JNIEnv *env, jobject thiz, jbyteArray nv12Src, jint width, jint height) {
    int src_nv12_len = env->GetArrayLength(nv12Src);
    uint8_t *src_nv12_data = new uint8_t[src_nv12_len];
    env->GetByteArrayRegion(nv12Src, 0, src_nv12_len, reinterpret_cast<jbyte *>(src_nv12_data));

    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    uint8_t *dst_i420_data = new uint8_t[dst_i420_len];

    nv12ToI420(src_nv12_data, width, height, dst_i420_data);
    delete[] src_nv12_data;

    jbyteArray i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    delete[]  dst_i420_data;
    return i420_array;
}

// =============================

static JNINativeMethod methods[] = {
        {"convertToI420", "([BIIIZI)[B", (void *) Convert_To_I420},
        {"mirrorI420",    "([BII)[B",    (void *) MirrorI420},
        {"rotateI420",    "([BIII)[B",   (void *) RotateI420},
        {"scaleI420",     "([BIIIII)[B", (void *) ScaleI420},
        {"cropI420",      "([BIIIIII)[B",(void *) CropI420},
        {"i420ToNv21",    "([BII)[B",    (void *) I420ToNV21},
        {"i420ToNv12",    "([BII)[B",    (void *) I420ToNV12},
        {"nv21ToI420",    "([BII)[B",    (void *) NV21ToI420},
        {"nv12ToI420",    "([BII)[B",    (void *) NV12ToI420},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(YUV_PACKAGE_BASE"YuvUtil");
    if (clz == nullptr) {
        LOGE("JNI_OnLoad FindClass error.");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        LOGE("JNI_OnLoad RegisterNatives error.");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}