#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "logger.h"

extern "C" {
#include "libyuv.h"
}

using namespace libyuv;

#define YUV_PACKAGE_BASE "com/leovp/yuv_sdk/"

// --------------------

void mirrorI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = src_i420_y_size >> 2;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_i420_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror(src_i420_y_data, width,
                       src_i420_u_data, width >> 1,
                       src_i420_v_data, width >> 1,
                       dst_i420_y_data, width,
                       dst_i420_u_data, width >> 1,
                       dst_i420_v_data, width >> 1,
                       width, height);
}

void rotateI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data, jint degree) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_i420_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    // 要注意这里的 width 和 height 在旋转之后是相反的
    if (degree == libyuv::kRotate90 || degree == libyuv::kRotate270) {
        libyuv::I420Rotate(src_i420_y_data, width,
                           src_i420_u_data, width >> 1,
                           src_i420_v_data, width >> 1,
                           dst_i420_y_data, height,
                           dst_i420_u_data, height >> 1,
                           dst_i420_v_data, height >> 1,
                           width, height,
                           (libyuv::RotationMode) degree);
    } else {
        libyuv::I420Rotate(src_i420_y_data, width,
                           src_i420_u_data, width >> 1,
                           src_i420_v_data, width >> 1,
                           dst_i420_y_data, width,
                           dst_i420_u_data, width >> 1,
                           dst_i420_v_data, width >> 1,
                           width, height,
                           (libyuv::RotationMode) degree);
    }
}

void scaleI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data, jint dst_width,
               jint dst_height, jint mode) {

    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);
    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jint dst_i420_y_size = dst_width * dst_height;
    jint dst_i420_u_size = (dst_width >> 1) * (dst_height >> 1);
    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + dst_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + dst_i420_y_size + dst_i420_u_size;

    libyuv::I420Scale((const uint8_t *) src_i420_y_data, width,
                      (const uint8_t *) src_i420_u_data, width >> 1,
                      (const uint8_t *) src_i420_v_data, width >> 1,
                      width, height,
                      (uint8_t *) dst_i420_y_data, dst_width,
                      (uint8_t *) dst_i420_u_data, dst_width >> 1,
                      (uint8_t *) dst_i420_v_data, dst_width >> 1,
                      dst_width, dst_height,
                      (libyuv::FilterMode) mode);
}

void cropI420(jbyte *src_i420_data, jint src_length, jint width, jint height,
              jbyte *dst_i420_data, jint dst_width, jint dst_height, jint left, jint top) {
    jint dst_i420_y_size = dst_width * dst_height;
    jint dst_i420_u_size = (dst_width >> 1) * (dst_height >> 1);

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + dst_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + dst_i420_y_size + dst_i420_u_size;

    libyuv::ConvertToI420((const uint8_t *) src_i420_data, src_length,
                          (uint8_t *) dst_i420_y_data, dst_width,
                          (uint8_t *) dst_i420_u_data, dst_width >> 1,
                          (uint8_t *) dst_i420_v_data, dst_width >> 1,
                          left, top,
                          width, height,
                          dst_width, dst_height,
                          libyuv::kRotate0, libyuv::FOURCC_I420);
}

// -----------------------------

// nv21 --> i420
void nv21ToI420(jbyte *src_nv21_data, jint width, jint height, jbyte *src_i420_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::NV21ToI420((const uint8_t *) src_nv21_y_data, width,
                       (const uint8_t *) src_nv21_vu_data, width,
                       (uint8_t *) src_i420_y_data, width,
                       (uint8_t *) src_i420_u_data, width >> 1,
                       (uint8_t *) src_i420_v_data, width >> 1,
                       width, height);
}

// i420 --> nv21
void i420ToNv21(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_uv_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::I420ToNV21(
            (const uint8_t *) src_i420_y_data, width,
            (const uint8_t *) src_i420_u_data, width >> 1,
            (const uint8_t *) src_i420_v_data, width >> 1,
            (uint8_t *) src_nv21_y_data, width,
            (uint8_t *) src_nv21_uv_data, width,
            width, height);
}

// nv12 --> i420
void nv12ToI420(jbyte *Src_data, jint src_width, jint src_height, jbyte *Dst_data) {
    // NV12 video size
    jint NV12_Size = src_width * src_height * 3 / 2;
    jint NV12_Y_Size = src_width * src_height;

    // YUV420 video size
    jint I420_Size = src_width * src_height * 3 / 2;
    jint I420_Y_Size = src_width * src_height;
    jint I420_U_Size = (src_width >> 1) * (src_height >> 1);
    jint I420_V_Size = I420_U_Size;

    // src: buffer address of Y channel and UV channel
    jbyte *Y_data_Src = Src_data;
    jbyte *UV_data_Src = Src_data + NV12_Y_Size;
    jint src_stride_y = src_width;
    jint src_stride_uv = src_width;

    //dst: buffer address of Y channel、U channel and V channel
    jbyte *Y_data_Dst = Dst_data;
    jbyte *U_data_Dst = Dst_data + I420_Y_Size;
    jbyte *V_data_Dst = Dst_data + I420_Y_Size + I420_U_Size;
    jint Dst_Stride_Y = src_width;
    jint Dst_Stride_U = src_width >> 1;
    jint Dst_Stride_V = Dst_Stride_U;

    libyuv::NV12ToI420((const uint8_t *) Y_data_Src, src_stride_y,
                       (const uint8_t *) UV_data_Src, src_stride_uv,
                       (uint8_t *) Y_data_Dst, Dst_Stride_Y,
                       (uint8_t *) U_data_Dst, Dst_Stride_U,
                       (uint8_t *) V_data_Dst, Dst_Stride_V,
                       src_width, src_height);
}
// i420 --> nv12
void i420ToNv12(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv12_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv12_y_data = src_nv12_data;
    jbyte *src_nv12_uv_data = src_nv12_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV12(
            (const uint8_t *) src_i420_y_data, width,
            (const uint8_t *) src_i420_u_data, width >> 1,
            (const uint8_t *) src_i420_v_data, width >> 1,
            (uint8_t *) src_nv12_y_data, width,
            (uint8_t *) src_nv12_uv_data, width,
            width, height);
}

// --------------------

/**
 * Mirror(height only) first then do rotate
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
JNIEXPORT jbyteArray Convert_To_I420(JNIEnv *env, jobject thiz, jbyteArray yuvData, jint format, jint w, jint h, jboolean vertically_flip, jint degree) {
    int yuvLen = env->GetArrayLength(yuvData);
    uint8_t *yuvBuf = new uint8_t[yuvLen];
    env->GetByteArrayRegion(yuvData, 0, yuvLen, reinterpret_cast<jbyte *>(yuvBuf));

    int Ysize = w * h;
    size_t src_size = Ysize * 3 / 2;

    uint8_t *I420 = new uint8_t[yuvLen];

    uint8_t *pDstY = I420;
    uint8_t *pDstU = I420 + Ysize;
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
    int retVal = libyuv::ConvertToI420(yuvBuf, src_size,
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

    jbyteArray I420byte = env->NewByteArray(yuvLen);
    env->SetByteArrayRegion(I420byte, 0, yuvLen, reinterpret_cast<jbyte *>(I420));

    delete[] I420;
    delete[] yuvBuf;

    return I420byte;
}

JNIEXPORT jbyteArray MirrorI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height) {
    jbyte *src_i420_data = env->GetByteArrayElements(i420Src, nullptr);
    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    jbyte *dst_i420_data = (jbyte *) malloc(dst_i420_len);

    mirrorI420((uint8_t *) src_i420_data, width, height, (uint8_t *) dst_i420_data);
    env->ReleaseByteArrayElements(i420Src, src_i420_data, 0);

    jbyteArray mirror_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(mirror_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    free(dst_i420_data);
    return mirror_i420_array;
}

/**
 * @param degree    0: No rotation.
 *                 90: Rotate 90 degrees clockwise.
 *                180: Rotate 180 degrees.
 *                270: Rotate 270 degrees clockwise.
 */
JNIEXPORT jbyteArray RotateI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height, jint degree) {
    jbyte *src_i420_data = env->GetByteArrayElements(i420Src, nullptr);
    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    jbyte *dst_i420_data = (jbyte *) malloc(dst_i420_len);

    rotateI420((uint8_t *) src_i420_data, width, height, (uint8_t *) dst_i420_data, degree);
    env->ReleaseByteArrayElements(i420Src, src_i420_data, 0);

    jbyteArray rotate_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(rotate_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    free(dst_i420_data);
    return rotate_i420_array;
}

// =============================

static JNINativeMethod methods[] = {
        {"convertToI420", "([BIIIZI)[B", (void *) Convert_To_I420},
        {"mirrorI420",    "([BII)[B",    (void *) MirrorI420},
        {"rotateI420",    "([BIII)[B",   (void *) RotateI420},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(YUV_PACKAGE_BASE"YUVUtil");
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