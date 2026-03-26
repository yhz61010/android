LOCAL_PATH := $(call my-dir)

MY_PREBUILT := $(LOCAL_PATH)/../ffmpeg_build/prebuilt/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavcodec.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavutil
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libavutil.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libswscale
LOCAL_SRC_FILES := $(MY_PREBUILT)/lib/libswscale.so
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

# ==================================

include $(CLEAR_VARS)
LOCAL_MODULE := h264-hevc-decoder
LOCAL_SRC_FILES := $(LOCAL_PATH)/h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog -ljnigraphics -lz -landroid -lm -pthread -L$(SYSROOT)/usr/lib
# 告诉 linker 将最大页面大小设置为 16KB（16384 字节）
# 这确保了生成的 .so 文件的 LOAD section 是 16KB 对齐的，符合 Android 的要求
# -Wl,--max-page-size=16384 这个参数在 NDK r25+ 中不被直接支持
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
                    $(LOCAL_C_INCLUDES) \
                    $(MY_PREBUILT)/include \
                    $(LOCAL_PATH)/h264_hevc_decoder
# The following libraries will be generated in src/main/lib folder
#LOCAL_SHARED_LIBRARIES := libavdevice libavcodec libavfilter libavformat libavutil libswresample libswscale
LOCAL_SHARED_LIBRARIES := libavcodec libavutil libswscale
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)

# ==================================

include $(CLEAR_VARS)
LOCAL_MODULE := adpcm-ima-qt-decoder
LOCAL_SRC_FILES := $(LOCAL_PATH)/adpcm_ima_qt_decoder/adpcm_ima_qt_decoder.cpp \
                   $(LOCAL_PATH)/adpcm_ima_qt_decoder/native_adpcm_ima_qt_decoder.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog -ljnigraphics -lz -landroid -lm -pthread -L$(SYSROOT)/usr/lib
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
                    $(LOCAL_C_INCLUDES) \
                    $(MY_PREBUILT)/include \
                    $(LOCAL_PATH)/adpcm_ima_qt_decoder
# The following libraries will be generated in src/main/lib folder
#LOCAL_SHARED_LIBRARIES := libavdevice libavcodec libavfilter libavformat libavutil libswresample libswscale
LOCAL_SHARED_LIBRARIES := libavcodec libavutil
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)

# ==================================

include $(CLEAR_VARS)
LOCAL_MODULE := adpcm-ima-qt-encoder
LOCAL_SRC_FILES := $(LOCAL_PATH)/adpcm_ima_qt_encoder/adpcm_ima_qt_encoder.cpp \
                   $(LOCAL_PATH)/adpcm_ima_qt_encoder/native_adpcm_ima_qt_encoder.cpp
LOCAL_CFLAGS    :=
LOCAL_LDLIBS    := -llog -ljnigraphics -lz -landroid -lm -pthread -L$(SYSROOT)/usr/lib
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
                    $(LOCAL_C_INCLUDES) \
                    $(MY_PREBUILT)/include \
                    $(LOCAL_PATH)/adpcm_ima_qt_encoder
# The following libraries will be generated in src/main/lib folder
#LOCAL_SHARED_LIBRARIES := libavdevice libavcodec libavfilter libavformat libavutil libswresample libswscale
LOCAL_SHARED_LIBRARIES := libavcodec libavutil
LOCAL_DISABLE_FORMAT_STRING_CHECKS := true
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
include $(BUILD_SHARED_LIBRARY)
