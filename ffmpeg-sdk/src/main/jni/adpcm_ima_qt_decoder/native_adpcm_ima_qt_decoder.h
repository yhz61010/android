#include <jni.h>

#ifndef NATIVE_ADPCM_IMA_QT_DECODER_H
#define NATIVE_ADPCM_IMA_QT_DECODER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL init(__attribute__((unused)) __attribute__((unused)) JNIEnv *env, __attribute__((unused)) __attribute__((unused)) jobject obj, jint sampleRate, jint channels);
JNIEXPORT jbyteArray JNICALL decode(JNIEnv *env, __attribute__((unused)) jobject obj, jbyteArray adpcmByteArray);
JNIEXPORT void JNICALL release(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject obj);
JNIEXPORT jint JNICALL chunkSize(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject obj);
JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, __attribute__((unused)) jobject thiz);

#ifdef __cplusplus
}
#endif
#endif
