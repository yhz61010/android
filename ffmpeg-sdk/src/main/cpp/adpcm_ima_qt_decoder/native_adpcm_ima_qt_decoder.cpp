#include "native_adpcm_ima_qt_decoder.h"
#include "adpcm_ima_qt_decoder.h"
#include "logger.h"

#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/audio/adpcm/"

static jfieldID getHandleField(JNIEnv *env, jobject obj) {
    jclass clazz = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);
    return fid;
}

static AdpcmImaQtDecoder *getDecoder(JNIEnv *env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<AdpcmImaQtDecoder *>(handle);
}

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels) {
    if (getDecoder(env, obj) != nullptr) {
        LOGE("Decoder already initialized");
        return -1;
    }
    auto *pDecoder = new(std::nothrow) AdpcmImaQtDecoder(sampleRate, channels);
    if (pDecoder == nullptr || !pDecoder->isValid()) {
        delete pDecoder;
        return -2;
    }
    env->SetLongField(obj, getHandleField(env, obj), reinterpret_cast<jlong>(pDecoder));
    return 0;
}

JNIEXPORT jint JNICALL chunkSize(JNIEnv *env, jobject obj) {
    auto *pDecoder = getDecoder(env, obj);
    if (pDecoder == nullptr) return -1;
    return 34 * pDecoder->getChannels();
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    auto *pDecoder = getDecoder(env, obj);
    if (pDecoder != nullptr) {
        delete pDecoder;
        env->SetLongField(obj, getHandleField(env, obj), 0L);
    }
}

JNIEXPORT jbyteArray JNICALL decode(JNIEnv *env, jobject obj, jbyteArray adpcmByteArray) {
    auto *pDecoder = getDecoder(env, obj);
    if (pDecoder == nullptr) return nullptr;

    int adpcmLen = env->GetArrayLength(adpcmByteArray);
    if (adpcmLen != pDecoder->getCodecContext()->ch_layout.nb_channels * 34) {
        LOGE("Decoder: ADPCM bytes must be %d", pDecoder->getCodecContext()->ch_layout.nb_channels * 34);
        return nullptr;
    }
    auto *adpcm_unit8_t_array = new uint8_t[adpcmLen];
    env->GetByteArrayRegion(adpcmByteArray, 0, adpcmLen, reinterpret_cast<jbyte *>(adpcm_unit8_t_array));

    int pcmLength;
    uint8_t *pcmBytes = pDecoder->decode(adpcm_unit8_t_array, adpcmLen, &pcmLength);

    jbyteArray pcm_byte_array = nullptr;
    if (pcmBytes != nullptr) {
        pcm_byte_array = env->NewByteArray(pcmLength);
        env->SetByteArrayRegion(pcm_byte_array, 0, pcmLength, reinterpret_cast<const jbyte *>(pcmBytes));
        delete[] pcmBytes;
    }
    delete[] adpcm_unit8_t_array;
    return pcm_byte_array;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return env->NewStringUTF("1.0.0");
}

// =============================

static JNINativeMethod methods[] = {
        {(char*)"init",       (char*)"(II)I",                (void *) init},
        {(char*)"release",    (char*)"()V",                  (void *) release},
        {(char*)"chunkSize",  (char*)"()I",                  (void *) chunkSize},
        {(char*)"decode",     (char*)"([B)[B",               (void *) decode},
        {(char*)"getVersion", (char*)"()Ljava/lang/String;", (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Decoder: JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(ADPCM_PACKAGE_BASE"AdpcmImaQtDecoder");
    if (clz == nullptr) {
        LOGE("Decoder: JNI_OnLoad FindClass error.");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        LOGE("Decoder: JNI_OnLoad RegisterNatives error.");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
