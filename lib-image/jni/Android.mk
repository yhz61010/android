LOCAL_PATH := $(call my-dir)

#bitmap operations module
include $(CLEAR_VARS)

LOCAL_MODULE    := leo-bitmap
LOCAL_SRC_FILES := BitmapRotateNative.cpp
LOCAL_LDLIBS := -llog
# 告诉 linker 将最大页面大小设置为 16KB（16384 字节）
# 这确保了生成的 .so 文件的 LOAD section 是 16KB 对齐的，符合 Android 的要求
# -Wl,--max-page-size=16384 这个参数在 NDK r25+ 中不被直接支持
LOCAL_LDFLAGS += -ljnigraphics -Wl,-z,max-page-size=16384
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
$(LOCAL_C_INCLUDES)

include $(BUILD_SHARED_LIBRARY)
APP_OPTIM := release
APP_PLATFORM := android-21
LOCAL_CFLAGS := -g

#if you need to add more module, do the same as the one we started with (the one with the CLEAR_VARS)
