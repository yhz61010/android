APP_STL := c++_shared
# armeabi-v7a arm64-v8a x86 x86_64 or all
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_PLATFORM := android-21
APP_OPTIM := release
#APP_CPPFLAGS += -fexceptions

# Support Android 16KB page size for arm64-v8a architecture (NDK r27+)
# This ensures ELF segments are aligned to 16KB boundaries
ifeq ($(APP_ABI),arm64-v8a)
    APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
endif
