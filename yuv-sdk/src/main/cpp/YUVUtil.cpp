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

void mirrorI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = src_i420_y_size >> 2;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror((const uint8_t *) src_i420_y_data, width,
                       (const uint8_t *) src_i420_u_data, width >> 1,
                       (const uint8_t *) src_i420_v_data, width >> 1,
                       (uint8_t *) dst_i420_y_data, width,
                       (uint8_t *) dst_i420_u_data, width >> 1,
                       (uint8_t *) dst_i420_v_data, width >> 1,
                       width, height);
}

void rotateI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data, jint degree) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    // 要注意这里的 width 和 height 在旋转之后是相反的
    if (degree == libyuv::kRotate90 || degree == libyuv::kRotate270) {
        libyuv::I420Rotate((const uint8_t *) src_i420_y_data, width,
                           (const uint8_t *) src_i420_u_data, width >> 1,
                           (const uint8_t *) src_i420_v_data, width >> 1,
                           (uint8_t *) dst_i420_y_data, height,
                           (uint8_t *) dst_i420_u_data, height >> 1,
                           (uint8_t *) dst_i420_v_data, height >> 1,
                           width, height,
                           (libyuv::RotationMode) degree);
    } else {
        libyuv::I420Rotate((const uint8_t *) src_i420_y_data, width,
                           (const uint8_t *) src_i420_u_data, width >> 1,
                           (const uint8_t *) src_i420_v_data, width >> 1,
                           (uint8_t *) dst_i420_y_data, width,
                           (uint8_t *) dst_i420_u_data, width >> 1,
                           (uint8_t *) dst_i420_v_data, width >> 1,
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

JNIEXPORT jbyteArray Convert_To_I420(JNIEnv *env, jobject thiz, jbyteArray nv21, jint w, jint h, int type) {
    int nv21Len = env->GetArrayLength(nv21);
    uint8_t *nv21Buf = new uint8_t[nv21Len];
    env->GetByteArrayRegion(nv21, 0, nv21Len, reinterpret_cast<jbyte *>(nv21Buf));

    int Ysize = w * h;
    size_t src_size = Ysize * 3 / 2;

    uint8_t *I420 = new uint8_t[nv21Len];

    uint8_t *pDstY = I420;
    uint8_t *pDstU = I420 + Ysize;
    uint8_t *pDstV = pDstU + (Ysize / 4);

    int retVal = 0;

    if (type == 1) {
        libyuv::RotationMode mode = libyuv::kRotate0;

        retVal = libyuv::ConvertToI420(nv21Buf, src_size, pDstY, w, pDstU,
                                       w / 2, pDstV, w / 2, 0, 0, w, h, w, h, mode,
                                       libyuv::FOURCC_NV21);
    } else if (type == 2) {
        libyuv::RotationMode mode = libyuv::kRotate0;

        retVal = libyuv::ConvertToI420(nv21Buf, src_size, pDstY, w, pDstU,
                                       w / 2, pDstV, w / 2, 0, 0, w, -h, w, h, mode,
                                       libyuv::FOURCC_NV21);
    } else if (type == 3) {
        libyuv::RotationMode mode = libyuv::kRotate90;

        retVal = libyuv::ConvertToI420(nv21Buf, src_size, pDstY, h, pDstU,
                                       h / 2, pDstV, h / 2, 0, 0, w, h, w, h, mode,
                                       libyuv::FOURCC_NV21);
    } else if (type == 4) {
        libyuv::RotationMode mode = libyuv::kRotate90;

        retVal = libyuv::ConvertToI420(nv21Buf, src_size, pDstY, h, pDstU,
                                       h / 2, pDstV, h / 2, 0, 0, w, -h, w, h, mode,
                                       libyuv::FOURCC_NV21);
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

    printf("type = %d and post convertI420 retVal = %d", type, retVal);

    jbyteArray I420byte = env->NewByteArray(nv21Len);
    env->SetByteArrayRegion(I420byte, 0, nv21Len, reinterpret_cast<jbyte *>(I420));

    if (I420) {
        delete[] I420;
    }

    if (nv21Buf) {
        delete[] nv21Buf;
    }

    return I420byte;
}

JNIEXPORT jbyteArray Convert_To_I420_Negative_Stride(JNIEnv *env, jobject thiz, jbyteArray nv21, jint w, jint h, int type) {
    int nv21Len = env->GetArrayLength(nv21);
    unsigned char *nv21Buf = new unsigned char[nv21Len];
    env->GetByteArrayRegion(nv21, 0, nv21Len,
                            reinterpret_cast<jbyte *>(nv21Buf));

    int Ysize = w * h;
    size_t src_size = Ysize * 1.5;

    unsigned char *I420 = new unsigned char[nv21Len];

    unsigned char *pDstY = I420 + (h - 1) * w;
    unsigned char *pDstU = I420 + Ysize + (h / 2 - 1) * (w / 2);
    unsigned char *pDstV = pDstU + (Ysize / 4);

    libyuv::RotationMode mode = libyuv::kRotate270;

    int retVal = libyuv::ConvertToI420(nv21Buf, src_size, pDstY, -h, pDstU, -h / 2, pDstV,
                                       -h / 2, 0, 0, w, -h, w, h, mode, libyuv::FOURCC_NV21);
    /*libyuv::ConvertToI420(const uint8* src_frame, size_t src_size,
     uint8* dst_y, int dst_stride_y,
     uint8* dst_u, int dst_stride_u,
     uint8* dst_v, int dst_stride_v,
     int crop_x, int crop_y,
     int src_width, int src_height,
     int crop_width, int crop_height,
     enum RotationMode rotation,
     uint32 format);*/

    printf("type = %d and post convertI420 negative stride retVal = %d", type, retVal);

    jbyteArray I420byte = env->NewByteArray(nv21Len);
    env->SetByteArrayRegion(I420byte, 0, nv21Len,
                            reinterpret_cast<jbyte *>(I420));

    if (I420) {
        delete[] I420;
    }

    if (nv21Buf) {
        delete[] nv21Buf;
    }

    return I420byte;
}

JNIEXPORT jbyteArray MirrorI420(JNIEnv *env, jobject thiz, jbyteArray i420Src, jint width, jint height) {
    jbyte *src_i420_data = env->GetByteArrayElements(i420Src, nullptr);
    int dst_i420_len = sizeof(jbyte) * width * height * 3 / 2;
    jbyte *dst_i420_data = (jbyte *) malloc(dst_i420_len);

    mirrorI420(src_i420_data, width, height, dst_i420_data);
    env->ReleaseByteArrayElements(i420Src, src_i420_data, 0);

    jbyteArray mirror_i420_array = env->NewByteArray(dst_i420_len);
    env->SetByteArrayRegion(mirror_i420_array, 0, dst_i420_len, reinterpret_cast<const jbyte *>(dst_i420_data));
    free(dst_i420_data);
    return mirror_i420_array;
}

JNIEXPORT jbyteArray RotateI420(JNIEnv *env, jobject thiz, jbyteArray i420Bytes, int width, int height, int degree) {
    int i420Len = env->GetArrayLength(i420Bytes);
    uint8_t *i420ByteArray = new uint8_t[i420Len];
    env->GetByteArrayRegion(i420Bytes, 0, i420Len, reinterpret_cast<jbyte *>(i420ByteArray));

    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    uint8_t *src_i420_y_data = i420ByteArray;
    uint8_t *src_i420_u_data = i420ByteArray + src_i420_y_size;
    uint8_t *src_i420_v_data = i420ByteArray + src_i420_y_size + src_i420_u_size;

    int dst_yuv_len = width * height * 3 / 2;
    uint8_t *dst_i420 = new uint8_t[dst_yuv_len];
    uint8_t *dst_i420_y_data = dst_i420;
    uint8_t *dst_i420_u_data = dst_i420 + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420 + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror((const uint8_t *) src_i420_y_data, width,
                       (const uint8_t *) src_i420_u_data, width >> 1,
                       (const uint8_t *) src_i420_v_data, width >> 1,
                       (uint8_t *) dst_i420_y_data, width,
                       (uint8_t *) dst_i420_u_data, width >> 1,
                       (uint8_t *) dst_i420_v_data, width >> 1,
                       width, height);

    delete[] i420ByteArray;

    jbyteArray mirror_i420_array = env->NewByteArray(dst_yuv_len);
    env->SetByteArrayRegion(mirror_i420_array, 0, dst_yuv_len, reinterpret_cast<const jbyte *>(dst_i420));
    delete dst_i420;
    return mirror_i420_array;
}

// =============================

static JNINativeMethod methods[] = {
        {"convertToI420",               "([BIII)[B", (void *) Convert_To_I420},
        {"convertToI420NegativeStride", "([BIII)[B", (void *) Convert_To_I420_Negative_Stride},
        {"mirrorI420",                  "([BII)[B",  (void *) MirrorI420},
        {"rotateI420",                  "([BIII)[B", (void *) RotateI420},
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