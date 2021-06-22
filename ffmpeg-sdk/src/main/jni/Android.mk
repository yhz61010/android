LOCAL_PATH := $(call my-dir)

MY_PREBUILT := $(LOCAL_PATH)/prebuilt/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavcodec.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavutil
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavutil.so
include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := libswresample
#LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libswresample.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := libavdevice
#LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavdevice.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := libavfilter
#LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavfilter.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := libavformat
#LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavformat.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := libswscale
#LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libswscale.so
#include $(PREBUILT_SHARED_LIBRARY)

# ==================================

include $(CLEAR_VARS)
LOCAL_MODULE := adpcm-ima-qt-decoder
LOCAL_SRC_FILES := adpcm_ima_qt_decoder_jni.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog -ljnigraphics -lz -landroid -lm -pthread -L$(SYSROOT)/usr/lib
LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES) $(MY_PREBUILT)/include
# The following libraries will be generated in src/main/lib folder
#LOCAL_SHARED_LIBRARIES := libavdevice libavcodec libavfilter libavformat libavutil libswresample
LOCAL_SHARED_LIBRARIES := libavcodec libavutil
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)

# ==================================

include $(CLEAR_VARS)
LOCAL_MODULE := adpcm-ima-qt-encoder
LOCAL_SRC_FILES := adpcm_ima_qt_encoder_jni.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog -ljnigraphics -lz -landroid -lm -pthread -L$(SYSROOT)/usr/lib
LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES) $(MY_PREBUILT)/include
# The following libraries will be generated in src/main/lib folder
#LOCAL_SHARED_LIBRARIES := libavdevice libavcodec libavfilter libavformat libavutil libswresample
LOCAL_SHARED_LIBRARIES := libavcodec libavutil
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)