#include "native_adpcm_ima_qt_encoder.h"

#include <cerrno>
#include <new>

#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

#define ADPCM_PACKAGE_BASE "com/leovp/ffmpeg/audio/adpcm/"

namespace {

void ThrowException(JNIEnv *env, const char *class_name, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

jfieldID GetHandleField(JNIEnv *env, jobject object) {
    jclass clazz = env->GetObjectClass(object);
    if (clazz == nullptr) return nullptr;
    jfieldID field = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);
    return field;
}

AdpcmImaQtEncoder *GetEncoder(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return nullptr;
    return reinterpret_cast<AdpcmImaQtEncoder *>(env->GetLongField(object, field));
}

}  // namespace

JNIEXPORT jint JNICALL NativeInit(JNIEnv *env, jobject object, jint sample_rate,
                                  jint channels, jint bit_rate) {
    if (sample_rate <= 0 || bit_rate <= 0 || (channels != 1 && channels != 2)) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Invalid ADPCM encoder parameters");
        return -1;
    }
    if (GetEncoder(env, object) != nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Encoder is already initialized");
        return -1;
    }
    auto *encoder = new(std::nothrow) AdpcmImaQtEncoder(sample_rate, channels, bit_rate);
    if (encoder == nullptr) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate ADPCM encoder");
        return -2;
    }
    if (!encoder->isValid()) {
        delete encoder;
        ThrowException(env, "java/lang/IllegalStateException", "Unable to initialize ADPCM encoder");
        return -2;
    }
    jfieldID handle_field = GetHandleField(env, object);
    if (handle_field == nullptr) {
        delete encoder;
        return -2;
    }
    env->SetLongField(object, handle_field, reinterpret_cast<jlong>(encoder));
    return 0;
}

JNIEXPORT void JNICALL NativeRelease(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return;
    auto *encoder = reinterpret_cast<AdpcmImaQtEncoder *>(env->GetLongField(object, field));
    env->SetLongField(object, field, 0L);
    delete encoder;
}

JNIEXPORT void JNICALL NativeEncode(JNIEnv *env, jobject object, jbyteArray pcm_array) {
    auto *encoder = GetEncoder(env, object);
    if (encoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Encoder is closed");
        return;
    }
    if (pcm_array == nullptr || env->GetArrayLength(pcm_array) <= 0) {
        ThrowException(env, "java/lang/IllegalArgumentException", "PCM input must not be empty");
        return;
    }
    const jsize pcm_length = env->GetArrayLength(pcm_array);
    const int frame_bytes = encoder->getInputFrameBytes();
    if (frame_bytes <= 0 || pcm_length % frame_bytes != 0) {
        ThrowException(env, "java/lang/IllegalArgumentException",
                       "PCM input must contain a whole number of encoder frames");
        return;
    }
    jclass clazz = env->GetObjectClass(object);
    jmethodID callback_method = clazz == nullptr
                                ? nullptr
                                : env->GetMethodID(clazz, "encodedAudioCallback", "([B)V");
    if (clazz != nullptr) env->DeleteLocalRef(clazz);
    if (callback_method == nullptr) return;

    jbyte *pcm = env->GetByteArrayElements(pcm_array, nullptr);
    if (pcm == nullptr) return;
    bool callback_failed = false;
    const int result = encoder->encode(
            reinterpret_cast<const uint8_t *>(pcm), pcm_length,
            [env, object, callback_method, &callback_failed](uint8_t *data, int length) {
                if (callback_failed || env->ExceptionCheck()) return;
                jbyteArray encoded = env->NewByteArray(length);
                if (encoded == nullptr) {
                    callback_failed = true;
                    return;
                }
                env->SetByteArrayRegion(encoded, 0, length,
                                        reinterpret_cast<const jbyte *>(data));
                if (!env->ExceptionCheck()) env->CallVoidMethod(object, callback_method, encoded);
                callback_failed = env->ExceptionCheck();
                env->DeleteLocalRef(encoded);
            });
    env->ReleaseByteArrayElements(pcm_array, pcm, JNI_ABORT);
    if (callback_failed) return;
    if (result < 0) {
        ThrowException(env, "java/lang/IllegalStateException", "ADPCM encoding failed");
    }
}

JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF("1.0.0");
}

static JNINativeMethod methods[] = {
        {"nativeInit", "(III)I", reinterpret_cast<void *>(NativeInit)},
        {"nativeRelease", "()V", reinterpret_cast<void *>(NativeRelease)},
        {"nativeEncode", "([B)V", reinterpret_cast<void *>(NativeEncode)},
        {"nativeGetVersion", "()Ljava/lang/String;", reinterpret_cast<void *>(NativeGetVersion)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(ADPCM_PACKAGE_BASE "AdpcmImaQtEncoder");
    if (clazz == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
