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
#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/"

AVCodecContext *ctx = nullptr;
AVFrame *frame = nullptr;
AVPacket *pkt = nullptr;

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels) {
    LOGE("ADPCM init. sampleRate: %d, channels: %d\n", sampleRate, channels);

    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_IMA_QT);
    ctx = avcodec_alloc_context3(codec);
    ctx->sample_rate = sampleRate;
    ctx->channels = channels;
    ctx->channel_layout = av_get_default_channel_layout(ctx->channels);

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("avcodec_open2 error. code=%d\n", ret);
        return ret;
    }

    frame = av_frame_alloc();
    pkt = av_packet_alloc();
    return ret;
}

JNIEXPORT jint JNICALL chunkSize(JNIEnv *env, jobject obj) {
    return 34 * ctx->channels;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
    LOGE("ADPCM released!");
}

JNIEXPORT jobject JNICALL decode(JNIEnv *env, jobject obj, jbyteArray adpcmByteArray) {
    size_t adpcmLen = env->GetArrayLength(adpcmByteArray);
    auto *temp = (jbyte *) env->GetByteArrayElements(adpcmByteArray, nullptr);
    auto *adpcm_unit8_t_array = new uint8_t[adpcmLen];
    memcpy(adpcm_unit8_t_array, temp, adpcmLen);
    env->ReleaseByteArrayElements(adpcmByteArray, temp, 0);
    pkt->data = adpcm_unit8_t_array;
    pkt->size = adpcmLen;

    size_t ret = avcodec_send_packet(ctx, pkt);
    if (ret < 0) {
        av_packet_free(&pkt);
        return nullptr;
    }

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

//    LOGE("sample_rate=%d\n", frame->sample_rate);
//    LOGE("channels=%d\n", frame->channels);
//    LOGE("nb_samples=%d\n", frame->nb_samples);
//    LOGE("format=%d\n", frame->format);
//    int t_data_size = av_samples_get_buffer_size(reinterpret_cast<int *>(frame->linesize), frame->channels, frame->nb_samples, (AVSampleFormat) frame->format, 0);
//
//    LOGE("t_data_size=%d\n", t_data_size);
//    LOGE("linesize=%d\n", frame->linesize[0]);

    uint8_t left_pcm_len = frame->linesize[0];
    uint8_t *left_channel_data = frame->extended_data[0];
    jbyteArray left_pcm_byte_array = env->NewByteArray(left_pcm_len);
    env->SetByteArrayRegion(left_pcm_byte_array, 0, left_pcm_len, reinterpret_cast<const jbyte *>(left_channel_data));

    uint8_t right_pcm_len = frame->linesize[1];
    uint8_t *right_channel_data = frame->extended_data[1];
    jbyteArray right_pcm_byte_array = env->NewByteArray(right_pcm_len);
    env->SetByteArrayRegion(right_pcm_byte_array, 0, right_pcm_len, reinterpret_cast<const jbyte *>(right_channel_data));

    // Get the class we wish to return an instance of
    jclass resultClass = env->FindClass(ADPCM_PACKAGE_BASE"base/DecodedAudioResult");
    // Get the method id of an empty constructor in clazz
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "([B[B)V");
    // Create an instance of clazz
    jobject resultObj = env->NewObject(resultClass, constructor, left_pcm_byte_array, right_pcm_byte_array);

    // Get Field references
//    jfieldID leftChannelField = env->GetFieldID(resultClass, "leftChannelData", "[B");
//    jfieldID rightChannelField = env->GetFieldID(resultClass, "rightChannelData", "[B");

    // Set fields for object
//    env->SetObjectField(resultObj, leftChannelField, left_pcm_byte_array);
//    env->SetObjectField(resultObj, rightChannelField, right_pcm_byte_array);

    return resultObj;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

// =============================

static JNINativeMethod methods[] = {
        {"init",       "(II)I",                                               (void *) init},
        {"release",    "()V",                                                 (void *) release},
        {"chunkSize",  "()I",                                                 (void *) chunkSize},
        {"decode",     "([B)L" ADPCM_PACKAGE_BASE "base/DecodedAudioResult;", (void *) decode},
        {"getVersion", "()Ljava/lang/String;",                                (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(ADPCM_PACKAGE_BASE"audio/adpcm/AdpcmImaQtDecoder");
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