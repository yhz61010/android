#include <jni.h>
#include <string>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <libavcodec/avcodec.h>

#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include <libavutil/frame.h>
#include <libavutil/samplefmt.h>
#ifdef __cplusplus
}
#endif /* __cplusplus */

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "adpcm_encoder_jni", __VA_ARGS__))

//#define GET_ARRAY_LEN(array, len) {len = (sizeof(array) / sizeof(array[0]));}
#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/"

AVCodecContext *c = nullptr;
AVFrame *frame = nullptr;
AVPacket *pkt = nullptr;

void encodedAudioCallback(JNIEnv *env, jobject obj, jbyteArray encodedAudioData) {
    // Get the class of the current calling object
    jclass clazz = env->GetObjectClass(obj);
    // Get the method id of an empty constructor in clazz
    jmethodID constructor = env->GetMethodID(clazz, "encodedAudioCallback", "([B)V");
    // Calls my.package.name.JNIReturnExample#javaCallback(float, float);
    env->CallVoidMethod(obj, constructor, encodedAudioData);
}

static void encode_n(JNIEnv *env, jobject obj, AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt) {
    int ret;

    /* send the frame for encoding */
    ret = avcodec_send_frame(pCtx, pFrame);
    if (ret < 0) {
        LOGE("Error sending the frame to the encoder\n");
        exit(1);
    }

    /* read all the available output packets (in general there may be any
     * number of them */
    while (ret >= 0) {
        ret = avcodec_receive_packet(pCtx, pPkt);
//        LOGE("avcodec_receive_packet ret=%d", ret);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return;
        else if (ret < 0) {
            LOGE("Error encoding audio frame\n");
            exit(1);
        }

        jbyteArray encoded_byte_array = env->NewByteArray(pPkt->size);
        env->SetByteArrayRegion(encoded_byte_array, 0, pPkt->size, reinterpret_cast<const jbyte *>(pPkt->data));
        encodedAudioCallback(env, obj, encoded_byte_array);

//        LOGE("avcodec_receive_packet pkt->size=%d", pkt->size);
        av_packet_unref(pPkt);
    }
}

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels, jint bitRate) {
    LOGE("ADPCM encoder init. sampleRate: %d, channels: %d bitRate: %d", sampleRate, channels, bitRate);

    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_ADPCM_IMA_QT);
    if (!codec) {
        LOGE("ADPCM IMA QT codec not found\n");
        return -1;
    }
    c = avcodec_alloc_context3(codec);
    if (!c) {
        LOGE("Could not allocate audio codec context\n");
        return -2;
    }

    c->sample_rate = sampleRate;
    c->bit_rate = bitRate;
    c->sample_fmt = AV_SAMPLE_FMT_S16P; // ADPCM-IMA-QT only support AV_SAMPLE_FMT_S16P
    c->channel_layout = channels == 2 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO;
    c->channels = av_get_channel_layout_nb_channels(c->channel_layout);

    int ret;
    /* open it */
    if ((ret = avcodec_open2(c, codec, nullptr)) < 0) {
        LOGE("Could not open codec");
        return ret;
    }
    /* packet for holding encoded output */
    pkt = av_packet_alloc();
    if (!pkt) {
        LOGE("Could not allocate the packet");
        return -3;
    }
    /* frame containing input raw audio */
    frame = av_frame_alloc();
    if (!frame) {
        LOGE("Could not allocate audio frame");
        return -4;
    }

    frame->nb_samples = c->frame_size;
    frame->format = c->sample_fmt;
    frame->channel_layout = c->channel_layout;

    /* allocate the data buffers */
    ret = av_frame_get_buffer(frame, 0);
    if (ret < 0) {
        LOGE("Could not allocate audio data buffers");
        return -5;
    }
    ret = av_frame_make_writable(frame);

    return ret;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    if (c != nullptr) {
        avcodec_free_context(&c);
        c = nullptr;
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    LOGE("ADPCM encoder released!");
}

JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    int pcmLen = env->GetArrayLength(pcmByteArray);
    auto *pcm_unit8_t_array = new uint8_t[pcmLen];
    env->GetByteArrayRegion(pcmByteArray, 0, pcmLen, reinterpret_cast<jbyte *>(pcm_unit8_t_array));

    LOGE("channels=%d c->frame_size=%d frame->linesize[0]=%d frame->nb_samples=%d", c->channels, c->frame_size, frame->linesize[0], frame->nb_samples);

    bool isStereo = c->channels == 2;
    uint8_t *outs[c->channels];
    const int BUF_SIZE = frame->linesize[0] * c->channels;
    outs[0] = new uint8_t[BUF_SIZE];
    if (isStereo)
        outs[1] = new uint8_t[BUF_SIZE];

    const int loopStep = 2 * c->channels;
    for (int loop = 0; loop < pcmLen / BUF_SIZE; loop++) {
        for (int idx = 0; idx < BUF_SIZE / loopStep; idx++) {
            outs[0][idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 0];
            outs[0][idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 1];

            if (isStereo) {
                outs[1][idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 2];
                outs[1][idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 3];
            }
        }

        frame->data[0] = outs[0];
        if (isStereo)
            frame->data[1] = outs[1];

//        LOGE("in loop frame->linesize[0]=%d", frame->linesize[0]);

        encode_n(env, obj, c, frame, pkt);
    }

    delete outs[0];
    if (isStereo)
        delete outs[1];

    /* flush the encoder */
    encode_n(env, obj, c, nullptr, pkt);

    av_frame_free(&frame);
    av_packet_free(&pkt);
    avcodec_free_context(&c);
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

// =============================

static JNINativeMethod methods[] = {
        {"init",                "(III)I",                (void *) init},
        {"release",             "()V",                   (void *) release},
        {"encode",              "([B)V",                 (void *) encode},
        {"getVersion",          "()Ljava/lang/String;",  (void *) getVersion},
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