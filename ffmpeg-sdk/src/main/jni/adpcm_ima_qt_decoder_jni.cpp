#include <jni.h>
#include <string>
#include <android/log.h>

extern "C"
{
#include <libavcodec/avcodec.h>
}

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "adpcm_decoder_jni", __VA_ARGS__))

//#define GET_ARRAY_LEN(array, len) {len = (sizeof(array) / sizeof(array[0]));}
#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/"

AVCodecContext *ctx = nullptr;
AVFrame *frame = nullptr;
AVPacket *pkt = nullptr;

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels) {
    LOGE("ADPCM decoder init. sampleRate: %d, channels: %d\n", sampleRate, channels);

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
    LOGE("ADPCM decoder released!");
}

JNIEXPORT jbyteArray JNICALL decode(JNIEnv *env, jobject obj, jbyteArray adpcmByteArray) {
    int adpcmLen = env->GetArrayLength(adpcmByteArray);
    if (adpcmLen != ctx->channels * 34) {
        LOGE("ADPCM bytes must be %d", ctx->channels * 34);
        return nullptr;
    }
    auto *adpcm_unit8_t_array = new uint8_t[adpcmLen];
    env->GetByteArrayRegion(adpcmByteArray, 0, adpcmLen, reinterpret_cast<jbyte *>(adpcm_unit8_t_array));
    // or you can do it like this:
//    auto *temp = (jbyte *) env->GetByteArrayElements(adpcmByteArray, nullptr);
//    auto *adpcm_unit8_t_array = new uint8_t[adpcmLen];
//    memcpy(adpcm_unit8_t_array, temp, adpcmLen);
//    env->ReleaseByteArrayElements(adpcmByteArray, temp, 0);

    pkt->data = adpcm_unit8_t_array;
    pkt->size = adpcmLen;
    int ret;
    if ((ret = avcodec_send_packet(ctx, pkt)) < 0) {
        LOGE("avcodec_send_packet() error. code=%d", ret);
        return nullptr;
    }
    if ((ret = avcodec_receive_frame(ctx, frame)) < 0) {
        LOGE("avcodec_receive_frame() error. code=%d", ret);
        return nullptr;
    }

    int each_channel_length = frame->linesize[0];
    uint8_t *left_channel_data = frame->data[0];

    int pcmSize = each_channel_length * ctx->channels;

    if (ctx->channels > 1) { // For stereo
        auto *pcm_all_channel_data = new uint8_t[pcmSize];
        uint8_t *right_channel_data = frame->data[1];
        int subI = 0;
        for (int k = 0; k < pcmSize; k += 4) {
            pcm_all_channel_data[k] = left_channel_data[subI];            // Left channel lower 8 bits
            pcm_all_channel_data[k + 1] = left_channel_data[subI + 1];    // Left channel higher 8 bits
            pcm_all_channel_data[k + 2] = right_channel_data[subI];       // Right channel lower 8 bits
            pcm_all_channel_data[k + 3] = right_channel_data[subI + 1];   // Right channel higher 8 bits
            subI += 2;
        }

        jbyteArray pcm_byte_array = env->NewByteArray(pcmSize);
        env->SetByteArrayRegion(pcm_byte_array, 0, pcmSize, reinterpret_cast<const jbyte *>(pcm_all_channel_data));
        return pcm_byte_array;
    } else { // For mono
        jbyteArray pcm_byte_array = env->NewByteArray(pcmSize);
        env->SetByteArrayRegion(pcm_byte_array, 0, pcmSize, reinterpret_cast<const jbyte *>(left_channel_data));
        return pcm_byte_array;
    }
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

// =============================

static JNINativeMethod methods[] = {
        {"init",       "(II)I",                (void *) init},
        {"release",    "()V",                  (void *) release},
        {"chunkSize",  "()I",                  (void *) chunkSize},
        {"decode",     "([B)[B",               (void *) decode},
        {"getVersion", "()Ljava/lang/String;", (void *) getVersion},
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