#ifndef LEOANDROIDBASEUTIL_BITMAPROTATE_H
#define LEOANDROIDBASEUTIL_BITMAPROTATE_H

#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <android/bitmap.h>
#include <cstring>
#include <unistd.h>

#define LOG_TAG "LEO-Native-Bitmap"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C"
{
#endif

    // set
    JNIEXPORT jobject JNICALL SetBitmapData(JNIEnv *env, jobject obj, jobject bitmap);

    // get
    JNIEXPORT jobject JNICALL GetBitmapFromSavedBitmapData(JNIEnv *env, jobject obj, jobject handle);

    // free
    JNIEXPORT void JNICALL FreeBitmapData(JNIEnv *env, jobject obj, jobject handle);

    // rotate 90 degrees CCW
    JNIEXPORT void JNICALL RotateBitmapCcw90(JNIEnv *env, jobject obj, jobject handle);

    // rotate 90 degrees CW
    JNIEXPORT void JNICALL RotateBitmapCw90(JNIEnv *env, jobject obj, jobject handle);

    // rotate 180 degrees
    JNIEXPORT void JNICALL RotateBitmap180(JNIEnv *env, jobject obj, jobject handle);

    // crop
    JNIEXPORT void JNICALL CropBitmap(JNIEnv *env, jobject obj,
                                      jobject handle,
                                      uint32_t left, uint32_t top, uint32_t right, uint32_t bottom);

    // scale using nearest neighbor
    JNIEXPORT void JNICALL ScaleNNBitmap(JNIEnv *env, jobject obj,
                                         jobject handle,
                                         uint32_t newWidth, uint32_t newHeight);

    // scale using Bilinear Interpolation
    JNIEXPORT void JNICALL ScaleBIBitmap(JNIEnv *env, jobject obj,
                                         jobject handle,
                                         uint32_t newWidth, uint32_t newHeight);

    JNIEXPORT void JNICALL FlipBitmapHorizontal(JNIEnv *env, jobject obj, jobject handle);

    JNIEXPORT void JNICALL FlipBitmapVertical(JNIEnv *env, jobject obj, jobject handle);

#ifdef __cplusplus
}
#endif

class JniBitmap
{
public:
    uint32_t *_storedBitmapPixels;
    AndroidBitmapInfo _bitmapInfo;

    JniBitmap()
    {
        _storedBitmapPixels = NULL;
    }
};

typedef struct
{
    uint8_t alpha, red, green, blue;
} ARGB;

#endif // LEOANDROIDBASEUTIL_BITMAPROTATE_H
