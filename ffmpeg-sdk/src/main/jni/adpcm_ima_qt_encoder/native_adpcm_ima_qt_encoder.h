#include <jni.h>

#ifndef NATIVE_ADPCM_IMA_QT_ENCODER_H
#define NATIVE_ADPCM_IMA_QT_ENCODER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL init(JNIEnv *env, jobject obj, jint sampleRate, jint channels, jint bitRate);
JNIEXPORT void JNICALL release(JNIEnv *env, jobject obj);
JNIEXPORT void JNICALL encode(JNIEnv *env, jobject obj, jbyteArray pcmByteArray);
JNIEXPORT jstring JNICALL getVersion(JNIEnv *env, jobject thiz);

static void encodedAudioCallback(uint8_t *encodedAudioData, int decodedAudioLength);

#ifdef __cplusplus
}
#endif
#endif
