#include "YuvConvert.h"

void android420ToI420(const uint8_t *src_android420_data, jint src_pixel_stride_uv, jint width, jint height, uint8_t *dst_i420_data, jboolean vertically_flip, jint degree) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = src_i420_y_size >> 2;

    const uint8_t *src_i420_y_data = src_android420_data;
    const uint8_t *src_i420_u_data = src_android420_data + src_i420_y_size;
    const uint8_t *src_i420_v_data = src_android420_data + src_i420_y_size + src_i420_u_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    int verticalFlip = 1;
    if (JNI_TRUE == vertically_flip) verticalFlip = -1;
    int base_dst_stride_dimension = width;
    if (90 == degree || 270 == degree) base_dst_stride_dimension = height;
    libyuv::Android420ToI420Rotate(src_i420_y_data, width,
                                   src_i420_u_data, width >> 1,
                                   src_i420_v_data, width >> 1,
                                   src_pixel_stride_uv,
                                   dst_i420_y_data, base_dst_stride_dimension,
                                   dst_i420_u_data, base_dst_stride_dimension >> 1,
                                   dst_i420_v_data, base_dst_stride_dimension >> 1,
                                   width, verticalFlip * height,
                                   (libyuv::RotationMode) degree);
}

void convertToI420(const uint8_t *src_yuv_data, jint src_length, jint format, jint width, jint height, uint8_t *dst_i420_data, jboolean vertically_flip, jint degree) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = src_i420_y_size >> 2;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    uint32_t fourcc;
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
        default:
            fourcc = libyuv::FOURCC_I420;
    }

    int verticalFlip = 1;
    if (JNI_TRUE == vertically_flip) verticalFlip = -1;
    int base_dst_stride_dimension = width;
    if (90 == degree || 270 == degree) base_dst_stride_dimension = height;
    libyuv::ConvertToI420(src_yuv_data, src_length,
                          dst_i420_y_data, base_dst_stride_dimension,
                          dst_i420_u_data, base_dst_stride_dimension >> 1,
                          dst_i420_v_data, base_dst_stride_dimension >> 1,
                          0, 0,
                          width, verticalFlip * height,
                          width, height,
                          (libyuv::RotationMode) degree, fourcc);
}

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

void flipVerticallyI420(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_i420_data) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = src_i420_y_size >> 2;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_i420_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Copy(src_i420_y_data, width,
                     src_i420_u_data, width >> 1,
                     src_i420_v_data, width >> 1,
                     dst_i420_y_data, width,
                     dst_i420_u_data, width >> 1,
                     dst_i420_v_data, width >> 1,
                     width, -height);
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

void i420ToNv21(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    uint8_t *dst_nv21_y_data = dst_nv21_data;
    uint8_t *dst_nv21_uv_data = dst_nv21_data + src_y_size;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV21(src_i420_y_data, width,
                       src_i420_u_data, width >> 1,
                       src_i420_v_data, width >> 1,
                       dst_nv21_y_data, width,
                       dst_nv21_uv_data, width,
                       width, height);
}

void i420ToNv12(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_nv12_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    uint8_t *dst_nv12_y_data = dst_nv12_data;
    uint8_t *dst_nv12_uv_data = dst_nv12_data + src_y_size;

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    libyuv::I420ToNV12(src_i420_y_data, width,
                       src_i420_u_data, width >> 1,
                       src_i420_v_data, width >> 1,
                       dst_nv12_y_data, width,
                       dst_nv12_uv_data, width,
                       width, height);
}

void nv21ToI420(const uint8_t *src_nv21_data, jint width, jint height, uint8_t *dst_i420_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    const uint8_t *src_nv21_y_data = src_nv21_data;
    const uint8_t *src_nv21_vu_data = src_nv21_data + src_y_size;

    uint8_t *dst_i420_y_data = dst_i420_data;
    uint8_t *dst_i420_u_data = dst_i420_data + src_y_size;
    uint8_t *dst_i420_v_data = dst_i420_data + src_y_size + src_u_size;

    libyuv::NV21ToI420(src_nv21_y_data, width,
                       src_nv21_vu_data, width,
                       dst_i420_y_data, width,
                       dst_i420_u_data, width >> 1,
                       dst_i420_v_data, width >> 1,
                       width, height);
}

void nv12ToI420(const uint8_t *src_nv12_data, jint width, jint height, uint8_t *dst_i420_data, jint degree) {
    // NV12 video size
    jint nv12_y_size = width * height;

    // YUV420 video size
    jint i420_y_size = width * height;
    jint i420_u_size = (width >> 1) * (height >> 1);

    // src: buffer address of Y channel and UV channel
    const uint8_t *src_y_data = src_nv12_data;
    const uint8_t *src_uv_data = src_nv12_data + nv12_y_size;
    jint src_stride_y = width;
    jint src_stride_uv = width;

    //dst: buffer address of Y channel、U channel and V channel
    uint8_t *dst_y_data = dst_i420_data;
    uint8_t *dst_u_data = dst_i420_data + i420_y_size;
    uint8_t *dst_v_data = dst_i420_data + i420_y_size + i420_u_size;
    jint dst_y_stride = width;
    jint dst_u_stride = width >> 1;
    jint dst_v_stride = dst_u_stride;

    libyuv::NV12ToI420Rotate(src_y_data, src_stride_y,
                             src_uv_data, src_stride_uv,
                             dst_y_data, dst_y_stride,
                             dst_u_data, dst_u_stride,
                             dst_v_data, dst_v_stride,
                             width, height,
                             (libyuv::RotationMode) degree);
}

void mirrorNV12(const uint8_t *src_nv12_data, jint width, jint height, uint8_t *dst_nv12_data) {
    // NV12 video size
    jint src_nv12_y_size = width * height;

    const uint8_t *src_y_data = src_nv12_data;
    const uint8_t *src_uv_data = src_nv12_data + src_nv12_y_size;

    uint8_t *dst_nv12_y_data = dst_nv12_data;
    uint8_t *dst_nv12_uv_data = dst_nv12_data + src_nv12_y_size;

    libyuv::NV12Mirror(src_y_data, width,
                       src_uv_data, width,
                       dst_nv12_y_data, width,
                       dst_nv12_uv_data, width,
                       width, height);
}

void scaleNV12(const uint8_t *src_nv12_data, jint width, jint height,
               uint8_t *dst_nv12_data, jint dst_width, jint dst_height, jint mode) {
    // NV12 video size
    jint src_nv12_y_size = width * height;

    const uint8_t *src_y_data = src_nv12_data;
    const uint8_t *src_uv_data = src_nv12_data + src_nv12_y_size;

    jint dst_nv12_y_size = dst_width * dst_height;
    uint8_t *dst_nv12_y_data = dst_nv12_data;
    uint8_t *dst_nv12_uv_data = dst_nv12_data + dst_nv12_y_size;

    libyuv::NV12Scale(src_y_data, width,
                      src_uv_data, width,
                      width, height,
                      dst_nv12_y_data, dst_width,
                      dst_nv12_uv_data, dst_width,
                      dst_width, dst_height,
                      (libyuv::FilterMode) mode);
}

void nv21ToNV12(const uint8_t *src_nv21_data, jint width, jint height, uint8_t *dst_nv12_data) {
    jint src_y_size = width * height;

    const uint8_t *src_nv21_y_data = src_nv21_data;
    const uint8_t *src_nv21_vu_data = src_nv21_data + src_y_size;

    uint8_t *dst_nv12_y_data = dst_nv12_data;
    uint8_t *dst_nv12_uv_data = dst_nv12_data + src_y_size;

    libyuv::NV21ToNV12(src_nv21_y_data, width,
                       src_nv21_vu_data, width,
                       dst_nv12_y_data, width,
                       dst_nv12_uv_data, width,
                       width, height);
}

// --------------------

void i420ToRgb24(const uint8_t *src_i420_data, jint width, jint height, uint8_t *dst_rgb24_data, jint dst_rgb24_data_len) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    const uint8_t *src_i420_y_data = src_i420_data;
    const uint8_t *src_i420_u_data = src_i420_data + src_y_size;
    const uint8_t *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

//    printf("i420ToRgb24 width=%d height=%d dst_rgb24_data_len=%d", width, height, dst_rgb24_data_len);

    libyuv::I420ToRGB24(src_i420_y_data, width,
                        src_i420_u_data, width >> 1,
                        src_i420_v_data, width >> 1,
                        dst_rgb24_data, dst_rgb24_data_len,
                        width, height);
}