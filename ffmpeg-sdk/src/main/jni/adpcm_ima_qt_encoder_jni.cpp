#include <jni.h>
#include <string>
#include <android/log.h>

extern "C"
{
#include <libavcodec/avcodec.h>

#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include <libavutil/frame.h>
#include <libavutil/samplefmt.h>
}

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "adpcm_encoder_jni", __VA_ARGS__))

//#define GET_ARRAY_LEN(array, len) {len = (sizeof(array) / sizeof(array[0]));}
#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/"

AVCodecContext *ctx = nullptr;
AVFrame *frame = nullptr;
AVPacket *pkt = nullptr;

/* check that a given sample format is supported by the encoder */
static int check_sample_fmt(const AVCodec *codec, enum AVSampleFormat sample_fmt) {
    const enum AVSampleFormat *p = codec->sample_fmts;

    while (*p != AV_SAMPLE_FMT_NONE) {
        if (*p == sample_fmt)
            return 1;
        p++;
    }
    return 0;
}

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels, jint bitRate) {
    LOGE("ADPCM encoder init. sampleRate: %d, channels: %d bitRate: %d\n", sampleRate, channels, bitRate);

    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_ADPCM_IMA_QT);
    ctx = avcodec_alloc_context3(codec);
    ctx->bit_rate = bitRate; // 64000
    ctx->sample_fmt = AV_SAMPLE_FMT_S16P;
    if (!check_sample_fmt(codec, ctx->sample_fmt)) {
        LOGE("Encoder does not support sample format %s", av_get_sample_fmt_name(ctx->sample_fmt));
        return -1;
    }
    ctx->codec_type = AVMEDIA_TYPE_AUDIO;
    ctx->sample_rate = sampleRate;
    ctx->channel_layout = channels == 1 ? AV_CH_LAYOUT_MONO : AV_CH_LAYOUT_STEREO;
    ctx->channels = av_get_channel_layout_nb_channels(ctx->channel_layout);

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("avcodec_open2 error. code=%d\n", ret);
        av_free(ctx);
        return ret;
    }

    frame = av_frame_alloc();
    pkt = av_packet_alloc();

    return ret;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
    LOGE("ADPCM encoder released!");
}

JNIEXPORT jbyteArray JNICALL encoder(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    int pcmLen = env->GetArrayLength(pcmByteArray);
    auto *pcm_unit8_t_array = new uint8_t[pcmLen];
    env->GetByteArrayRegion(pcmByteArray, 0, pcmLen, reinterpret_cast<jbyte *>(pcm_unit8_t_array));
// or you can do it like this:
//    auto *temp = (jbyte *) env->GetByteArrayElements(pcmByteArray, nullptr);
//    auto *adpcm_unit8_t_array = new uint8_t[pcmLen];
//    memcpy(pcm_unit8_t_array, temp, pcmLen);
//    env->ReleaseByteArrayElements(pcmByteArray, temp, 0);

    frame->nb_samples = ctx->frame_size;
    frame->format = ctx->sample_fmt;
    frame->channel_layout = ctx->channel_layout;

//    int bufferSize = av_samples_get_buffer_size(nullptr, ctx->channels, ctx->frame_size, ctx->sample_fmt, 0);
//    if (bufferSize < 0) {
//        LOGE("get av_samples_get_buffer_size() failed!");
//        av_frame_free(&frame);
//        frame = nullptr;
//        avcodec_close(ctx);
//        av_free(ctx);
//        ctx = nullptr;
//        return nullptr;
//    }
//    auto *buffer = (uint8_t *) av_malloc(bufferSize);
//    avcodec_fill_audio_frame(frame, ctx->channels, ctx->sample_fmt, buffer, bufferSize, 0);

    /* allocate the data buffers */
    int ret = av_frame_get_buffer(frame, 0);
    if (ret < 0) {
        LOGE("Could not allocate audio data buffers. code=%d", ret);
        return nullptr;
    }

    /* make sure the frame is writable -- makes a copy if the encoder
     * kept a reference internally */
    ret = av_frame_make_writable(frame);
    if (ret < 0) {
        LOGE("av_frame_make_writable is not writable. code=%d", ret);
        return nullptr;
    }
    frame->data[0] = pcm_unit8_t_array;

    /* send the frame for encoding */
    if ((ret = avcodec_send_frame(ctx, frame)) < 0) {
        LOGE("Error sending the frame to the encoder. code=%d", ret);
        return nullptr;
    }
    /* read all the available output packets (in general there may be any
     * number of them */
    while (ret >= 0) {
        if ((ret = avcodec_receive_packet(ctx, pkt)) < 0) {
            LOGE("Error encoding audio frame. code=%d", ret);
            return nullptr;
        }
//        pkt->stream_index = audioStream->index;
//        av_interleaved_write_frame(nullptr, pkt);
        av_packet_unref(pkt);
        LOGE("avcodec_receive_packet ----------------");
    }

    uint8_t *adpcmData = pkt->data;
    int adpcmLen = pkt->size;
    jbyteArray adpcm_byte_array = env->NewByteArray(adpcmLen);
    env->SetByteArrayRegion(adpcm_byte_array, 0, adpcmLen, reinterpret_cast <const jbyte *>(adpcmData));

    return adpcm_byte_array;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

// =============================

static JNINativeMethod methods[] = {
        {"init",       "(III)I",               (void *) init},
        {"release",    "()V",                  (void *) release},
        {"encode",     "([B)[B",               (void *) encoder},
        {"getVersion", "()Ljava/lang/String;", (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(ADPCM_PACKAGE_BASE"audio/adpcm/AdpcmImaQtEncoder");
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