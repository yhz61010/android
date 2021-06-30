#ifndef LEO_FFMPEG4ANDROID_LOGGER_H
#define LEO_FFMPEG4ANDROID_LOGGER_H

#ifdef ANDROID
#define LOG_TAG    "leo_ffmpeg_jni"
#include <android/log.h>

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#else
#define LOGE(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#endif

#endif //LEO_FFMPEG4ANDROID_LOGGER_H
