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

void mirrorI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data);

void rotateI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data, jint degree);

void scaleI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint mode);

void cropI420(const uint8_t *src_i420_data, jint src_length, jint width, jint height, uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint left, jint top);

void i420ToNv21(const uint8_t *src_i420_data, jint width, jint height, uint8_t *src_nv21_data);

void i420ToNv12(const uint8_t *src_i420_data, jint width, jint height, uint8_t *src_nv12_data);

void nv21ToI420(jbyte *src_nv21_data, jint width, jint height, jbyte *src_i420_data);

void nv12ToI420(jbyte *Src_data, jint src_width, jint src_height, jbyte *Dst_data);

#endif //LEOANDROIDBASEUTIL_YUVCONVERT_H
