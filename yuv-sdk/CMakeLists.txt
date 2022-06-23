cmake_minimum_required(VERSION 3.10.2)

# https://developer.android.com/studio/projects/configure-cmake?hl=zh-cn

# 官方标准配置
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fno-rtti -fno-exceptions -Wall")

add_library(leo-yuv # Sets the name of the library.
            SHARED  # Sets the library as a shared library.
            # Provides a relative path to your source file(s).
            src/main/cpp/YuvUtilNative.cpp
            src/main/cpp/YuvConvert.cpp)

add_library(yuv
            SHARED
            # 添加其他预构建库。表明要将此库导入到项目中。
            IMPORTED)
# IMPORTED 之后，需要使用 set_target_properties() 命令指定库的路径。
set_target_properties(yuv # Specifies the target library.
                      PROPERTIES IMPORTED_LOCATION # Specifies the parameter you want to define.
                      # Provides the path to the library you want to import.
                      ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libyuv.so)
# 使用 include_directories() 命令并包含相应头文件的路径
include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/include)

# 找到 Android 专有的日志支持库，并将其路径存储在 log-lib 中。
find_library(log-lib # Defines the name of the path variable that stores the location of the NDK library.
        # Specifies the name of the NDK library that CMake needs to locate.
        log )

target_link_libraries(leo-yuv
                      yuv
                      # android
                      # jnigraphics
                      # log
                      # Links the log library to the target library.
                      ${log-lib}
                      )