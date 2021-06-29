#include "native_adpcm_ima_qt_encoder.h"
#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/audio/adpcm/"

AdpcmImaQtEncoder *pEncoder = nullptr;

JNIEnv *gEnv;
jobject gObj;

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels, jint bitRate) {
    if (nullptr == pEncoder) {
        pEncoder = new AdpcmImaQtEncoder();
        pEncoder->init(sampleRate, channels, bitRate);
        return 0;
    }
    return -1;
}

JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj) {
    pEncoder->release();
    delete pEncoder;
    pEncoder = nullptr;
}

JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray) {
    gEnv = env;
    gObj = obj;
    int pcmLen = env->GetArrayLength(pcmByteArray);
    auto *pcm_unit8_t_array = new uint8_t[pcmLen];
    env->GetByteArrayRegion(pcmByteArray, 0, pcmLen, reinterpret_cast<jbyte *>(pcm_unit8_t_array));
    pEncoder->encode(pcm_unit8_t_array, pcmLen, encodedAudioCallback);
    delete[] pcm_unit8_t_array;
}

JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("0.1.0");
}

void encodedAudioCallback(uint8_t *encodedAudioData, int decodedAudioLength) {
//    LOGE("encodedAudioCallback decodedAudioLength=%d gEnv=%p", decodedAudioLength, gEnv);
    jbyteArray encoded_byte_array = gEnv->NewByteArray(decodedAudioLength);
    gEnv->SetByteArrayRegion(encoded_byte_array, 0, decodedAudioLength, reinterpret_cast<const jbyte *>(encodedAudioData));

    // Get the class of the current calling object
    jclass clazz = gEnv->GetObjectClass(gObj);
    // Get the method id of an empty constructor in clazz
    jmethodID constructor = gEnv->GetMethodID(clazz, "encodedAudioCallback", "([B)V");
    // Calls my.package.name.JNIReturnExample#javaCallback(float, float);
    gEnv->CallVoidMethod(gObj, constructor, encoded_byte_array);
}

// =============================

static JNINativeMethod methods[] = {
        {"init",       "(III)I",               (void *) init},
        {"release",    "()V",                  (void *) release},
        {"encode",     "([B)V",                (void *) encode},
        {"getVersion", "()Ljava/lang/String;", (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
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