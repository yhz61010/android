#include "native_adpcm_ima_qt_decoder.h"

#include <new>

#include "adpcm_ima_qt_decoder.h"
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

AdpcmImaQtDecoder *GetDecoder(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return nullptr;
    return reinterpret_cast<AdpcmImaQtDecoder *>(env->GetLongField(object, field));
}

}  // namespace

JNIEXPORT jint JNICALL NativeInit(JNIEnv *env, jobject object, jint sample_rate, jint channels) {
    if (sample_rate <= 0 || (channels != 1 && channels != 2)) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Invalid ADPCM decoder parameters");
        return -1;
    }
    if (GetDecoder(env, object) != nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is already initialized");
        return -1;
    }
    auto *decoder = new(std::nothrow) AdpcmImaQtDecoder(sample_rate, channels);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate ADPCM decoder");
        return -2;
    }
    if (!decoder->isValid()) {
        delete decoder;
        ThrowException(env, "java/lang/IllegalStateException", "Unable to initialize ADPCM decoder");
        return -2;
    }
    jfieldID handle_field = GetHandleField(env, object);
    if (handle_field == nullptr) {
        delete decoder;
        return -2;
    }
    env->SetLongField(object, handle_field, reinterpret_cast<jlong>(decoder));
    return 0;
}

JNIEXPORT jint JNICALL NativeChunkSize(JNIEnv *env, jobject object) {
    auto *decoder = GetDecoder(env, object);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is closed");
        return -1;
    }
    return 34 * decoder->getChannels();
}

JNIEXPORT void JNICALL NativeRelease(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return;
    auto *decoder = reinterpret_cast<AdpcmImaQtDecoder *>(env->GetLongField(object, field));
    env->SetLongField(object, field, 0L);
    delete decoder;
}

JNIEXPORT jbyteArray JNICALL NativeDecode(JNIEnv *env, jobject object, jbyteArray input_array) {
    auto *decoder = GetDecoder(env, object);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is closed");
        return nullptr;
    }
    if (input_array == nullptr || env->GetArrayLength(input_array) != 34 * decoder->getChannels()) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Invalid ADPCM chunk size");
        return nullptr;
    }
    jbyte *input = env->GetByteArrayElements(input_array, nullptr);
    if (input == nullptr) return nullptr;
    int pcm_length = 0;
    uint8_t *pcm = decoder->decode(reinterpret_cast<const uint8_t *>(input),
                                   env->GetArrayLength(input_array), &pcm_length);
    env->ReleaseByteArrayElements(input_array, input, JNI_ABORT);
    if (pcm == nullptr || pcm_length <= 0) {
        delete[] pcm;
        return nullptr;
    }
    jbyteArray output = env->NewByteArray(pcm_length);
    if (output == nullptr) {
        delete[] pcm;
        if (!env->ExceptionCheck()) {
            ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoded PCM output");
        }
        return nullptr;
    }
    env->SetByteArrayRegion(output, 0, pcm_length, reinterpret_cast<const jbyte *>(pcm));
    delete[] pcm;
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(output);
        return nullptr;
    }
    return output;
}

JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF("1.0.0");
}

static JNINativeMethod methods[] = {
        {"nativeInit", "(II)I", reinterpret_cast<void *>(NativeInit)},
        {"nativeRelease", "()V", reinterpret_cast<void *>(NativeRelease)},
        {"nativeChunkSize", "()I", reinterpret_cast<void *>(NativeChunkSize)},
        {"nativeDecode", "([B)[B", reinterpret_cast<void *>(NativeDecode)},
        {"nativeGetVersion", "()Ljava/lang/String;", reinterpret_cast<void *>(NativeGetVersion)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(ADPCM_PACKAGE_BASE "AdpcmImaQtDecoder");
    if (clazz == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
