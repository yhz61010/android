#include <jni.h>

#ifndef NATIVE_ADPCM_IMA_QT_DECODER_H
#define NATIVE_ADPCM_IMA_QT_DECODER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels);
JNIEXPORT jbyteArray JNICALL decode(JNIEnv *env, jobject obj, jbyteArray adpcmByteArray);
JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj);
JNIEXPORT jint JNICALL chunkSize(JNIEnv *env, jobject obj);
JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif
#endif
