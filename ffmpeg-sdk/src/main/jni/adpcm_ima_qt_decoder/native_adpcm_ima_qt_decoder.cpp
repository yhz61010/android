#include "native_adpcm_ima_qt_decoder.h"
#include "adpcm_ima_qt_decoder.h"
#include "logger.h"

#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/audio/adpcm/"

AdpcmImaQtDecoder *pDecoder = nullptr;

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels) {
    LOGE("init decoder=%p", pDecoder);
    if (nullptr == pDecoder) {
        pDecoder = new AdpcmImaQtDecoder();
        pDecoder->init(sampleRate, channels);
        return 0;
    }
    return -1;
}

JNIEXPORT jint JNICALL chunkSize(JNIEnv *env, jobject obj) {
    return pDecoder->chunkSize();
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    pDecoder->release();
    delete pDecoder;
    pDecoder = nullptr;
}

JNIEXPORT jbyteArray JNICALL decode(JNIEnv *env, jobject obj, jbyteArray adpcmByteArray) {
    int adpcmLen = env->GetArrayLength(adpcmByteArray);
    if (adpcmLen != pDecoder->ctx->channels * 34) {
        LOGE("ADPCM bytes must be %d", pDecoder->ctx->channels * 34);
        return nullptr;
    }
    auto *adpcm_unit8_t_array = new uint8_t[adpcmLen];
    env->GetByteArrayRegion(adpcmByteArray, 0, adpcmLen, reinterpret_cast<jbyte *>(adpcm_unit8_t_array));

    int pcmLength;
    uint8_t *pcmBytes = pDecoder->decode(adpcm_unit8_t_array, adpcmLen, &pcmLength);

//    LOGE("adpcmLen=%d pcmLength=%d", adpcmLen, pcmLength);

    jbyteArray pcm_byte_array = env->NewByteArray(pcmLength);
    env->SetByteArrayRegion(pcm_byte_array, 0, pcmLength, reinterpret_cast<const jbyte *>(pcmBytes));
    delete[] adpcm_unit8_t_array;
    delete pcmBytes;
    return pcm_byte_array;
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

    jclass clz = env->FindClass(ADPCM_PACKAGE_BASE"AdpcmImaQtDecoder");
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