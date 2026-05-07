cmake_minimum_required(VERSION 4.1.2)

project("ffmpeg-sdk")

# Path to FFmpeg prebuilt libraries
set(FFMPEG_PREBUILT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_build/prebuilt/${ANDROID_ABI})

# --- FFmpeg prebuilt shared libraries ---

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES
    IMPORTED_LOCATION ${FFMPEG_PREBUILT_DIR}/lib/libavcodec.so
)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES
    IMPORTED_LOCATION ${FFMPEG_PREBUILT_DIR}/lib/libavutil.so
)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES
    IMPORTED_LOCATION ${FFMPEG_PREBUILT_DIR}/lib/libswscale.so
)

# --- Common settings ---

set(FFMPEG_INCLUDE_DIR ${FFMPEG_PREBUILT_DIR}/include)

# 16KB page alignment for Android compatibility
set(PAGE_SIZE_FLAGS "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

# --- h264-hevc-decoder library ---

add_library(h264-hevc-decoder SHARED
    h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp
)

target_include_directories(h264-hevc-decoder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/h264_hevc_decoder
    ${FFMPEG_INCLUDE_DIR}
)

target_link_libraries(h264-hevc-decoder
    avcodec
    avutil
    swscale
    log
    jnigraphics
    z
    android
    m
)

target_link_options(h264-hevc-decoder PRIVATE ${PAGE_SIZE_FLAGS})

# --- adpcm-ima-qt-decoder library ---

add_library(adpcm-ima-qt-decoder SHARED
    adpcm_ima_qt_decoder/adpcm_ima_qt_decoder.cpp
    adpcm_ima_qt_decoder/native_adpcm_ima_qt_decoder.cpp
)

target_include_directories(adpcm-ima-qt-decoder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/adpcm_ima_qt_decoder
    ${FFMPEG_INCLUDE_DIR}
)

target_link_libraries(adpcm-ima-qt-decoder
    avcodec
    avutil
    log
    jnigraphics
    z
    android
    m
)

target_link_options(adpcm-ima-qt-decoder PRIVATE ${PAGE_SIZE_FLAGS})

# --- adpcm-ima-qt-encoder library ---

add_library(adpcm-ima-qt-encoder SHARED
    adpcm_ima_qt_encoder/adpcm_ima_qt_encoder.cpp
    adpcm_ima_qt_encoder/native_adpcm_ima_qt_encoder.cpp
)

target_include_directories(adpcm-ima-qt-encoder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/adpcm_ima_qt_encoder
    ${FFMPEG_INCLUDE_DIR}
)

target_link_libraries(adpcm-ima-qt-encoder
    avcodec
    avutil
    log
    jnigraphics
    z
    android
    m
)

target_link_options(adpcm-ima-qt-encoder PRIVATE ${PAGE_SIZE_FLAGS})
