#include "YuvConvert.h"

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

void scaleI420(const uint8_t *src_i420_data, jint width, jint height,
               uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint mode) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);
    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_i420_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jint dst_i420_y_size = dst_width * dst_height;
    jint dst_i420_u_size = (dst_width >> 1) * (dst_height >> 1);
    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + dst_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + dst_i420_y_size + dst_i420_u_size;

    libyuv::I420Scale(src_i420_y_data, width,
                      src_i420_u_data, width >> 1,
                      src_i420_v_data, width >> 1,
                      width, height,
                      dst_i420_y_data, dst_width,
                      dst_i420_u_data, dst_width >> 1,
                      dst_i420_v_data, dst_width >> 1,
                      dst_width, dst_height,
                      (libyuv::FilterMode) mode);
}

void cropI420(const uint8_t *src_i420_data, jint src_length, jint width, jint height,
              uint8_t *dst_i420_data, jint dst_width, jint dst_height, jint left, jint top) {
    jint dst_i420_y_size = dst_width * dst_height;
    jint dst_i420_u_size = (dst_width >> 1) * (dst_height >> 1);

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + dst_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + dst_i420_y_size + dst_i420_u_size;

    libyuv::ConvertToI420(src_i420_data, src_length,
                          dst_i420_y_data, dst_width,
                          dst_i420_u_data, dst_width >> 1,
                          dst_i420_v_data, dst_width >> 1,
                          left, top,
                          width, height,
                          dst_width, dst_height,
                          libyuv::kRotate0, libyuv::FOURCC_I420);
}

void i420ToNv21(const uint8_t *src_i420_data, jint width, jint height, uint8_t *src_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    uint8_t *src_nv21_y_data = src_nv21_data;
    uint8_t *src_nv21_uv_data = src_nv21_data + src_y_size;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV21(src_i420_y_data, width,
                       src_i420_u_data, width >> 1,
                       src_i420_v_data, width >> 1,
                       src_nv21_y_data, width,
                       src_nv21_uv_data, width,
                       width, height);
}

void i420ToNv12(const uint8_t *src_i420_data, jint width, jint height, uint8_t *src_nv12_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    uint8_t *src_nv12_y_data = src_nv12_data;
    uint8_t *src_nv12_uv_data = src_nv12_data + src_y_size;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV12(src_i420_y_data, width,
                       src_i420_u_data, width >> 1,
                       src_i420_v_data, width >> 1,
                       src_nv12_y_data, width,
                       src_nv12_uv_data, width,
                       width, height);
}

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