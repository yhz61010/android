#include <jni.h>
#include <string>
#include <android/log.h>

extern "C"
{
#include "libavutil/imgutils.h"
#include "libavformat/avformat.h"
}

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "adpcm_jni", __VA_ARGS__))

//#define GET_ARRAY_LEN(array, len) {len = (sizeof(array) / sizeof(array[0]));}
#define ADPCM_PACKAGE "com/leovp/ffmpeg/adpcm/"

//#define AUDIO_INBUF_SIZE 20480
//#define AUDIO_REFILL_THRESH 4096

AVCodecContext *ctx = nullptr;
const AVCodec *codec = nullptr;
//AVCodecParserContext *parser = nullptr;

JNIEXPORT jint init(JNIEnv *env, jobject obj, jint sampleRate, jint channels) {
    LOGE("ADPCM init. sampleRate: %d, channels: %d\n", sampleRate, channels);

    codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_IMA_QT);
    ctx = avcodec_alloc_context3(codec);
    ctx->sample_rate = sampleRate;
    ctx->channels = channels;
    ctx->channel_layout = av_get_default_channel_layout(ctx->channels);

//    parser = av_parser_init(codec->id);

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("avcodec_open2 err\n");
        return ret;
    }

    return ret;
}

JNIEXPORT void release(JNIEnv *env, jobject obj) {
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
//    if (parser != nullptr) {
//        av_parser_close(parser);
//        parser = null;
//    }
}

JNIEXPORT jbyteArray decode(JNIEnv *env, jobject obj, jbyteArray adpcmBytes) {
    size_t dataLen = env->GetArrayLength(adpcmBytes);
    LOGE("dataLen=%d\n", dataLen);
    auto *adpcmData = (uint8_t *) env->GetByteArrayElements(adpcmBytes, nullptr);
    AVPacket *pkt = av_packet_alloc();
    size_t ret = av_new_packet(pkt, dataLen);
    if (ret < 0) {
        av_packet_free(&pkt);
        return nullptr;
    }
    pkt->data = adpcmData;
    pkt->pts = dataLen;

//    parser = av_parser_init(codec->id);
//    if (!parser) {
//        LOGE("Parser not found\n");
//        return nullptr;
//    }
//
//    size_t ret = av_parser_parse2(parser, ctx, &pkt->data, &pkt->size, adpcmData, dataLen, AV_NOPTS_VALUE, AV_NOPTS_VALUE, 0);
//    if (ret < 0) {
//        av_packet_free(&pkt);
//        return nullptr;
//    }
//    LOGE("av_parser_parse2 ret=%d\n", ret);

    ret = avcodec_send_packet(ctx, pkt);
    if (ret < 0) {
        av_packet_free(&pkt);
        return nullptr;
    }

    AVFrame *frame = av_frame_alloc();
    ret = avcodec_receive_frame(ctx, frame);
    if (ret < 0) {
        av_frame_free(&frame);
        return nullptr;
    }

    int data_size = av_get_bytes_per_sample(ctx->sample_fmt);
    if (data_size < 0) {
        /* This should not occur, checking just for paranoia */
        LOGE("Failed to calculate data size\n");
        return nullptr;
    }

//
//    int data_size = av_get_bytes_per_sample(ctx->sample_fmt);
//    LOGE("data_size=%d\n", data_size);

    LOGE("sample_rate=%d\n", frame->sample_rate);
    LOGE("channels=%d\n", frame->channels);
    LOGE("nb_samples=%d\n", frame->nb_samples);
    LOGE("format=%d\n", frame->format);
    int t_data_size = av_samples_get_buffer_size(reinterpret_cast<int *>(frame->linesize), frame->channels, frame->nb_samples, (AVSampleFormat) frame->format, 0);

    LOGE("11 t_data_size=%d\n", t_data_size);
    LOGE("11 linesize=%d\n", frame->linesize[0]);

    uint8_t pcm_len = frame->linesize[0];
    LOGE("pcm_len=%d\n", pcm_len);
    uint8_t *left_channel = frame->extended_data[0];
//    uint8_t *right_channel = frame->extended_data[1];

    jbyteArray byte_array = env->NewByteArray(pcm_len);
    env->SetByteArrayRegion(byte_array, 0, pcm_len, reinterpret_cast<const jbyte *>(left_channel));

    av_packet_free(&pkt);
    return static_cast<jbyteArray>(byte_array);
}

JNIEXPORT jstring getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.0.1");
}

// =============================

static JNINativeMethod methods[] = {
        {"init",       "(II)I",                (void *) init},
        {"release",    "()V",                  (void *) release},
        {"decode",     "([B)[B",               (void *) decode},
        {"getVersion", "()Ljava/lang/String;", (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(ADPCM_PACKAGE"AdpcmImaQtDecoder");
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