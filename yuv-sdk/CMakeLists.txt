cmake_minimum_required(VERSION 3.4.1)

# 官方标准配置
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fno-rtti -fno-exceptions -Wall")

add_library(leo-yuv
            SHARED
            src/main/cpp/YUVUtil.cpp
            src/main/cpp/YUVBase.cpp)

add_library(yuv
            SHARED
            IMPORTED)

set_target_properties(yuv
                      PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libyuv.so)

include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/include)

target_link_libraries(leo-yuv
                      yuv
                      android
                      # jnigraphics
                      log)