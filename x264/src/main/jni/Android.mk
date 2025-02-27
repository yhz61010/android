LOCAL_PATH := $(call my-dir)

MY_PREBUILT := $(LOCAL_PATH)/prebuilt/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := libx264
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/$(LOCAL_MODULE).so
include $(PREBUILT_SHARED_LIBRARY)
#include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := x264-encoder
LOCAL_SRC_FILES := libx264_jni.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog
LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES) $(MY_PREBUILT)/include
#LOCAL_STATIC_LIBRARIES := libx264
LOCAL_SHARED_LIBRARIES := libx264
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)
