#ifndef LEOANDROIDBASEUTIL_YUVCONVERT_H
#define LEOANDROIDBASEUTIL_YUVCONVERT_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

#include "libyuv.h"

#ifdef __cplusplus
}
#endif

int android420ToI420(const uint8_t *src_android420_data, jint src_pixel_stride_uv, jint width, jint height, uint8_t *dst_i420_data, jboolean vertically_flip, jint degree);

int convertToI420(const uint8_t *src_yuv_data, jint src_length, jint format, jint width, jint height, uint8_t *dst_i420_data, jboolean vertically_flip, jint degree);

int mirrorI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data);

int flipVerticallyI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data);

int rotateI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data, jint degree);

int scaleI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint mode);

int cropI420(const uint8_t *src_i420_data, jint src_length, jint width, jint height, uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint left, jint top);

int i420ToNv21(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_nv21_data);

int i420ToNv12(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_nv12_data);

int nv21ToI420(const uint8_t *src_nv21_data, jint width, jint height, uint8_t *dst_i420_data);

int nv12ToI420(const uint8_t *src_nv12_data, jint width, jint height, uint8_t *dst_i420_data, jint degree);

int mirrorNV12(const uint8_t *src_nv12_data, jint width, jint height, uint8_t *dst_nv12_data);

int scaleNV12(const uint8_t *src_nv12_data, jint width, jint height, uint8_t *dst_nv12_data, jint dst_width, jint dst_height, jint mode);

int nv21ToNV12(const uint8_t *src_nv21_data, jint width, jint height, uint8_t *dst_nv12_data);

// --------------------

int i420ToRgb24(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_rgb24_data);

#endif //LEOANDROIDBASEUTIL_YUVCONVERT_H
