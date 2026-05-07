#include <jni.h>
#include <string>
#include "logger.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <libavutil/imgutils.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <libavcodec/jni.h>

//#define GET_ARRAY_LEN(array, len) {len = (sizeof(array) / sizeof(array[0]));}
#define H264_HEVC_PACKAGE_BASE "com/leovp/ffmpeg/"

AVCodecContext *ctx = nullptr;
AVFrame *frame = nullptr;
AVPacket *pkt = nullptr;

struct SwsContext *convertCxt = nullptr;
AVFrame *bmpFrame = nullptr;
AVPixelFormat bmpFormat = AV_PIX_FMT_NONE;

// 把已经废除的格式转换成新的格式，防止报 "deprecated pixel format used, make sure you did set range correctly" 错误
AVPixelFormat convertDeprecatedFormat(enum AVPixelFormat format)
{
    switch (format)
    {
        case AV_PIX_FMT_YUVJ420P:
            return AV_PIX_FMT_YUV420P;
        case AV_PIX_FMT_YUVJ422P:
            return AV_PIX_FMT_YUV422P;
        case AV_PIX_FMT_YUVJ444P:
            return AV_PIX_FMT_YUV444P;
        case AV_PIX_FMT_YUVJ440P:
            return AV_PIX_FMT_YUV440P;
        default:
            return format;
    }
}

/**
 * @param spsByteArray The data start with separator like 0x0, 0x0, 0x0, 0x01
 * @param ppsByteArray NOT Used. The data start with separator like 0x0, 0x0, 0x0, 0x01
 */
JNIEXPORT jobject JNICALL init(JNIEnv *env, __attribute__((unused)) jobject obj,
                               jbyteArray vpsByteArray,
                               jbyteArray spsByteArray, jbyteArray ppsByteArray,
                               jbyteArray prefixSeiByteArray, jbyteArray suffixSeiByteArray,
                               jint rgbType) {
    LOGE("H264 & HEVC decoder init.");

    AVCodecID codecId = AV_CODEC_ID_H264;

    int vpsLen = 0;
    uint8_t *vps_unit8_t_array = nullptr;

    int prefixSeiLen = 0;
    uint8_t *prefixSei_unit8_t_array = nullptr;

    int suffixSeiLen = 0;
    uint8_t *suffixSei_unit8_t_array = nullptr;

    if (nullptr != vpsByteArray) {
        codecId = AV_CODEC_ID_HEVC;

        vpsLen = env->GetArrayLength(vpsByteArray);
        vps_unit8_t_array = new uint8_t[vpsLen];
        env->GetByteArrayRegion(vpsByteArray, 0, vpsLen, reinterpret_cast<jbyte *>(vps_unit8_t_array));

        if (nullptr != prefixSeiByteArray) {
            prefixSeiLen = env->GetArrayLength(prefixSeiByteArray);
            prefixSei_unit8_t_array = new uint8_t[prefixSeiLen];
            env->GetByteArrayRegion(prefixSeiByteArray, 0, prefixSeiLen, reinterpret_cast<jbyte *>(prefixSei_unit8_t_array));
        }

        if (nullptr != suffixSeiByteArray) {
            suffixSeiLen = env->GetArrayLength(suffixSeiByteArray);
            suffixSei_unit8_t_array = new uint8_t[suffixSeiLen];
            env->GetByteArrayRegion(suffixSeiByteArray, 0, suffixSeiLen, reinterpret_cast<jbyte *>(suffixSei_unit8_t_array));
        }
    }

    int spsLen = env->GetArrayLength(spsByteArray);
    auto *sps_unit8_t_array = new uint8_t[spsLen];
    env->GetByteArrayRegion(spsByteArray, 0, spsLen, reinterpret_cast<jbyte *>(sps_unit8_t_array));

    int ppsLen = env->GetArrayLength(ppsByteArray);
    auto *pps_unit8_t_array = new uint8_t[ppsLen];
    env->GetByteArrayRegion(ppsByteArray, 0, ppsLen, reinterpret_cast<jbyte *>(pps_unit8_t_array));

    int csdLen = vpsLen + spsLen + ppsLen + prefixSeiLen + suffixSeiLen;
    auto *csd_array = new uint8_t[csdLen];
    if (nullptr != vps_unit8_t_array) {
        memcpy(csd_array, vps_unit8_t_array, vpsLen);
    }
    memcpy(csd_array + vpsLen, sps_unit8_t_array, spsLen);
    memcpy(csd_array + vpsLen + spsLen, pps_unit8_t_array, ppsLen);
    if (nullptr != prefixSei_unit8_t_array) {
        memcpy(csd_array + vpsLen + spsLen + ppsLen, prefixSei_unit8_t_array, prefixSeiLen);
    }
    if (nullptr != suffixSei_unit8_t_array) {
        memcpy(csd_array + vpsLen + spsLen + ppsLen + prefixSeiLen, suffixSei_unit8_t_array, suffixSeiLen);
    }

    if (nullptr != vps_unit8_t_array) free(vps_unit8_t_array);
    free(sps_unit8_t_array);
    free(pps_unit8_t_array);
    if (nullptr != prefixSei_unit8_t_array) free(prefixSei_unit8_t_array);
    if (nullptr != suffixSei_unit8_t_array) free(suffixSei_unit8_t_array);

//    LOGE("sizeof(sps)=%d sizeof(pps)=%d sizeof(csd)=%d", spsLen, ppsLen, csd0Len);
//
//    for(int idx = 0;idx<csdLen; idx++) {
//        LOGE("%d: %u ",idx, csd_array[idx]);
//    }
//
    const AVCodec *codec = avcodec_find_decoder(codecId);
    ctx = avcodec_alloc_context3(codec);
    ctx->extradata = (uint8_t *) av_malloc(csdLen + AV_INPUT_BUFFER_PADDING_SIZE);
    ctx->extradata_size = csdLen;
    memcpy(ctx->extradata, csd_array, csdLen);
    memset(&ctx->extradata[ctx->extradata_size], 0, AV_INPUT_BUFFER_PADDING_SIZE);

    free(csd_array);

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("avcodec_open2 error. code=%d\n", ret);
        return nullptr;
    }

    char buf[1024];
    avcodec_string(buf, sizeof(buf), ctx, 0);
    LOGE("%s", buf);

    // videoType=173[hevc][yuv420p] width=1920 height=800
//    LOGE("Video meta=%s | %s[%d], %s[%d] | width=%d height=%d", buf,
//         avcodec_get_name(ctx->codec_id), (int) ctx->codec_id,
//         av_get_pix_fmt_name(ctx->pix_fmt), (int) ctx->pix_fmt,
//         ctx->width, ctx->height);

    jclass returnBean = env->FindClass(H264_HEVC_PACKAGE_BASE"video/H264HevcDecoder$DecodeVideoInfo");
    // Get the method id of an constructor in clazz
    jmethodID returnObjConstructor = env->GetMethodID(returnBean, "<init>", "(ILjava/lang/String;ILjava/lang/String;II)V");
    // Create an instance of clazz
    jobject returnObj = env->NewObject(returnBean, returnObjConstructor,
                                       (int) ctx->codec_id, env->NewStringUTF(avcodec_get_name(ctx->codec_id)),
                                       (int) ctx->pix_fmt, env->NewStringUTF(av_get_pix_fmt_name(ctx->pix_fmt)),
                                       ctx->width, ctx->height);
    env->DeleteLocalRef(returnBean);

    frame = av_frame_alloc();
    pkt = av_packet_alloc();

    switch(rgbType) {
        case 1:
            bmpFormat = AV_PIX_FMT_BGRA;
            break;
        case 2:
            bmpFormat = AV_PIX_FMT_RGBA;
            break;
        case 3:
            bmpFormat = AV_PIX_FMT_ARGB;
            break;
        case 4:
            bmpFormat = AV_PIX_FMT_ABGR;
            break;
        case 5:
            bmpFormat = AV_PIX_FMT_BGR24;
            break;
        case 6:
            bmpFormat = AV_PIX_FMT_RGB24;
            break;
        default:
            bmpFormat = AV_PIX_FMT_NONE;
            break;
    }
    if (AV_PIX_FMT_NONE != bmpFormat) {
        bmpFrame = av_frame_alloc();
    }

    return returnObj;
}

JNIEXPORT void JNICALL release(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject obj) {
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    if (bmpFrame != nullptr) {
        av_frame_free(&bmpFrame);
        bmpFrame = nullptr;
    }
    if (convertCxt != nullptr) {
        sws_freeContext(convertCxt);
        convertCxt = nullptr;
    }

    LOGE("H264 & HEVC decoder released!");
}

JNIEXPORT jobject JNICALL decode(JNIEnv *env, __attribute__((unused)) jobject obj, jbyteArray videoRawByteArray) {
    int videoRawLen = env->GetArrayLength(videoRawByteArray);
    auto *video_raw_unit8_t_array = new uint8_t[videoRawLen];
    env->GetByteArrayRegion(videoRawByteArray, 0, videoRawLen, reinterpret_cast<jbyte *>(video_raw_unit8_t_array));

    pkt->data = video_raw_unit8_t_array;
    pkt->size = videoRawLen;
    int ret;
    if ((ret = avcodec_send_packet(ctx, pkt)) < 0) {
        LOGE("avcodec_send_packet() error. code=%d", ret);
        return nullptr;
    }
    if ((ret = avcodec_receive_frame(ctx, frame)) < 0) {
        LOGE("avcodec_receive_frame() error. code=%d", ret);
        return nullptr;
    }

    auto format = (AVPixelFormat) frame->format;
    if (AV_PIX_FMT_NONE != bmpFormat) {
        format = bmpFormat;
    }
//    LOGE("-----> frame_format: %s(%d)  ctx->pix_fmt: %s(%d)",
//        av_get_pix_fmt_name(format), format,
//        av_get_pix_fmt_name(ctx->pix_fmt), ctx->pix_fmt
//    );
    int image_buffer_size = av_image_get_buffer_size(format, frame->width, frame->height, 32);
    auto *image_byte_buffer = av_malloc(image_buffer_size);

    int written_image_bytes = 0;
    if (AV_PIX_FMT_NONE != bmpFormat) {
        AVPixelFormat yuvFmt = convertDeprecatedFormat(ctx->pix_fmt);
        // LOGE("YUV Format: %d  Bitmap format: %d", yuvFmt, bmpFormat);

        // struct SwsContext *sws_getContext(int srcW, int srcH, enum AVPixelFormat srcFormat,
        //                                   int dstW, int dstH, enum AVPixelFormat dstFormat,
        //                                   int flags, SwsFilter *srcFilter,
        //                                   SwsFilter *dstFilter, const double *param);
        convertCxt = sws_getContext(
                frame->width, frame->height, yuvFmt,
                frame->width, frame->height, bmpFormat,
                SWS_POINT, nullptr, nullptr, nullptr);
        // int av_image_fill_arrays(uint8_t *dst_data[4], int dst_linesize[4],
        //                          const uint8_t *src,
        //                          enum AVPixelFormat pix_fmt, int width, int height, int align);
        av_image_fill_arrays(bmpFrame->data, bmpFrame->linesize, (uint8_t *)image_byte_buffer,
                             format, frame->width, frame->height, 1);
        // int sws_scale(struct SwsContext *c, const uint8_t *const srcSlice[],
        //               const int srcStride[], int srcSliceY, int srcSliceH,
        //               uint8_t *const dst[], const int dstStride[]);
        sws_scale(convertCxt, frame->data, frame->linesize, 0, frame->height, bmpFrame->data, bmpFrame->linesize);
        written_image_bytes = image_buffer_size;
    } else {
        written_image_bytes = av_image_copy_to_buffer((uint8_t *) image_byte_buffer, image_buffer_size,
                                                      (const uint8_t *const *) frame->data, (const int *) frame->linesize,
                                                      (AVPixelFormat) frame->format, frame->width, frame->height, 1);
    }

//  LOGE("written_image_bytes=%d - %d  |  %dx%d", written_image_bytes, frame->format, frame->width, frame->height);

    jbyteArray out_byte_array = env->NewByteArray(written_image_bytes);
    env->SetByteArrayRegion(out_byte_array, 0, written_image_bytes, reinterpret_cast<const jbyte *>(image_byte_buffer));
    if (nullptr != image_byte_buffer) {
        av_free(image_byte_buffer);
    }

    free(video_raw_unit8_t_array);

//    return out_byte_array;

    jclass returnBean = env->FindClass(H264_HEVC_PACKAGE_BASE"video/H264HevcDecoder$DecodedVideoFrame");
    // Get the method id of an constructor in clazz
    jmethodID returnObjConstructor = env->GetMethodID(returnBean, "<init>", "([BIII)V");
    // Create an instance of clazz
    jobject returnObj = env->NewObject(returnBean, returnObjConstructor, out_byte_array, frame->format, frame->width, frame->height);
    env->DeleteLocalRef(returnBean);
    return returnObj;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

// =============================

static JNINativeMethod methods[] = {
        {(char*)"init",      (char*)"([B[B[B[B[BI)Lcom/leovp/ffmpeg/video/H264HevcDecoder$DecodeVideoInfo;",(void *) init},
        {(char*)"release",   (char*)"()V",                                                                  (void *) release},
        {(char*)"decode",    (char*)"([B)Lcom/leovp/ffmpeg/video/H264HevcDecoder$DecodedVideoFrame;",       (void *) decode},
        {(char*)"getVersion",(char*)"()Ljava/lang/String;",                                                 (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(H264_HEVC_PACKAGE_BASE"video/H264HevcDecoder");
    if (clz == nullptr) {
        LOGE("JNI_OnLoad FindClass error.");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        LOGE("JNI_OnLoad RegisterNatives error.");
        return JNI_ERR;
    }

    av_jni_set_java_vm(vm, reserved);

    return JNI_VERSION_1_6;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */
