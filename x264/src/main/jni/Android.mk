LOCAL_PATH := $(call my-dir)

MY_PREBUILT := $(LOCAL_PATH)/prebuilt/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := libx264
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/$(LOCAL_MODULE).so
# 告诉 linker 将最大页面大小设置为 16KB（16384 字节）
# 这确保了生成的 .so 文件的 LOAD section 是 16KB 对齐的，符合 Android 的要求
# -Wl,--max-page-size=16384 这个参数在 NDK r25+ 中不被直接支持
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
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
