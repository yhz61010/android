#include "native_adpcm_ima_qt_encoder.h"
#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/audio/adpcm/"

static jfieldID getHandleField(JNIEnv *env, jobject obj) {
    jclass clazz = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);
    return fid;
}

static AdpcmImaQtEncoder *getEncoder(JNIEnv *env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<AdpcmImaQtEncoder *>(handle);
}

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels, jint bitRate) {
    if (getEncoder(env, obj) != nullptr) {
        LOGE("Encoder already initialized");
        return -1;
    }
    auto *pEncoder = new(std::nothrow) AdpcmImaQtEncoder(sampleRate, channels, bitRate);
    if (pEncoder == nullptr || !pEncoder->isValid()) {
        delete pEncoder;
        return -2;
    }
    env->SetLongField(obj, getHandleField(env, obj), reinterpret_cast<jlong>(pEncoder));
    return 0;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder != nullptr) {
        delete pEncoder;
        env->SetLongField(obj, getHandleField(env, obj), 0L);
    }
}

JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    auto *pEncoder = getEncoder(env, obj);
    if (pEncoder == nullptr) return;

    int pcmLen = env->GetArrayLength(pcmByteArray);
    auto *pcm_unit8_t_array = new uint8_t[pcmLen];
    env->GetByteArrayRegion(pcmByteArray, 0, pcmLen, reinterpret_cast<jbyte *>(pcm_unit8_t_array));

    // Cache jclass and jmethodID for the callback
    jclass clazz = env->GetObjectClass(obj);
    jmethodID callbackMethod = env->GetMethodID(clazz, "encodedAudioCallback", "([B)V");
    env->DeleteLocalRef(clazz);

    pEncoder->encode(pcm_unit8_t_array, pcmLen, [env, obj, callbackMethod](uint8_t *data, int len) {
        jbyteArray encoded_byte_array = env->NewByteArray(len);
        env->SetByteArrayRegion(encoded_byte_array, 0, len, reinterpret_cast<const jbyte *>(data));
        env->CallVoidMethod(obj, callbackMethod, encoded_byte_array);
        env->DeleteLocalRef(encoded_byte_array);
    });

    delete[] pcm_unit8_t_array;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return env->NewStringUTF("1.0.0");
}

// =============================

static JNINativeMethod methods[] = {
        {(char*)"init",       (char*)"(III)I",               (void *) init},
        {(char*)"release",    (char*)"()V",                  (void *) release},
        {(char*)"encode",     (char*)"([B)V",                (void *) encode},
        {(char*)"getVersion", (char*)"()Ljava/lang/String;", (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(ADPCM_PACKAGE_BASE"AdpcmImaQtEncoder");
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
