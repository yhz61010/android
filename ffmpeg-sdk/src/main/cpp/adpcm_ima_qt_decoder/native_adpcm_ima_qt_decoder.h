#include <jni.h>

#ifndef NATIVE_ADPCM_IMA_QT_DECODER_H
#define NATIVE_ADPCM_IMA_QT_DECODER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL NativeInit(JNIEnv *env, jobject object, jint sample_rate, jint channels);
JNIEXPORT jbyteArray JNICALL NativeDecode(JNIEnv *env, jobject object, jbyteArray input_array);
JNIEXPORT void JNICALL NativeRelease(JNIEnv *env, jobject object);
JNIEXPORT jint JNICALL NativeChunkSize(JNIEnv *env, jobject object);
JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject object);

#ifdef __cplusplus
}
#endif
#endif
