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

#define H264_HEVC_PACKAGE_BASE "com/leovp/ffmpeg/"

struct H264HevcDecoderContext {
    AVCodecContext *ctx = nullptr;
    AVFrame *frame = nullptr;
    AVPacket *pkt = nullptr;

    struct SwsContext *convertCxt = nullptr;
    AVFrame *bmpFrame = nullptr;
    AVPixelFormat bmpFormat = AV_PIX_FMT_NONE;

    // Cached dimensions for SwsContext reuse
    int lastSwsWidth = 0;
    int lastSwsHeight = 0;
    AVPixelFormat lastSwsSrcFmt = AV_PIX_FMT_NONE;
};

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

static jfieldID getHandleField(JNIEnv *env, jobject obj) {
    jclass clazz = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);
    return fid;
}

static H264HevcDecoderContext *getDecoderCtx(JNIEnv *env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<H264HevcDecoderContext *>(handle);
}

/**
 * @param spsByteArray The data start with separator like 0x0, 0x0, 0x0, 0x01
 * @param ppsByteArray NOT Used. The data start with separator like 0x0, 0x0, 0x0, 0x01
 */
JNIEXPORT jobject JNICALL init(JNIEnv *env, jobject obj,
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

    delete[] vps_unit8_t_array;
    delete[] sps_unit8_t_array;
    delete[] pps_unit8_t_array;
    delete[] prefixSei_unit8_t_array;
    delete[] suffixSei_unit8_t_array;

    const AVCodec *codec = avcodec_find_decoder(codecId);
    if (!codec) {
        LOGE("Decoder not found for codec id=%d", codecId);
        delete[] csd_array;
        return nullptr;
    }

    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        LOGE("Could not allocate codec context");
        delete[] csd_array;
        return nullptr;
    }

    ctx->extradata = (uint8_t *) av_malloc(csdLen + AV_INPUT_BUFFER_PADDING_SIZE);
    ctx->extradata_size = csdLen;
    memcpy(ctx->extradata, csd_array, csdLen);
    memset(&ctx->extradata[ctx->extradata_size], 0, AV_INPUT_BUFFER_PADDING_SIZE);

    delete[] csd_array;

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("avcodec_open2 error. code=%d", ret);
        avcodec_free_context(&ctx);
        return nullptr;
    }

    char buf[1024];
    avcodec_string(buf, sizeof(buf), ctx, 0);
    LOGE("%s", buf);

    jclass returnBean = env->FindClass(H264_HEVC_PACKAGE_BASE"video/H264HevcDecoder$DecodeVideoInfo");
    jmethodID returnObjConstructor = env->GetMethodID(returnBean, "<init>", "(ILjava/lang/String;ILjava/lang/String;II)V");

    jstring codecName = env->NewStringUTF(avcodec_get_name(ctx->codec_id));
    jstring pixFmtName = env->NewStringUTF(av_get_pix_fmt_name(ctx->pix_fmt));
    jobject returnObj = env->NewObject(returnBean, returnObjConstructor,
                                       (int) ctx->codec_id, codecName,
                                       (int) ctx->pix_fmt, pixFmtName,
                                       ctx->width, ctx->height);
    env->DeleteLocalRef(codecName);
    env->DeleteLocalRef(pixFmtName);
    env->DeleteLocalRef(returnBean);

    // Create decoder context
    auto *decoderCtx = new H264HevcDecoderContext();
    decoderCtx->ctx = ctx;
    decoderCtx->frame = av_frame_alloc();
    decoderCtx->pkt = av_packet_alloc();

    switch(rgbType) {
        case 1:  decoderCtx->bmpFormat = AV_PIX_FMT_BGRA;  break;
        case 2:  decoderCtx->bmpFormat = AV_PIX_FMT_RGBA;  break;
        case 3:  decoderCtx->bmpFormat = AV_PIX_FMT_ARGB;  break;
        case 4:  decoderCtx->bmpFormat = AV_PIX_FMT_ABGR;  break;
        case 5:  decoderCtx->bmpFormat = AV_PIX_FMT_BGR24; break;
        case 6:  decoderCtx->bmpFormat = AV_PIX_FMT_RGB24; break;
        default: decoderCtx->bmpFormat = AV_PIX_FMT_NONE;  break;
    }
    if (AV_PIX_FMT_NONE != decoderCtx->bmpFormat) {
        decoderCtx->bmpFrame = av_frame_alloc();
    }

    env->SetLongField(obj, getHandleField(env, obj), reinterpret_cast<jlong>(decoderCtx));

    return returnObj;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    auto *decoderCtx = getDecoderCtx(env, obj);
    if (decoderCtx == nullptr) return;

    if (decoderCtx->ctx != nullptr) {
        avcodec_free_context(&decoderCtx->ctx);
    }
    if (decoderCtx->frame != nullptr) {
        av_frame_free(&decoderCtx->frame);
    }
    if (decoderCtx->pkt != nullptr) {
        av_packet_free(&decoderCtx->pkt);
    }
    if (decoderCtx->bmpFrame != nullptr) {
        av_frame_free(&decoderCtx->bmpFrame);
    }
    if (decoderCtx->convertCxt != nullptr) {
        sws_freeContext(decoderCtx->convertCxt);
    }

    delete decoderCtx;
    env->SetLongField(obj, getHandleField(env, obj), 0L);

    LOGE("H264 & HEVC decoder released!");
}

JNIEXPORT jobject JNICALL decode(JNIEnv *env, jobject obj, jbyteArray videoRawByteArray) {
    auto *decoderCtx = getDecoderCtx(env, obj);
    if (decoderCtx == nullptr) return nullptr;

    int videoRawLen = env->GetArrayLength(videoRawByteArray);
    auto *video_raw_unit8_t_array = new uint8_t[videoRawLen];
    env->GetByteArrayRegion(videoRawByteArray, 0, videoRawLen, reinterpret_cast<jbyte *>(video_raw_unit8_t_array));

    decoderCtx->pkt->data = video_raw_unit8_t_array;
    decoderCtx->pkt->size = videoRawLen;
    int ret;
    if ((ret = avcodec_send_packet(decoderCtx->ctx, decoderCtx->pkt)) < 0) {
        LOGE("avcodec_send_packet() error. code=%d", ret);
        delete[] video_raw_unit8_t_array;
        return nullptr;
    }
    if ((ret = avcodec_receive_frame(decoderCtx->ctx, decoderCtx->frame)) < 0) {
        LOGE("avcodec_receive_frame() error. code=%d", ret);
        delete[] video_raw_unit8_t_array;
        return nullptr;
    }

    AVFrame *frame = decoderCtx->frame;
    auto format = (AVPixelFormat) frame->format;
    if (AV_PIX_FMT_NONE != decoderCtx->bmpFormat) {
        format = decoderCtx->bmpFormat;
    }
    int image_buffer_size = av_image_get_buffer_size(format, frame->width, frame->height, 32);
    auto *image_byte_buffer = av_malloc(image_buffer_size);

    int written_image_bytes = 0;
    if (AV_PIX_FMT_NONE != decoderCtx->bmpFormat) {
        AVPixelFormat yuvFmt = convertDeprecatedFormat(decoderCtx->ctx->pix_fmt);

        // Reuse SwsContext if dimensions and format haven't changed
        if (decoderCtx->convertCxt == nullptr ||
            decoderCtx->lastSwsWidth != frame->width ||
            decoderCtx->lastSwsHeight != frame->height ||
            decoderCtx->lastSwsSrcFmt != yuvFmt) {

            if (decoderCtx->convertCxt != nullptr) {
                sws_freeContext(decoderCtx->convertCxt);
            }
            decoderCtx->convertCxt = sws_getContext(
                    frame->width, frame->height, yuvFmt,
                    frame->width, frame->height, decoderCtx->bmpFormat,
                    SWS_POINT, nullptr, nullptr, nullptr);
            decoderCtx->lastSwsWidth = frame->width;
            decoderCtx->lastSwsHeight = frame->height;
            decoderCtx->lastSwsSrcFmt = yuvFmt;
        }

        av_image_fill_arrays(decoderCtx->bmpFrame->data, decoderCtx->bmpFrame->linesize,
                             (uint8_t *)image_byte_buffer,
                             format, frame->width, frame->height, 1);
        sws_scale(decoderCtx->convertCxt, frame->data, frame->linesize, 0, frame->height,
                  decoderCtx->bmpFrame->data, decoderCtx->bmpFrame->linesize);
        written_image_bytes = image_buffer_size;
    } else {
        written_image_bytes = av_image_copy_to_buffer((uint8_t *) image_byte_buffer, image_buffer_size,
                                                      (const uint8_t *const *) frame->data, (const int *) frame->linesize,
                                                      (AVPixelFormat) frame->format, frame->width, frame->height, 1);
    }

    jbyteArray out_byte_array = env->NewByteArray(written_image_bytes);
    env->SetByteArrayRegion(out_byte_array, 0, written_image_bytes, reinterpret_cast<const jbyte *>(image_byte_buffer));
    av_free(image_byte_buffer);
    delete[] video_raw_unit8_t_array;

    jclass returnBean = env->FindClass(H264_HEVC_PACKAGE_BASE"video/H264HevcDecoder$DecodedVideoFrame");
    jmethodID returnObjConstructor = env->GetMethodID(returnBean, "<init>", "([BIII)V");
    jobject returnObj = env->NewObject(returnBean, returnObjConstructor, out_byte_array, frame->format, frame->width, frame->height);
    env->DeleteLocalRef(returnBean);
    env->DeleteLocalRef(out_byte_array);
    return returnObj;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return env->NewStringUTF("1.0.0");
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
