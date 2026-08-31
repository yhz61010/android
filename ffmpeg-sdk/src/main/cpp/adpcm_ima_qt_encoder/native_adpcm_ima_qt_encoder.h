#include <jni.h>

#ifndef NATIVE_ADPCM_IMA_QT_ENCODER_H
#define NATIVE_ADPCM_IMA_QT_ENCODER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL NativeInit(JNIEnv *env, jobject object, jint sample_rate,
                                  jint channels, jint bit_rate);
JNIEXPORT void JNICALL NativeRelease(JNIEnv *env, jobject object);
JNIEXPORT void JNICALL NativeEncode(JNIEnv *env, jobject object, jbyteArray pcm_array);
JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject object);

#ifdef __cplusplus
}
#endif
#endif
