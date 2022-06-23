LOCAL_PATH := $(call my-dir)

#bitmap operations module
include $(CLEAR_VARS)

LOCAL_MODULE    := leo-bitmap
LOCAL_SRC_FILES := BitmapRotateNative.cpp
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS += -ljnigraphics
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
$(LOCAL_C_INCLUDES)

include $(BUILD_SHARED_LIBRARY)
APP_OPTIM := release
APP_PLATFORM := android-21
LOCAL_CFLAGS := -g

#if you need to add more module, do the same as the one we started with (the one with the CLEAR_VARS)
