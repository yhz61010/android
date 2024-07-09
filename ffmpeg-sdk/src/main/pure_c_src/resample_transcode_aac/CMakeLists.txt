cmake_minimum_required(VERSION 3.28)
project(resample_transcode_aac C)

set(CMAKE_C_STANDARD 11)

# <project path>/cmake-build-debug/output
#set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/output")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_HOME_DIRECTORY}/output")

# <project path>/cmake-build-debug/output
#set(EXECUTABLE_OUTPUT_PATH "output")

find_package(PkgConfig REQUIRED)
pkg_check_modules(LIBAV REQUIRED IMPORTED_TARGET
        libavformat
        libavcodec
        libavutil
        libswscale
        libswresample
)
# libswscale
# libavdevice
# libavfilter
# libswresample

include_directories(${PROJECT_NAME})

add_executable(${PROJECT_NAME} resample_transcode_aac.c
               cmn_util.c
               ffmpeg_util.c)

target_link_libraries(${PROJECT_NAME} PkgConfig::LIBAV)

#set_target_properties()
